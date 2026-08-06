import type { IncomingMessage, ServerResponse } from 'http';
import type { ProviderManager, ProviderConnectionRecord } from '../provider-manager.js';
import type { GatewayStore } from './gateway-store.js';
import type { ChatCompletionRequest, GatewayConfig } from './types.js';
import { CircuitBreaker, isRetriableStatus } from './circuit-breaker.js';
import { selectRoute, type RouteCandidate } from './combo-router.js';
import { detectDialect, cursorToOpenAI, toOpenAI, toAnthropic, toGoogle, resolveModelAlias } from './translator.js';
import { buildSseHeaders } from './stream.js';
import { executeWorkflow } from './workflow-engine.js';

export interface GatewayEngineDeps {
  store: GatewayStore;
  providerManager: ProviderManager;
  getConnections: () => ProviderConnectionRecord[];
}

export class GatewayEngine {
  private circuit = new CircuitBreaker();
  private config: GatewayConfig;

  constructor(
    private deps: GatewayEngineDeps,
    config?: GatewayConfig
  ) {
    this.config = config ?? { port: 20128, enableCursorBridge: true, enableCompression: true, enableAutoCombo: true, forceHttp11: true, corsOrigins: ['*'] };
  }

  async reloadConfig(): Promise<void> {
    this.config = await this.deps.store.getConfig();
  }

  async handle(req: IncomingMessage, res: ServerResponse, urlPath: string): Promise<boolean> {
    await this.reloadConfig();

    if (urlPath === '/v1/models' && req.method === 'GET') {
      await this.handleModels(res);
      return true;
    }

    if (urlPath === '/gateway/status' && req.method === 'GET') {
      await this.handleStatus(res);
      return true;
    }

    if (urlPath === '/gateway/workflows' && req.method === 'GET') {
      const workflows = await this.deps.store.getWorkflows();
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ workflows }));
      return true;
    }

    const isCursor = urlPath.startsWith('/cursor/');
    const chatPath = isCursor ? urlPath.replace('/cursor', '') : urlPath;

    if ((chatPath === '/v1/chat/completions' || chatPath === '/v1/messages') && req.method === 'POST') {
      const body = await this.readBody(req);
      const parsed = JSON.parse(body) as ChatCompletionRequest;
      await this.handleChatCompletions(res, parsed, isCursor ? 'ide' : 'chat', isCursor);
      return true;
    }

    return false;
  }

  private async handleModels(res: ServerResponse): Promise<void> {
    const connections = this.deps.getConnections().filter((c) => c.status === 'connected');
    const models = connections.flatMap((c) =>
      (c.syncedModels ?? []).map((m) => ({
        id: m,
        object: 'model',
        owned_by: c.providerId,
        root: m,
      }))
    );
    if (!models.length) {
      models.push({ id: 'ocean-gateway', object: 'model', owned_by: 'ocean', root: 'ocean-gateway' });
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ object: 'list', data: models }));
  }

  private async handleStatus(res: ServerResponse): Promise<void> {
    const stats = await this.deps.store.getStats();
    const combos = await this.deps.store.getCombos();
    const circuits = this.circuit.allStates();
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      gateway: 'ocean',
      version: '1.0.0',
      features: ['cursor-bridge', 'auto-combo', 'compression', 'circuit-breaker', 'workflows', 'virtual-keys'],
      config: this.config,
      stats,
      combos: combos.filter((c) => c.enabled).length,
      circuits,
    }));
  }

  private async handleChatCompletions(
    res: ServerResponse,
    rawBody: ChatCompletionRequest,
    trigger: 'chat' | 'ide',
    forceCursorBridge: boolean
  ): Promise<void> {
    const start = Date.now();
    const workflows = await this.deps.store.getWorkflows();
    let body = rawBody;

    if (forceCursorBridge || this.config.enableCursorBridge) {
      body = cursorToOpenAI(body);
    }

    const wfResult = executeWorkflow({ trigger, body, workflows });
    body = wfResult.body;

    const combos = await this.deps.store.getCombos();
    const combo = combos.find((c) => c.id === (wfResult.comboId ?? this.config.defaultComboId ?? 'ocean-smart'));

    const candidates = await this.buildCandidates(body.model);
    const route = selectRoute(candidates, combo, this.circuit, this.config.enableAutoCombo);

    if (!route) {
      res.writeHead(503, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'No available provider connections', type: 'gateway_error' } }));
      return;
    }

    await this.deps.store.recordDecision(route.decision);

    const connection = this.deps.getConnections().find((c) => c.providerId === route.winner.providerId);
    if (!connection) {
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: { message: 'Provider connection not found' } }));
      return;
    }

    const model = resolveModelAlias(body.model, combo?.modelAliases);
    const dialect = detectDialect(body);
    const upstream = await this.forwardToProvider(connection, { ...body, model }, dialect);

    if (!upstream.ok) {
      const retriable = isRetriableStatus(upstream.status);
      this.circuit.recordFailure(route.winner.connectionId, retriable);
      res.writeHead(upstream.status, { 'Content-Type': 'application/json' });
      res.end(upstream.body);
      await this.deps.store.recordUsage({
        id: `u_${Date.now()}`,
        connectionId: route.winner.connectionId,
        model,
        promptTokens: 0,
        completionTokens: 0,
        totalCostUsd: 0,
        latencyMs: Date.now() - start,
        success: false,
        timestamp: Date.now(),
      });
      return;
    }

    this.circuit.recordSuccess(route.winner.connectionId);
    await this.deps.store.updateHealth(route.winner.connectionId, {
      latencyMs: Date.now() - start,
      providerId: route.winner.providerId,
    });

    if (body.stream) {
      res.writeHead(200, buildSseHeaders());
      if (upstream.stream) {
        const reader = upstream.stream.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() ?? '';
            for (const line of lines) {
              if (line.trim()) res.write(`${line}\n`);
            }
          }
          if (buffer.trim()) res.write(`${buffer}\n`);
        } finally {
          res.write('data: [DONE]\n\n');
          res.end();
        }
      } else {
        res.end();
      }
    } else {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(upstream.body);
    }

    await this.deps.store.recordUsage({
      id: `u_${Date.now()}`,
      connectionId: route.winner.connectionId,
      model,
      promptTokens: estimateTokens(JSON.stringify(body.messages)),
      completionTokens: 0,
      totalCostUsd: 0.001,
      latencyMs: Date.now() - start,
      success: true,
      timestamp: Date.now(),
    });
  }

  private async buildCandidates(model: string): Promise<RouteCandidate[]> {
    const health = await this.deps.store.getHealth();
    return this.deps.getConnections()
      .filter((c) => c.status === 'connected')
      .map((c) => {
        const h = health.find((x) => x.connectionId === c.providerId);
        return {
          connectionId: c.providerId,
          providerId: c.providerId,
          model,
          health: h ?? {
            connectionId: c.providerId,
            providerId: c.providerId,
            latencyMs: 800,
            successRate: 0.9,
            quotaHeadroom: 0.7,
            costPer1kTokens: 0.015,
            lastUsed: 0,
          },
        };
      });
  }

  private async forwardToProvider(
    conn: ProviderConnectionRecord,
    body: ChatCompletionRequest,
    dialect: string
  ): Promise<{ ok: boolean; status: number; body: string; stream?: ReadableStream<Uint8Array> }> {
    const endpoint = this.resolveEndpoint(conn);
    if (!endpoint) {
      return { ok: false, status: 502, body: JSON.stringify({ error: 'No endpoint configured' }) };
    }

    let payload: Record<string, unknown>;
    if (dialect === 'anthropic' || conn.providerId.includes('anthropic')) {
      payload = toAnthropic(body);
    } else if (conn.providerId.includes('google') || conn.providerId.includes('gemini')) {
      payload = toGoogle(body);
    } else {
      payload = toOpenAI(body);
    }

    const apiKey = this.findApiKey(conn.config);
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (apiKey) {
      if (conn.providerId.includes('anthropic')) {
        headers['x-api-key'] = apiKey;
        headers['anthropic-version'] = '2023-06-01';
      } else {
        headers.Authorization = `Bearer ${apiKey}`;
      }
    }

    if (this.config.forceHttp11) {
      headers.Connection = 'keep-alive';
    }

    const res = await fetch(endpoint, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const errText = await res.text();
      return { ok: false, status: res.status, body: errText };
    }

    if (body.stream && res.body) {
      return { ok: true, status: 200, body: '', stream: res.body as unknown as ReadableStream<Uint8Array> };
    }

    return { ok: true, status: 200, body: await res.text() };
  }

  private resolveEndpoint(conn: ProviderConnectionRecord): string | null {
    const cfg = conn.config;
    const base = cfg.OMNIROUTE_URL ?? cfg.LITELLM_URL ?? cfg.GATEWAY_URL ?? cfg.CUSTOM_ENDPOINT ?? cfg.API_ENDPOINT;
    if (!base) return null;
    if (base.includes('/chat/completions') || base.includes('/messages')) return base;
    const root = base.replace(/\/$/, '');
    if (conn.providerId.includes('anthropic')) return `${root}/v1/messages`;
    if (conn.providerId.includes('google')) return `${root}/v1beta/models/${cfg.model ?? 'gemini-pro'}:generateContent`;
    return `${root}/v1/chat/completions`;
  }

  private findApiKey(config: Record<string, string>): string | undefined {
    for (const [k, v] of Object.entries(config)) {
      if (v && /key|token|secret/i.test(k) && v.length > 8) return v;
    }
    return config.access_token;
  }

  private readBody(req: IncomingMessage): Promise<string> {
    return new Promise((resolve) => {
      const chunks: Buffer[] = [];
      req.on('data', (c) => chunks.push(c));
      req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
    });
  }
}

function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4);
}

/** Generate IDE integration configs */
export function generateIdeConfigs(port: number): Record<string, unknown> {
  const base = `http://127.0.0.1:${port}`;
  return {
    cursor: {
      'cursor.general.disableHttp2': true,
      openaiBaseUrl: `${base}/cursor/v1`,
      models: { overrideOpenAIBaseURL: `${base}/cursor/v1` },
      note: 'Settings → Models → Override OpenAI Base URL',
    },
    cline: {
      provider: 'OpenAI Compatible',
      baseUrl: `${base}/v1`,
      apiKey: 'use-ocean-virtual-key',
    },
    windsurf: {
      customModelProvider: `${base}/v1`,
      note: 'Settings → AI → Custom Model Provider',
    },
    claudeCode: {
      ANTHROPIC_BASE_URL: `${base}/v1`,
      ANTHROPIC_API_KEY: 'use-ocean-virtual-key',
    },
    zed: {
      'language_models.openai.api_url': `${base}/v1`,
    },
    mcp: {
      mcpServers: {
        ocean: {
          command: 'npx',
          args: ['-y', '@oceanstudio/mcp-bridge', '--port', String(port)],
        },
      },
    },
  };
}
