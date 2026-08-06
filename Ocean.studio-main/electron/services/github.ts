import { EventEmitter } from 'events';
import http from 'http';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import { shell } from 'electron';
import { exec } from 'child_process';
import { promisify } from 'util';

const execAsync = promisify(exec);
const GITHUB_API = 'https://api.github.com';
const REDIRECT_URI = 'http://127.0.0.1:8767/oauth/github/callback';
const SCOPES = ['repo', 'read:user', 'workflow', 'read:org'];

export interface GitHubStatus {
  connected: boolean;
  authenticated: boolean;
  username?: string;
  avatarUrl?: string;
  message: string;
}

export interface GitHubRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  html_url: string;
  clone_url: string;
  ssh_url: string;
  description: string | null;
  default_branch: string;
  updated_at: string;
}

interface TokenStore {
  access_token: string;
  token_type: string;
  scope: string;
  username?: string;
  avatar_url?: string;
}

export class GitHubService extends EventEmitter {
  private tokens: TokenStore | null = null;
  private tokenPath: string;
  private callbackServer: http.Server | null = null;
  private clientId = '';
  private clientSecret = '';

  constructor(private userDataPath: string) {
    super();
    this.tokenPath = path.join(userDataPath, 'github-tokens.json');
    void this.loadTokens();
    this.clientId = process.env.GITHUB_CLIENT_ID || process.env.VITE_GITHUB_CLIENT_ID || '';
    this.clientSecret = process.env.GITHUB_CLIENT_SECRET || '';
  }

  private async loadTokens() {
    try {
      if (existsSync(this.tokenPath)) {
        this.tokens = JSON.parse(await fs.readFile(this.tokenPath, 'utf-8'));
      }
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

  getStatus(): GitHubStatus {
    return {
      connected: Boolean(this.tokens?.access_token),
      authenticated: Boolean(this.tokens?.access_token),
      username: this.tokens?.username,
      avatarUrl: this.tokens?.avatar_url,
      message: this.tokens?.access_token
        ? `Connected as @${this.tokens.username}`
        : 'Authorize GitHub for import, export, and commits',
    };
  }

  getToken(): string | null {
    return this.tokens?.access_token ?? null;
  }

  async authenticate(): Promise<GitHubStatus> {
    if (!this.clientId || !this.clientSecret) {
      throw new Error('Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET in .env (GitHub OAuth App).');
    }

    const state = crypto.randomUUID();
    const authUrl = new URL('https://github.com/login/oauth/authorize');
    authUrl.searchParams.set('client_id', this.clientId);
    authUrl.searchParams.set('redirect_uri', REDIRECT_URI);
    authUrl.searchParams.set('scope', SCOPES.join(' '));
    authUrl.searchParams.set('state', state);

    const code = await this.waitForCallback(state);
    const tokenRes = await fetch('https://github.com/login/oauth/access_token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        client_id: this.clientId,
        client_secret: this.clientSecret,
        code,
        redirect_uri: REDIRECT_URI,
      }),
    });

    if (!tokenRes.ok) throw new Error(`GitHub token exchange failed: ${await tokenRes.text()}`);
    const tokenData = await tokenRes.json() as { access_token?: string; token_type?: string; scope?: string; error?: string };
    if (tokenData.error || !tokenData.access_token) {
      throw new Error(tokenData.error ?? 'No access token received');
    }

    const userRes = await fetch(`${GITHUB_API}/user`, {
      headers: { Authorization: `Bearer ${tokenData.access_token}`, Accept: 'application/vnd.github+json' },
    });
    const user = await userRes.json() as { login: string; avatar_url: string };

    this.tokens = {
      access_token: tokenData.access_token,
      token_type: tokenData.token_type ?? 'bearer',
      scope: tokenData.scope ?? SCOPES.join(' '),
      username: user.login,
      avatar_url: user.avatar_url,
    };
    await this.saveTokens();
    this.emit('authenticated', this.getStatus());
    return this.getStatus();
  }

  private waitForCallback(expectedState: string): Promise<string> {
    return new Promise((resolve, reject) => {
      this.callbackServer?.close();
      this.callbackServer = http.createServer((req, res) => {
        if (!req.url?.startsWith('/oauth/github/callback')) return;
        const u = new URL(req.url, `http://127.0.0.1:8767`);
        const code = u.searchParams.get('code');
        const state = u.searchParams.get('state');
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end('<html><body style="font-family:sans-serif;text-align:center;padding:40px"><h2>GitHub connected!</h2><p>Return to Ocean.studio.</p></body></html>');
        this.callbackServer?.close();
        this.callbackServer = null;
        if (state !== expectedState) reject(new Error('OAuth state mismatch'));
        else if (code) resolve(code);
        else reject(new Error('No authorization code'));
      });
      this.callbackServer.listen(8767, () => {
        shell.openExternal(`https://github.com/login/oauth/authorize?client_id=${this.clientId}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=${encodeURIComponent(SCOPES.join(' '))}&state=${expectedState}`);
      });
      setTimeout(() => {
        this.callbackServer?.close();
        reject(new Error('GitHub OAuth timed out'));
      }, 120000);
    });
  }

  async disconnect(): Promise<void> {
    this.tokens = null;
    try { await fs.unlink(this.tokenPath); } catch { /* ok */ }
    this.emit('disconnected');
  }

  private headers(): Record<string, string> {
    if (!this.tokens?.access_token) throw new Error('GitHub not authenticated');
    return {
      Authorization: `Bearer ${this.tokens.access_token}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
    };
  }

  async listRepos(page = 1, perPage = 30): Promise<GitHubRepo[]> {
    const res = await fetch(`${GITHUB_API}/user/repos?sort=updated&per_page=${perPage}&page=${page}`, { headers: this.headers() });
    if (!res.ok) throw new Error(`GitHub API error: ${res.status}`);
    return res.json() as Promise<GitHubRepo[]>;
  }

  async getRepo(owner: string, repo: string): Promise<GitHubRepo> {
    const res = await fetch(`${GITHUB_API}/repos/${owner}/${repo}`, { headers: this.headers() });
    if (!res.ok) throw new Error(`Repo not found: ${owner}/${repo}`);
    return res.json() as Promise<GitHubRepo>;
  }

  async createRepo(name: string, isPrivate = false, description = ''): Promise<GitHubRepo> {
    const res = await fetch(`${GITHUB_API}/user/repos`, {
      method: 'POST',
      headers: { ...this.headers(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, private: isPrivate, description, auto_init: false }),
    });
    if (!res.ok) throw new Error(`Create repo failed: ${await res.text()}`);
    return res.json() as Promise<GitHubRepo>;
  }

  /** Clone repo into target directory using git + token */
  async importRepo(cloneUrl: string, targetDir: string): Promise<{ success: boolean; path: string; message: string }> {
    const token = this.getToken();
    if (!token) throw new Error('GitHub not authenticated');

    let url = cloneUrl;
    if (url.startsWith('https://github.com/')) {
      url = url.replace('https://github.com/', `https://${token}@github.com/`);
    }

    await fs.mkdir(targetDir, { recursive: true });
    const repoName = cloneUrl.split('/').pop()?.replace('.git', '') ?? 'repo';
    const dest = path.join(targetDir, repoName);

    if (existsSync(dest)) {
      return { success: true, path: dest, message: 'Repository already exists at ' + dest };
    }

    await execAsync(`git clone "${url}" "${dest}"`, { cwd: targetDir });
    return { success: true, path: dest, message: `Imported ${repoName} to ${dest}` };
  }

  /** Commit and push changes from workspace */
  async commitAndPush(
    workspacePath: string,
    message: string,
    remote = 'origin',
    branch?: string
  ): Promise<{ success: boolean; message: string; output: string }> {
    const token = this.getToken();
    if (!token) throw new Error('GitHub not authenticated');

    const cwd = workspacePath;
    if (!existsSync(path.join(cwd, '.git'))) {
      throw new Error('Not a git repository — run git init first or import a repo');
    }

    // Configure credential helper via token in remote URL if needed
    const { stdout: remoteUrl } = await execAsync('git remote get-url origin', { cwd }).catch(() => ({ stdout: '' }));
    if (remoteUrl.includes('github.com') && !remoteUrl.includes(token)) {
      const authed = remoteUrl.trim().replace('https://github.com/', `https://${token}@github.com/`);
      await execAsync(`git remote set-url origin "${authed}"`, { cwd });
    }

    await execAsync('git add -A', { cwd });
    const statusResult = await execAsync('git status --porcelain', { cwd });
    if (!statusResult.stdout.trim()) {
      return { success: true, message: 'Nothing to commit', output: '' };
    }

    await execAsync(`git commit -m "${message.replace(/"/g, '\\"')}"`, { cwd });
    const branchArg = branch ? branch : (await execAsync('git branch --show-current', { cwd })).stdout.trim();
    const pushResult = await execAsync(`git push ${remote} ${branchArg}`, { cwd });

    return { success: true, message: `Pushed to ${remote}/${branchArg}`, output: pushResult.stdout };
  }

  /** Export workspace: init git if needed, create remote repo, push */
  async exportRepo(
    workspacePath: string,
    repoName: string,
    isPrivate = false,
    commitMessage = 'Export from Ocean.studio'
  ): Promise<{ success: boolean; repo: GitHubRepo; message: string }> {
    const cwd = workspacePath;
    const repo = await this.createRepo(repoName, isPrivate, 'Exported from Ocean.studio');

    if (!existsSync(path.join(cwd, '.git'))) {
      await execAsync('git init', { cwd });
      await execAsync('git branch -M main', { cwd });
    }

    const token = this.getToken()!;
    const authedUrl = repo.clone_url.replace('https://github.com/', `https://${token}@github.com/`);
    await execAsync(`git remote remove origin`, { cwd }).catch(() => {});
    await execAsync(`git remote add origin "${authedUrl}"`, { cwd });

    await this.commitAndPush(cwd, commitMessage, 'origin', 'main');
    return { success: true, repo, message: `Exported to https://github.com/${repo.full_name}` };
  }

  /** Open repo in browser */
  openRepo(url: string): void {
    shell.openExternal(url);
  }

  dispose(): void {
    this.callbackServer?.close();
  }
}
