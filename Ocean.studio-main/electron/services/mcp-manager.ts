import { EventEmitter } from 'events';
import { spawn, type ChildProcess } from 'child_process';
import http from 'http';
import { URL } from 'url';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import { shell } from 'electron';

export interface McpConnectorConfig {
  id: string;
  name: string;
  transport: 'stdio' | 'sse' | 'http' | 'websocket';
  hosting: 'local' | 'cloud';
  command?: string;
  args?: string[];
  url?: string;
  env?: Record<string, string>;
  headers?: Record<string, string>;
  auth?: {
    type: string;
    oauthAuthorizeUrl?: string;
    oauthTokenUrl?: string;
    oauthScopes?: string[];
    clientId?: string;
    clientSecret?: string;
  };
}

export interface McpConnectionRecord {
  connectorId: string;
  name: string;
  transport: string;
  hosting: string;
  config: Record<string, string>;
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  error?: string;
  connectedAt?: number;
  custom?: boolean;
}

interface RunningConnection {
  process?: ChildProcess;
  abortController?: AbortController;
  config: McpConnectorConfig;
}

export class McpManager extends EventEmitter {
  private connections: Map<string, McpConnectionRecord> = new Map();
  private running: Map<string, RunningConnection> = new Map();
  private storagePath: string;
  private tokensPath: string;
  private oauthServer: http.Server | null = null;
  private oauthPort = 8766;

  constructor(userDataPath: string) {
    super();
    this.storagePath = path.join(userDataPath, 'mcp-connections.json');
    this.tokensPath = path.join(userDataPath, 'mcp-tokens.json');
  }

  async load(): Promise<void> {
    try {
      if (existsSync(this.storagePath)) {
        const raw = await fs.readFile(this.storagePath, 'utf-8');
        const list = JSON.parse(raw) as McpConnectionRecord[];
        for (const c of list) this.connections.set(c.connectorId, c);
      }
    } catch { /* fresh */ }
  }

  private async save(): Promise<void> {
    await fs.writeFile(this.storagePath, JSON.stringify([...this.connections.values()], null, 2));
  }

  private async loadTokens(): Promise<Record<string, string>> {
    try {
      if (existsSync(this.tokensPath)) {
        return JSON.parse(await fs.readFile(this.tokensPath, 'utf-8'));
      }
    } catch { /* empty */ }
    return {};
  }

  private async saveToken(connectorId: string, token: string): Promise<void> {
    const tokens = await this.loadTokens();
    tokens[connectorId] = token;
    await fs.writeFile(this.tokensPath, JSON.stringify(tokens, null, 2));
  }

  list(): McpConnectionRecord[] {
    return [...this.connections.values()];
  }

  getAgentConfig(): Record<string, unknown> {
    const connected = [...this.connections.values()].filter((c) => c.status === 'connected');
    const mcpServers: Record<string, unknown> = {};
    for (const c of connected) {
      const key = c.connectorId.replace(/^mcp\./, '').replace(/^custom\./, 'custom-');
      if (c.transport === 'stdio') {
        mcpServers[key] = {
          command: c.config.command ?? 'npx',
          args: c.config.args ? JSON.parse(c.config.args) : [],
          env: Object.fromEntries(Object.entries(c.config).filter(([k]) => !['command', 'args', 'url'].includes(k))),
        };
      } else {
        mcpServers[key] = { url: c.config.url, transport: c.transport };
      }
    }
    return { mcpServers, connected: connected.map((c) => c.connectorId) };
  }

  async connect(def: McpConnectorConfig): Promise<McpConnectionRecord> {
    const record: McpConnectionRecord = {
      connectorId: def.id,
      name: def.name,
      transport: def.transport,
      hosting: def.hosting,
      config: { ...def.env, command: def.command, args: def.args ? JSON.stringify(def.args) : undefined, url: def.url } as Record<string, string>,
      status: 'connecting',
    };
    this.connections.set(def.id, record);
    await this.save();

    try {
      if (def.transport === 'stdio') {
        await this.connectStdio(def);
      } else if (def.transport === 'sse' || def.transport === 'http') {
        await this.connectRemote(def);
      } else {
        throw new Error(`Transport ${def.transport} not yet supported`);
      }
      record.status = 'connected';
      record.connectedAt = Date.now();
      record.error = undefined;
    } catch (e) {
      record.status = 'error';
      record.error = e instanceof Error ? e.message : 'Connection failed';
    }

    this.connections.set(def.id, record);
    await this.save();
    this.emit('connection:changed', record);
    return record;
  }

  private async connectStdio(def: McpConnectorConfig): Promise<void> {
    if (!def.command) throw new Error('stdio requires command');
    const env = { ...process.env, ...def.env } as NodeJS.ProcessEnv;
    const child = spawn(def.command, def.args ?? [], {
      env,
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: process.platform === 'win32',
    });

    child.stderr?.on('data', (d: Buffer) => {
      this.emit('mcp:log', def.id, d.toString());
    });

    child.on('exit', (code) => {
      this.running.delete(def.id);
      const rec = this.connections.get(def.id);
      if (rec) {
        rec.status = code === 0 ? 'disconnected' : 'error';
        rec.error = code !== 0 ? `Process exited ${code}` : undefined;
        this.save();
        this.emit('connection:changed', rec);
      }
    });

    this.running.set(def.id, { process: child, config: def });
  }

  private async connectRemote(def: McpConnectorConfig): Promise<void> {
    if (!def.url) throw new Error('Remote transport requires url');
    const headers: Record<string, string> = { Accept: 'text/event-stream', ...def.headers };
    const tokens = await this.loadTokens();
    if (tokens[def.id]) {
      headers.Authorization = `Bearer ${tokens[def.id]}`;
    }
    for (const [k, v] of Object.entries(def.env ?? {})) {
      if (k.includes('KEY') || k.includes('TOKEN')) headers.Authorization = `Bearer ${v}`;
    }

    const controller = new AbortController();
    const res = await fetch(def.url, { headers, signal: controller.signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    this.running.set(def.id, { abortController: controller, config: def });
  }

  async disconnect(id: string): Promise<void> {
    const running = this.running.get(id);
    if (running?.process) running.process.kill();
    if (running?.abortController) running.abortController.abort();
    this.running.delete(id);

    const rec = this.connections.get(id);
    if (rec) {
      rec.status = 'disconnected';
      rec.error = undefined;
      await this.save();
      this.emit('connection:changed', rec);
    }
  }

  async authenticate(def: McpConnectorConfig, clientId?: string, clientSecret?: string): Promise<{ success: boolean; message: string }> {
    if (!def.auth?.oauthAuthorizeUrl) {
      return { success: false, message: 'No OAuth configured — use API key instead' };
    }

    return new Promise((resolve) => {
      const redirectUri = `http://127.0.0.1:${this.oauthPort}/oauth/callback`;
      const state = crypto.randomUUID();
      const scope = (def.auth?.oauthScopes ?? []).join(' ');
      const authUrl = new URL(def.auth!.oauthAuthorizeUrl!);
      authUrl.searchParams.set('client_id', clientId ?? '');
      authUrl.searchParams.set('redirect_uri', redirectUri);
      authUrl.searchParams.set('state', state);
      authUrl.searchParams.set('response_type', 'code');
      if (scope) authUrl.searchParams.set('scope', scope);

      this.oauthServer?.close();
      this.oauthServer = http.createServer(async (req, res) => {
        if (!req.url?.startsWith('/oauth/callback')) return;
        const u = new URL(req.url, `http://127.0.0.1:${this.oauthPort}`);
        const code = u.searchParams.get('code');
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end('<html><body><h2>Connected! Return to Ocean.studio.</h2><script>window.close()</script></body></html>');

        if (code && def.auth?.oauthTokenUrl) {
          try {
            const tokenRes = await fetch(def.auth.oauthTokenUrl, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
              body: JSON.stringify({
                client_id: clientId,
                client_secret: clientSecret,
                code,
                redirect_uri: redirectUri,
                grant_type: 'authorization_code',
              }),
            });
            const tokenData = await tokenRes.json() as { access_token?: string };
            if (tokenData.access_token) {
              await this.saveToken(def.id, tokenData.access_token);
              resolve({ success: true, message: 'OAuth complete — token saved' });
            } else {
              resolve({ success: false, message: 'Token exchange failed' });
            }
          } catch (e) {
            resolve({ success: false, message: e instanceof Error ? e.message : 'OAuth failed' });
          }
        } else {
          resolve({ success: false, message: 'No authorization code received' });
        }
        this.oauthServer?.close();
        this.oauthServer = null;
      });

      this.oauthServer.listen(this.oauthPort, () => {
        shell.openExternal(authUrl.toString());
      });
    });
  }

  async test(id: string): Promise<{ ok: boolean; message: string }> {
    const rec = this.connections.get(id);
    if (!rec) return { ok: false, message: 'Not found' };
    if (rec.status === 'connected') return { ok: true, message: 'Connected and running' };
    return { ok: false, message: rec.error ?? 'Not connected' };
  }

  disposeAll(): void {
    for (const [id, r] of this.running) {
      r.process?.kill();
      r.abortController?.abort();
      this.running.delete(id);
    }
    this.oauthServer?.close();
  }
}
