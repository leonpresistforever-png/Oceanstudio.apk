import { EventEmitter } from 'events';
import http from 'http';
import { URL } from 'url';
import fs from 'fs/promises';
import path from 'path';
import { shell } from 'electron';

const CLOUD_PLATFORM_SCOPE = 'https://www.googleapis.com/auth/cloud-platform';
const CLOUD_SHELL_API = 'https://cloudshell.googleapis.com/v1';
const REDIRECT_URI = 'http://127.0.0.1:8765/oauth/callback';

export interface CloudShellPortPreview {
  port: number;
  url: string;
  openUrl: string;
  source: 'cloudshell';
}

export interface CloudShellEnvironmentMeta {
  webHost?: string;
  machine?: string;
  region?: string;
}

export interface CloudShellStatus {
  connected: boolean;
  authenticated: boolean;
  environmentReady: boolean;
  email?: string;
  message: string;
}

interface TokenStore {
  access_token: string;
  refresh_token?: string;
  expires_at: number;
  email?: string;
}

export class CloudShellService extends EventEmitter {
  private tokens: TokenStore | null = null;
  private environmentReady = false;
  private tokenPath: string;
  private callbackServer: http.Server | null = null;
  private clientId = '';
  private clientSecret = '';

  private environmentMeta: CloudShellEnvironmentMeta = {};
  private authorizedPorts = new Map<number, CloudShellPortPreview>();

  constructor(private userDataPath: string) {
    super();
    this.tokenPath = path.join(userDataPath, 'cloud-shell-tokens.json');
    this.loadTokens();
    this.clientId = process.env.GOOGLE_CLOUD_CLIENT_ID || process.env.VITE_GOOGLE_CLOUD_CLIENT_ID || '';
    this.clientSecret = process.env.GOOGLE_CLOUD_CLIENT_SECRET || '';
  }

  configure(clientId: string, clientSecret: string) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  private async loadTokens() {
    try {
      const raw = await fs.readFile(this.tokenPath, 'utf-8');
      this.tokens = JSON.parse(raw);
    } catch {
      this.tokens = null;
    }
  }

  private async saveTokens() {
    if (this.tokens) {
      await fs.mkdir(path.dirname(this.tokenPath), { recursive: true });
      await fs.writeFile(this.tokenPath, JSON.stringify(this.tokens, null, 2));
    }
  }

  getStatus(): CloudShellStatus {
    return {
      connected: this.environmentReady,
      authenticated: Boolean(this.tokens?.access_token),
      environmentReady: this.environmentReady,
      email: this.tokens?.email,
      message: this.environmentReady
        ? 'Cloud Shell connected'
        : this.tokens?.access_token
        ? 'Authenticated — click Activate Cloud Shell'
        : 'Sign in with Google to connect Cloud Shell',
    };
  }

  /**
   * OAuth flow — user picks Google account in browser, callback auto-captures token.
   */
  async authenticate(): Promise<CloudShellStatus> {
    return this.authenticateWithBrowser();
  }

  async authenticateWithBrowser(): Promise<CloudShellStatus> {
    if (!this.clientId || !this.clientSecret) {
      throw new Error('Set GOOGLE_CLOUD_CLIENT_ID and GOOGLE_CLOUD_CLIENT_SECRET environment variables.');
    }

    const state = crypto.randomUUID();
    const authUrl = new URL('https://accounts.google.com/o/oauth2/v2/auth');
    authUrl.searchParams.set('client_id', this.clientId);
    authUrl.searchParams.set('redirect_uri', REDIRECT_URI);
    authUrl.searchParams.set('response_type', 'code');
    authUrl.searchParams.set('scope', CLOUD_PLATFORM_SCOPE);
    authUrl.searchParams.set('access_type', 'offline');
    authUrl.searchParams.set('prompt', 'consent select_account');
    authUrl.searchParams.set('state', state);

    const codePromise = this.waitForOAuthCallback(state);
    await shell.openExternal(authUrl.toString());
    const code = await codePromise;

    const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        code,
        client_id: this.clientId,
        client_secret: this.clientSecret,
        redirect_uri: REDIRECT_URI,
        grant_type: 'authorization_code',
      }),
    });

    if (!tokenRes.ok) {
      throw new Error(`OAuth token exchange failed: ${await tokenRes.text()}`);
    }

    const tokenData = await tokenRes.json() as {
      access_token: string;
      refresh_token?: string;
      expires_in: number;
    };

    const userRes = await fetch('https://www.googleapis.com/oauth2/v2/userinfo', {
      headers: { Authorization: `Bearer ${tokenData.access_token}` },
    });
    const userInfo = userRes.ok ? await userRes.json() as { email?: string } : {};

    this.tokens = {
      access_token: tokenData.access_token,
      refresh_token: tokenData.refresh_token,
      expires_at: Date.now() + tokenData.expires_in * 1000,
      email: userInfo.email,
    };
    await this.saveTokens();
    return this.getStatus();
  }

  private waitForOAuthCallback(expectedState: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.callbackServer?.close();
        reject(new Error('OAuth timed out after 2 minutes'));
      }, 120000);

      if (this.callbackServer) {
        this.callbackServer.close();
      }

      this.callbackServer = http.createServer((req, res) => {
        const url = new URL(req.url || '/', REDIRECT_URI);
        if (url.pathname !== '/oauth/callback') {
          res.writeHead(404);
          res.end();
          return;
        }

        const code = url.searchParams.get('code');
        const state = url.searchParams.get('state');
        const error = url.searchParams.get('error');

        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(
          error
            ? '<html><body style="font-family:sans-serif;text-align:center;padding:40px"><h2>Authorization failed</h2><p>Close this window and try again.</p></body></html>'
            : '<html><body style="font-family:sans-serif;text-align:center;padding:40px"><h2>Connected</h2><p>Return to Ocean.studio. You can close this window.</p></body></html>'
        );

        clearTimeout(timeout);
        this.callbackServer?.close();
        this.callbackServer = null;

        if (error) {
          reject(new Error(`OAuth denied: ${error}`));
          return;
        }
        if (state !== expectedState || !code) {
          reject(new Error('Invalid OAuth state'));
          return;
        }
        resolve(code);
      });

      this.callbackServer.listen(8765, '127.0.0.1');
    });
  }

  private async getAccessToken(): Promise<string> {
    if (!this.tokens) throw new Error('Not authenticated');

    if (this.tokens.expires_at > Date.now() + 60000) {
      return this.tokens.access_token;
    }

    if (!this.tokens.refresh_token || !this.clientSecret) {
      throw new Error('Token expired — re-authenticate via Setup Cloud Shell');
    }

    const res = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: this.clientId,
        client_secret: this.clientSecret,
        refresh_token: this.tokens.refresh_token,
        grant_type: 'refresh_token',
      }),
    });

    if (!res.ok) throw new Error('Failed to refresh token');

    const data = await res.json() as { access_token: string; expires_in: number };
    this.tokens.access_token = data.access_token;
    this.tokens.expires_at = Date.now() + data.expires_in * 1000;
    await this.saveTokens();
    return this.tokens.access_token;
  }

  async activate(): Promise<CloudShellStatus> {
    const token = await this.getAccessToken();

    const startRes = await fetch(`${CLOUD_SHELL_API}/users/me/environments/default:start`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!startRes.ok && startRes.status !== 409) {
      throw new Error(`Cloud Shell start failed: ${await startRes.text()}`);
    }

    for (let i = 0; i < 30; i++) {
      const envRes = await fetch(`${CLOUD_SHELL_API}/users/me/environments/default`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (envRes.ok) {
        const env = await envRes.json() as { state?: string };
        if (env.state === 'RUNNING') {
          this.environmentReady = true;
          this.emit('ready');
          return this.getStatus();
        }
      }
      await new Promise((r) => setTimeout(r, 2000));
    }

    throw new Error('Cloud Shell did not reach RUNNING state');
  }

  /** Parse WEB_HOST from cloud shell terminal output */
  ingestTerminalOutput(data: string) {
    const webHostMatch = data.match(/WEB_HOST[=:\s]+["']?([^\s'"\\n]+)/i);
    if (webHostMatch?.[1]) {
      this.environmentMeta.webHost = webHostMatch[1].replace(/^https?:\/\//, '');
    }
    const regionMatch = data.match(/cs-([a-z0-9-]+)-vpcf\.cloudshell\.dev/i);
    if (regionMatch?.[1]) {
      this.environmentMeta.region = regionMatch[1];
    }
  }

  private buildProxyUrl(port: number): string {
    const params = new URLSearchParams({
      authuser: '0',
      port: String(port),
      environment_id: 'default',
    });
    return `https://shell.cloud.google.com/devshell/proxy?${params.toString()}`;
  }

  private buildDevUrl(port: number): string {
    if (this.environmentMeta.webHost) {
      const host = this.environmentMeta.webHost.replace(/^https?:\/\//, '').replace(/\/$/, '');
      return `https://${port}-${host}`;
    }
    if (this.environmentMeta.machine && this.environmentMeta.region) {
      const machine = this.environmentMeta.machine.endsWith('-default')
        ? this.environmentMeta.machine
        : `${this.environmentMeta.machine}-default`;
      return `https://${port}-${machine}.cs-${this.environmentMeta.region}-vpcf.cloudshell.dev/?authuser=0`;
    }
    return this.buildProxyUrl(port);
  }

  /**
   * Register a port running in Cloud Shell sandbox — returns HTTPS preview URLs.
   * Preview iframe uses dev URL; openUrl uses Google proxy for auth.
   */
  async registerPort(port: number): Promise<CloudShellPortPreview> {
    if (port < 2000 || port > 65000) {
      throw new Error(`Cloud Shell ports must be 2000–65000 (got ${port})`);
    }

    // Refresh environment metadata when possible
    try {
      const token = await this.getAccessToken();
      const envRes = await fetch(`${CLOUD_SHELL_API}/users/me/environments/default`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (envRes.ok) {
        const env = await envRes.json() as { name?: string; dockerImage?: string };
        const nameParts = env.name?.split('/') ?? [];
        const envId = nameParts[nameParts.length - 1] ?? 'default';
        if (envId.includes('.')) {
          const [, machine, region] = envId.split('.');
          if (machine) this.environmentMeta.machine = machine;
          if (region) this.environmentMeta.region = region.replace(/\.cloudshell\.dev.*/, '');
        }
      }
    } catch { /* use cached meta */ }

    const preview: CloudShellPortPreview = {
      port,
      url: this.buildDevUrl(port),
      openUrl: this.buildProxyUrl(port),
      source: 'cloudshell',
    };
    this.authorizedPorts.set(port, preview);
    this.emit('portRegistered', preview);
    return preview;
  }

  getPortPreview(port: number): CloudShellPortPreview | undefined {
    return this.authorizedPorts.get(port);
  }

  getAuthorizedPorts(): CloudShellPortPreview[] {
    return Array.from(this.authorizedPorts.values());
  }

  setEnvironmentMeta(meta: CloudShellEnvironmentMeta) {
    this.environmentMeta = { ...this.environmentMeta, ...meta };
  }

  async disconnect() {
    this.environmentReady = false;
    this.authorizedPorts.clear();
    this.environmentMeta = {};
    this.tokens = null;
    try { await fs.unlink(this.tokenPath); } catch { /* ignore */ }
  }
}
