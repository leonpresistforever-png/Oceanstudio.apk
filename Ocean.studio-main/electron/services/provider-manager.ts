import { EventEmitter } from 'events';
import http from 'http';
import crypto from 'crypto';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import { shell } from 'electron';
import type { OceanProxyService } from './ocean-proxy.js';
import { ProviderVault } from './provider-vault.js';

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export interface ProviderConnectConfig {
  providerId: string;
  name: string;
  authType: string;
  config: Record<string, string>;
  oauth?: {
    authorizeUrl: string;
    tokenUrl: string;
    scopes: string[];
    usePkce?: boolean;
    clientIdEnv?: string;
    clientSecretEnv?: string;
    redirectPort?: number;
    redirectPath?: string;
  };
  pipeline?: {
    modelsEndpoint?: string;
    chatEndpoint?: string;
  };
  defaultModels?: string[];
  embeddedService?: string;
}

export interface ProviderConnectionRecord {
  providerId: string;
  name: string;
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  config: Record<string, string>;
  error?: string;
  connectedAt?: number;
  accountLabel?: string;
  avatarUrl?: string;
  syncedModels?: string[];
  syncedTools?: { name: string; description: string }[];
  syncedSkills?: string[];
}

interface TokenRecord {
  access_token: string;
  refresh_token?: string;
  token_type?: string;
  expires_at?: number;
  account_label?: string;
}

export class ProviderManager extends EventEmitter {
  private connections = new Map<string, ProviderConnectionRecord>();
  private storagePath: string;
  private tokensPath: string;
  private oauthServer: http.Server | null = null;
  private defaultOAuthPort = 8768;
  private pendingOAuth = new Map<string, { providerId: string; codeVerifier?: string; resolve: (t: TokenRecord) => void; reject: (e: Error) => void }>();

  constructor(
    private userDataPath: string,
    private embeddedHandlers?: {
      github?: { authenticate: () => Promise<{ username?: string; avatarUrl?: string }> };
      cloudshell?: { authenticate: () => Promise<{ email?: string }> };
    },
    private oceanProxy?: OceanProxyService,
    vault?: ProviderVault,
  ) {
    super();
    this.storagePath = path.join(userDataPath, 'provider-connections.json');
    this.tokensPath = path.join(userDataPath, 'provider-tokens.json');
    this.vault = vault ?? new ProviderVault(userDataPath);
  }

  private vault: ProviderVault;

  async load(): Promise<void> {
    try {
      if (existsSync(this.storagePath)) {
        const list = JSON.parse(await fs.readFile(this.storagePath, 'utf-8')) as ProviderConnectionRecord[];
        for (const c of list) this.connections.set(c.providerId, c);
      }
    } catch { /* fresh */ }
  }

  private async save(): Promise<void> {
    await fs.writeFile(this.storagePath, JSON.stringify([...this.connections.values()], null, 2));
  }

  private async loadTokens(): Promise<Record<string, TokenRecord>> {
    try {
      if (existsSync(this.tokensPath)) {
        return JSON.parse(await fs.readFile(this.tokensPath, 'utf-8'));
      }
    } catch { /* empty */ }
    return {};
  }

  private async saveToken(providerId: string, token: TokenRecord): Promise<void> {
    const tokens = await this.loadTokens();
    tokens[providerId] = token;
    await fs.writeFile(this.tokensPath, JSON.stringify(tokens, null, 2));
  }

  list(): ProviderConnectionRecord[] {
    return [...this.connections.values()];
  }

  getStatus(providerId: string): { connected: boolean; message: string; accountLabel?: string } {
    const conn = this.connections.get(providerId);
    return {
      connected: conn?.status === 'connected',
      message: conn?.error ?? (conn?.status === 'connected' ? 'Connected' : 'Not connected'),
      accountLabel: conn?.accountLabel,
    };
  }

  async connect(cfg: ProviderConnectConfig): Promise<ProviderConnectionRecord> {
    const record: ProviderConnectionRecord = {
      providerId: cfg.providerId,
      name: cfg.name,
      status: 'connecting',
      config: { ...cfg.config },
    };
    this.connections.set(cfg.providerId, record);
    this.emit('status', cfg.providerId, 'connecting');

    try {
      if (cfg.embeddedService === 'github' && this.embeddedHandlers?.github) {
        const gh = await this.embeddedHandlers.github.authenticate();
        record.status = 'connected';
        record.accountLabel = gh.username;
        record.avatarUrl = gh.avatarUrl;
        record.connectedAt = Date.now();
        record.config._embedded = 'github';
      } else if (cfg.embeddedService === 'cloudshell' && this.embeddedHandlers?.cloudshell) {
        const cs = await this.embeddedHandlers.cloudshell.authenticate();
        record.status = 'connected';
        record.accountLabel = cs.email;
        record.connectedAt = Date.now();
        record.config._embedded = 'cloudshell';
      } else if (cfg.authType === 'api_key' || cfg.authType === 'bearer_token' || cfg.authType === 'manual' || cfg.authType === 'proxy_gateway' || cfg.authType === 'web_cookie' || cfg.authType === 'device_flow') {
        await this.testApiConnection(cfg);
        record.status = 'connected';
        record.connectedAt = Date.now();
      } else if (cfg.authType === 'oauth_redirect' || cfg.authType === 'oauth_pkce') {
        if (!record.config.access_token && !record.config._oauthConnected) {
          throw new Error('OAuth required — call authenticate() first');
        }
        record.status = 'connected';
        record.connectedAt = Date.now();
      } else {
        record.status = 'connected';
        record.connectedAt = Date.now();
      }

      record.syncedModels = await this.syncModels(cfg, record);
      record.syncedTools = this.getDefaultTools(cfg);
      record.syncedSkills = this.buildProviderSkills(cfg);

      // Persist to per-provider vault with default agent system files
      const systemFiles: Record<string, string> = {
        'provider-skill.md': 'See agent/provider-skill.md',
        'ocean-core.md': 'Provider agent harnesses Ocean platform + provider native capabilities',
      };
      await this.vault.save(cfg.providerId, {
        tokens: record.config.access_token ? { access_token: record.config.access_token } : undefined,
        config: record.config,
        tools: record.syncedTools,
        skills: (record.syncedSkills ?? []).join('\n'),
        syncedModels: record.syncedModels,
        accountLabel: record.accountLabel,
        avatarUrl: record.avatarUrl,
        systemFiles,
        pipeline: cfg.pipeline ? {
          requestFormat: 'openai',
          responseFormat: 'openai',
          chatEndpoint: cfg.pipeline.chatEndpoint,
          modelsEndpoint: cfg.pipeline.modelsEndpoint,
        } : undefined,
      });
    } catch (err) {
      record.status = 'error';
      record.error = err instanceof Error ? err.message : 'Connection failed';
    }

    this.connections.set(cfg.providerId, record);
    await this.save();
    this.emit('status', cfg.providerId, record.status);
    return record;
  }

  async authenticate(cfg: ProviderConnectConfig): Promise<{ success: boolean; message: string }> {
    if (!cfg.oauth) {
      return { success: false, message: 'Provider does not support OAuth' };
    }

    const clientId = cfg.config.clientId
      || process.env[cfg.oauth.clientIdEnv ?? '']
      || process.env.OCEAN_OAUTH_CLIENT_ID
      || '';
    const clientSecret = cfg.config.clientSecret
      || process.env[cfg.oauth.clientSecretEnv ?? '']
      || process.env.OCEAN_OAUTH_CLIENT_SECRET
      || '';

    // Auto-start Ocean proxy if not running — enables mobile OAuth via LAN
    if (this.oceanProxy && !this.oceanProxy.getStatus().running) {
      try {
        await this.oceanProxy.start({ port: 20128 });
      } catch { /* fallback to localhost oauth server */ }
    }

    const useProxy = this.oceanProxy?.getStatus().running;
    const redirectUri = useProxy
      ? this.oceanProxy!.getOAuthRedirectUri(cfg.providerId)
      : `http://127.0.0.1:${cfg.oauth.redirectPort ?? this.defaultOAuthPort}${cfg.oauth.redirectPath ?? '/oauth/callback'}`;

    if (!clientId && !useProxy) {
      return { success: false, message: 'OAuth credentials not configured — set env vars or start Ocean proxy' };
    }
    const state = crypto.randomUUID();

    let codeVerifier: string | undefined;
    let challenge: string | undefined;
    if (cfg.oauth.usePkce || cfg.authType === 'oauth_pkce') {
      codeVerifier = crypto.randomBytes(32).toString('base64url');
      challenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');
    }

    const authUrl = new URL(cfg.oauth.authorizeUrl);
    authUrl.searchParams.set('client_id', clientId);
    authUrl.searchParams.set('redirect_uri', redirectUri);
    authUrl.searchParams.set('response_type', 'code');
    authUrl.searchParams.set('state', state);
    if (cfg.oauth.scopes.length) authUrl.searchParams.set('scope', cfg.oauth.scopes.join(' '));
    if (challenge) {
      authUrl.searchParams.set('code_challenge', challenge);
      authUrl.searchParams.set('code_challenge_method', 'S256');
    }
    authUrl.searchParams.set('access_type', 'offline');
    authUrl.searchParams.set('prompt', 'consent');

    try {
      let token: TokenRecord;

      if (useProxy && this.oceanProxy) {
        // Ocean proxy handles OAuth callback — works on mobile via LAN IP
        const callbackPromise = this.oceanProxy.waitForOAuthCallback(cfg.providerId, state);
        void shell.openExternal(authUrl.toString());
        const { code } = await callbackPromise;
        token = await this.exchangeToken(cfg, clientId, clientSecret, redirectUri, code, codeVerifier);
      } else {
        token = await new Promise<TokenRecord>((resolve, reject) => {
          const port = cfg.oauth!.redirectPort ?? this.defaultOAuthPort;
          const redirectPath = cfg.oauth!.redirectPath ?? '/oauth/callback';
          this.pendingOAuth.set(state, { providerId: cfg.providerId, codeVerifier, resolve, reject });
          this.startOAuthServer(port, redirectPath, state, cfg, clientId, clientSecret, redirectUri, codeVerifier);
          setTimeout(() => {
            if (this.pendingOAuth.has(state)) {
              this.pendingOAuth.delete(state);
              reject(new Error('OAuth timed out after 120s'));
            }
          }, 120_000);
          void shell.openExternal(authUrl.toString());
        });
      }

      await this.saveToken(cfg.providerId, token);

      const conn = this.connections.get(cfg.providerId) ?? {
        providerId: cfg.providerId, name: cfg.name, status: 'disconnected' as const, config: {},
      };
      conn.config = { ...conn.config, ...cfg.config, access_token: token.access_token, _oauthConnected: 'true' };
      conn.accountLabel = token.account_label;
      conn.status = 'connected';
      conn.connectedAt = Date.now();
      this.connections.set(cfg.providerId, conn);
      await this.save();

      return { success: true, message: `Signed in${token.account_label ? ` as ${token.account_label}` : ''}` };
    } catch (err) {
      return { success: false, message: err instanceof Error ? err.message : 'OAuth failed' };
    }
  }

  private async exchangeToken(
    cfg: ProviderConnectConfig, clientId: string, clientSecret: string,
    redirectUri: string, code: string, codeVerifier?: string
  ): Promise<TokenRecord> {
    const body = new URLSearchParams({
      grant_type: 'authorization_code', code, redirect_uri: redirectUri, client_id: clientId,
    });
    if (clientSecret) body.set('client_secret', clientSecret);
    if (codeVerifier) body.set('code_verifier', codeVerifier);

    const tokenRes = await fetch(cfg.oauth!.tokenUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: body.toString(),
    });
    if (!tokenRes.ok) throw new Error(`Token exchange failed: ${(await tokenRes.text()).slice(0, 200)}`);
    const tokenData = await tokenRes.json() as TokenRecord & { error?: string };
    if (tokenData.error) throw new Error(tokenData.error);
    return tokenData;
  }

  private buildProviderSkills(cfg: ProviderConnectConfig): string[] {
    const base = ['ocean-platform-integration', 'terminal-access', 'mcp-connectors', 'filesystem'];
    if (cfg.providerId.includes('antigravity')) {
      return [...base, 'gemini-models', 'thinking-mode', 'tool-calling', 'system-instructions', 'project-context'];
    }
    if (cfg.providerId.includes('codex')) {
      return [...base, 'code-generation', 'repository-context', 'tool-calling'];
    }
    if (cfg.providerId.includes('claude')) {
      return [...base, 'claude-models', 'extended-thinking', 'tool-use', 'computer-use'];
    }
    return base;
  }

  private startOAuthServer(
    port: number, redirectPath: string, state: string,
    cfg: ProviderConnectConfig, clientId: string, clientSecret: string,
    redirectUri: string, codeVerifier?: string
  ): void {
    if (this.oauthServer) {
      this.oauthServer.close();
      this.oauthServer = null;
    }

    this.oauthServer = http.createServer(async (req, res) => {
      const remote = req.socket.remoteAddress ?? '';
      if (remote !== '127.0.0.1' && remote !== '::1' && remote !== '::ffff:127.0.0.1') {
        res.writeHead(403); res.end('Forbidden'); return;
      }

      const url = new URL(req.url ?? '/', `http://127.0.0.1:${port}`);
      if (url.pathname !== redirectPath) {
        res.writeHead(404); res.end('Not found'); return;
      }

      const code = url.searchParams.get('code');
      const returnedState = url.searchParams.get('state');
      const error = url.searchParams.get('error');

      if (error) {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<html><body><h2>Auth failed: ${escapeHtml(error)}</h2><p>You can close this window.</p></body></html>`);
        this.pendingOAuth.get(state)?.reject(new Error(error));
        this.oauthServer?.close();
        return;
      }

      if (!code || returnedState !== state) {
        res.writeHead(400); res.end('Invalid OAuth callback'); return;
      }

      try {
        const body = new URLSearchParams({
          grant_type: 'authorization_code',
          code,
          redirect_uri: redirectUri,
          client_id: clientId,
        });
        if (clientSecret) body.set('client_secret', clientSecret);
        if (codeVerifier) body.set('code_verifier', codeVerifier);

        const tokenRes = await fetch(cfg.oauth!.tokenUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
          body: body.toString(),
        });

        if (!tokenRes.ok) {
          const errText = await tokenRes.text();
          throw new Error(`Token exchange failed: ${errText.slice(0, 200)}`);
        }

        const tokenData = await tokenRes.json() as TokenRecord & { error?: string };
        if (tokenData.error) throw new Error(tokenData.error);

        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<html><body style="font-family:sans-serif;text-align:center;padding:40px">
          <h2>✓ ${cfg.name} connected</h2><p>Return to Ocean.studio — you can close this window.</p></body></html>`);

        this.pendingOAuth.get(state)?.resolve(tokenData);
      } catch (err) {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<html><body><h2>Connection failed</h2><p>${escapeHtml(err instanceof Error ? err.message : 'Error')}</p></body></html>`);
        this.pendingOAuth.get(state)?.reject(err instanceof Error ? err : new Error('OAuth failed'));
      } finally {
        this.oauthServer?.close();
        this.oauthServer = null;
        this.pendingOAuth.delete(state);
      }
    });

    this.oauthServer.listen(port, '127.0.0.1');
  }

  private async testApiConnection(cfg: ProviderConnectConfig): Promise<void> {
    const baseUrl = cfg.config.OMNIROUTE_URL || cfg.config.LITELLM_URL || cfg.config.OLLAMA_URL || cfg.config.LMSTUDIO_URL || cfg.config.CUSTOM_ENDPOINT || cfg.config.MODELS_API_BASE_URL;
    if (cfg.authType === 'proxy_gateway' || cfg.authType === 'manual') {
      if (!baseUrl) throw new Error('Endpoint URL required');
      const modelsUrl = cfg.pipeline?.modelsEndpoint
        ? `${baseUrl.replace(/\/$/, '')}${cfg.pipeline.modelsEndpoint}`
        : `${baseUrl.replace(/\/$/, '')}/v1/models`;
      try {
        const headers: Record<string, string> = {};
        const apiKey = cfg.config.OMNIROUTE_API_KEY || cfg.config.LITELLM_API_KEY || cfg.config.CUSTOM_API_KEY || cfg.config.MODELS_API_KEY;
        if (apiKey) headers.Authorization = `Bearer ${apiKey}`;
        const res = await fetch(modelsUrl, { headers, signal: AbortSignal.timeout(8000) });
        if (!res.ok && res.status !== 404) throw new Error(`HTTP ${res.status}`);
      } catch (err) {
        if (cfg.providerId === 'ollama') return; // Ollama may be offline initially
        throw err instanceof Error ? err : new Error('Connection test failed');
      }
    }
  }

  private async syncModels(cfg: ProviderConnectConfig, record: ProviderConnectionRecord): Promise<string[]> {
    if (cfg.defaultModels?.length) return cfg.defaultModels;

    const baseUrl = record.config.OMNIROUTE_URL || record.config.CUSTOM_ENDPOINT || record.config.LMSTUDIO_URL || record.config.MODELS_API_BASE_URL;
    if (!baseUrl || !cfg.pipeline?.modelsEndpoint) return [];

    try {
      const url = `${baseUrl.replace(/\/$/, '')}${cfg.pipeline.modelsEndpoint}`;
      const headers: Record<string, string> = {};
      const apiKey = record.config.access_token || record.config.OPENAI_API_KEY || record.config.OMNIROUTE_API_KEY || record.config.CUSTOM_API_KEY || record.config.MODELS_API_KEY;
      if (apiKey) headers.Authorization = `Bearer ${apiKey}`;
      const res = await fetch(url, { headers, signal: AbortSignal.timeout(8000) });
      if (!res.ok) return cfg.defaultModels ?? [];
      const data = await res.json() as { data?: { id: string }[] };
      return data.data?.map((m) => m.id) ?? cfg.defaultModels ?? [];
    } catch {
      return cfg.defaultModels ?? [];
    }
  }

  private getDefaultTools(cfg: ProviderConnectConfig): { name: string; description: string }[] {
    if (cfg.providerId.includes('antigravity') || cfg.providerId.includes('codex') || cfg.providerId.includes('claude')) {
      return [
        { name: 'run_terminal', description: 'Execute shell commands' },
        { name: 'read_file', description: 'Read workspace files' },
        { name: 'write_file', description: 'Write workspace files' },
        { name: 'search_code', description: 'Search codebase' },
      ];
    }
    return [];
  }

  async disconnect(providerId: string): Promise<void> {
    const conn = this.connections.get(providerId);
    if (conn) {
      conn.status = 'disconnected';
      conn.error = undefined;
      this.connections.set(providerId, conn);
      await this.save();
    }
    const tokens = await this.loadTokens();
    delete tokens[providerId];
    await fs.writeFile(this.tokensPath, JSON.stringify(tokens, null, 2));
    await this.vault?.remove(providerId);
    this.emit('status', providerId, 'disconnected');
  }

  async test(providerId: string): Promise<{ ok: boolean; message: string }> {
    const conn = this.connections.get(providerId);
    if (!conn) return { ok: false, message: 'Not configured' };
    if (conn.status !== 'connected') return { ok: false, message: conn.error ?? 'Not connected' };
    return { ok: true, message: `Connected${conn.accountLabel ? ` as ${conn.accountLabel}` : ''} — ${conn.syncedModels?.length ?? 0} models` };
  }

  async getVaultBundle(providerId: string): Promise<Record<string, unknown>> {
    return this.vault?.getAgentBundle(providerId) ?? {};
  }

  getAgentConfig(): Record<string, unknown> {
    const connected = [...this.connections.values()].filter((c) => c.status === 'connected');
    const providers: Record<string, unknown> = {};
    for (const c of connected) {
      providers[c.providerId] = {
        name: c.name,
        models: c.syncedModels ?? [],
        tools: c.syncedTools ?? [],
        skills: c.syncedSkills ?? [],
        accountLabel: c.accountLabel,
        pipeline: c.config._pipeline ?? 'openai',
      };
    }
    const proxyStatus = this.oceanProxy?.getStatus();
    return {
      providers,
      connectedCount: connected.length,
      proxy: proxyStatus?.running ? { url: proxyStatus.url, lanUrl: proxyStatus.lanUrl } : null,
      updatedAt: new Date().toISOString(),
    };
  }
}
