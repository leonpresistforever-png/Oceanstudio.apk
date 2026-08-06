import http from 'http';
import https from 'https';
import { EventEmitter } from 'events';
import { URL } from 'url';
import os from 'os';
import type { GatewayEngine } from './gateway/gateway-engine.js';

export interface OceanProxyStatus {
  running: boolean;
  port: number;
  host: string;
  url: string;
  lanUrl: string;
  oauthBaseUrl: string;
  message: string;
}

export interface OceanProxyConfig {
  port?: number;
  bindHost?: string;
  upstreamProxy?: string;
}

/**
 * Ocean.studio native proxy — routes OAuth callbacks and provider API traffic.
 * Binds 0.0.0.0 so mobile/APK can reach OAuth via device LAN IP when proxy is running.
 * No OmniRoute dependency — our app is the gateway.
 */
export class OceanProxyService extends EventEmitter {
  private server: http.Server | null = null;
  private gatewayEngine: GatewayEngine | null = null;
  private status: OceanProxyStatus = {
    running: false,
    port: 20128,
    host: '0.0.0.0',
    url: 'http://localhost:20128',
    lanUrl: 'http://localhost:20128',
    oauthBaseUrl: 'http://localhost:20128/oauth',
    message: 'Ocean proxy not running',
  };

  private pendingOAuth = new Map<string, {
    providerId: string;
    resolve: (code: string, state: string) => void;
    reject: (err: Error) => void;
  }>();

  getStatus(): OceanProxyStatus {
    return { ...this.status };
  }

  setGatewayEngine(engine: GatewayEngine): void {
    this.gatewayEngine = engine;
  }

  getLanIp(): string {
    const nets = os.networkInterfaces();
    for (const name of Object.keys(nets)) {
      for (const net of nets[name] ?? []) {
        if (net.family === 'IPv4' && !net.internal) return net.address;
      }
    }
    return '127.0.0.1';
  }

  async start(config: OceanProxyConfig = {}): Promise<OceanProxyStatus> {
    if (this.server) await this.stop();

    const port = config.port ?? 20128;
    const host = config.bindHost ?? '0.0.0.0';
    const lanIp = this.getLanIp();

    return new Promise((resolve, reject) => {
      this.server = http.createServer((req, res) => {
        this.handleRequest(req, res).catch((err) => {
          res.writeHead(500, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: err instanceof Error ? err.message : 'Proxy error' }));
        });
      });

      this.server.on('error', (err) => {
        this.status.running = false;
        reject(err);
      });

      this.server.listen(port, host, () => {
        this.status = {
          running: true,
          port,
          host,
          url: `http://localhost:${port}`,
          lanUrl: `http://${lanIp}:${port}`,
          oauthBaseUrl: `http://localhost:${port}/oauth`,
          message: `Ocean proxy ready — localhost:${port} | LAN:${lanIp}:${port}`,
        };
        this.emit('ready', this.status);
        resolve(this.getStatus());
      });
    });
  }

  async stop(): Promise<OceanProxyStatus> {
    return new Promise((resolve) => {
      if (!this.server) {
        this.status.running = false;
        this.status.message = 'Ocean proxy stopped';
        resolve(this.getStatus());
        return;
      }
      this.server.close(() => {
        this.server = null;
        this.status.running = false;
        this.status.message = 'Ocean proxy stopped';
        this.emit('stopped');
        resolve(this.getStatus());
      });
    });
  }

  /** OAuth redirect URI for a provider — auto-configured, no manual entry */
  getOAuthRedirectUri(providerId: string): string {
    const safeId = providerId.replace(/\./g, '-');
    return `${this.status.url}/oauth/${safeId}/callback`;
  }

  /** LAN OAuth redirect for mobile/APK */
  getLanOAuthRedirectUri(providerId: string): string {
    const safeId = providerId.replace(/\./g, '-');
    const lanIp = this.getLanIp();
    return `http://${lanIp}:${this.status.port}/oauth/${safeId}/callback`;
  }

  waitForOAuthCallback(providerId: string, state: string, timeoutMs = 120_000): Promise<{ code: string; state: string }> {
    return new Promise((resolve, reject) => {
      const key = `${providerId}:${state}`;
      const timer = setTimeout(() => {
        this.pendingOAuth.delete(key);
        reject(new Error('OAuth callback timed out'));
      }, timeoutMs);

      this.pendingOAuth.set(key, {
        providerId,
        resolve: (code, returnedState) => {
          clearTimeout(timer);
          this.pendingOAuth.delete(key);
          resolve({ code, state: returnedState });
        },
        reject: (err) => {
          clearTimeout(timer);
          this.pendingOAuth.delete(key);
          reject(err);
        },
      });
    });
  }

  private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
    const url = new URL(req.url ?? '/', `http://${req.headers.host}`);
    const path = url.pathname;

    // CORS for mobile web clients hitting LAN proxy
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    if (req.method === 'OPTIONS') {
      res.writeHead(204);
      res.end();
      return;
    }

    if (path === '/ocean-proxy/status' || path === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ...this.getStatus(), gateway: !!this.gatewayEngine }));
      return;
    }

    // Ocean Gateway Engine — /v1/*, /cursor/*, /gateway/*
    if (this.gatewayEngine && (path.startsWith('/v1/') || path.startsWith('/cursor/') || path.startsWith('/gateway/'))) {
      const handled = await this.gatewayEngine.handle(req, res, path);
      if (handled) return;
    }

    // OAuth callback: /oauth/{providerId}/callback?code=...&state=...
    const oauthMatch = path.match(/^\/oauth\/([^/]+)\/callback$/);
    if (oauthMatch && req.method === 'GET') {
      const providerSlug = oauthMatch[1];
      const code = url.searchParams.get('code');
      const state = url.searchParams.get('state');
      const error = url.searchParams.get('error');

      if (error) {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<html><body style="font-family:sans-serif;text-align:center;padding:40px">
          <h2>Connection failed</h2><p>${error}</p></body></html>`);
        for (const [, pending] of this.pendingOAuth) {
          if (pending.providerId.replace(/\./g, '-') === providerSlug) {
            pending.reject(new Error(error));
          }
        }
        return;
      }

      if (code && state) {
        const key = [...this.pendingOAuth.keys()].find((k) => k.endsWith(`:${state}`));
        const pending = key ? this.pendingOAuth.get(key) : undefined;
        if (pending) pending.resolve(code, state);

        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`<html><body style="font-family:sans-serif;text-align:center;padding:40px;background:#FAFAF9">
          <h2 style="color:#16a34a">✓ Connected to Ocean.studio</h2>
          <p>Return to the app — you can close this window.</p></body></html>`);
        return;
      }
    }

    // Reverse proxy: /proxy/{targetHost}/path → upstream
    const proxyMatch = path.match(/^\/proxy\/([^/]+)(\/.*)?$/);
    if (proxyMatch) {
      await this.proxyRequest(req, res, decodeURIComponent(proxyMatch[1]), proxyMatch[2] ?? '/');
      return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      error: 'Not found',
      routes: [
        '/ocean-proxy/status',
        '/oauth/{provider}/callback',
        '/proxy/{host}/...',
        '/v1/models',
        '/v1/chat/completions',
        '/cursor/v1/chat/completions',
        '/gateway/status',
        '/gateway/workflows',
      ],
    }));
  }

  private async proxyRequest(
    req: http.IncomingMessage,
    res: http.ServerResponse,
    targetHost: string,
    targetPath: string
  ): Promise<void> {
    const targetUrl = targetHost.startsWith('http') ? targetHost : `https://${targetHost}`;
    const fullUrl = `${targetUrl.replace(/\/$/, '')}${targetPath}`;

    const headers: Record<string, string> = {};
    for (const [k, v] of Object.entries(req.headers)) {
      if (v && !['host', 'connection'].includes(k.toLowerCase())) {
        headers[k] = Array.isArray(v) ? v[0] : v;
      }
    }

    const body = req.method !== 'GET' && req.method !== 'HEAD'
      ? await new Promise<Buffer>((resolve) => {
          const chunks: Buffer[] = [];
          req.on('data', (c) => chunks.push(c));
          req.on('end', () => resolve(Buffer.concat(chunks)));
        })
      : undefined;

    const parsed = new URL(fullUrl);
    const transport = parsed.protocol === 'https:' ? https : http;

    await new Promise<void>((resolve) => {
      const proxyReq = transport.request(
        fullUrl,
        { method: req.method, headers },
        (proxyRes) => {
          res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers);
          proxyRes.pipe(res);
          proxyRes.on('end', resolve);
        }
      );
      proxyReq.on('error', () => {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Upstream unreachable' }));
        resolve();
      });
      if (body) proxyReq.write(body);
      proxyReq.end();
    });
  }
}
