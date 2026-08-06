import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  Github, X, Download, Upload, GitCommit, ExternalLink, FolderOpen,
  RefreshCw, Plus, Search,
} from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';
import './GitHubPanel.css';

interface GitHubRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  html_url: string;
  clone_url: string;
  description: string | null;
  default_branch: string;
  updated_at: string;
}

interface GitHubStatus {
  connected: boolean;
  authenticated: boolean;
  username?: string;
  avatarUrl?: string;
  message: string;
}

type Tab = 'repos' | 'import' | 'export' | 'commit';

interface Props {
  onClose: () => void;
}

export default function GitHubPanel({ onClose }: Props) {
  const [tab, setTab] = useState<Tab>('repos');
  const [status, setStatus] = useState<GitHubStatus | null>(null);
  const [repos, setRepos] = useState<GitHubRepo[]>([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [importUrl, setImportUrl] = useState('');
  const [exportName, setExportName] = useState('');
  const [exportPrivate, setExportPrivate] = useState(false);
  const [commitMsg, setCommitMsg] = useState('');
  const [result, setResult] = useState('');
  const workspacePath = useAppStore((s) => s.workspacePath);
  const setWorkspacePath = useAppStore((s) => s.setWorkspacePath);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  const api = getOceanAPI() as {
    github?: {
      getStatus: () => Promise<GitHubStatus>;
      authenticate: () => Promise<GitHubStatus>;
      disconnect: () => Promise<void>;
      listRepos: () => Promise<GitHubRepo[]>;
      importRepo: (url: string, dir: string) => Promise<{ success: boolean; path: string; message: string }>;
      exportRepo: (path: string, name: string, priv: boolean, msg: string) => Promise<{ success: boolean; repo: GitHubRepo; message: string }>;
      commitAndPush: (path: string, msg: string) => Promise<{ success: boolean; message: string; output: string }>;
      openRepo: (url: string) => Promise<void>;
    };
  };

  const refresh = useCallback(async () => {
    if (!api.github) return;
    const s = await api.github.getStatus();
    setStatus(s);
    if (s.authenticated) {
      setLoading(true);
      try {
        const r = await api.github.listRepos();
        setRepos(r);
      } catch { /* offline */ }
      setLoading(false);
    }
  }, [api.github]);

  useEffect(() => { refresh(); }, [refresh]);

  async function handleAuth() {
    if (!api.github) return;
    const s = await api.github.authenticate();
    setStatus(s);
    refresh();
  }

  async function handleImport(url?: string) {
    if (!api.github || !workspacePath) return;
    setLoading(true);
    try {
      const res = await api.github.importRepo(url ?? importUrl, workspacePath);
      setResult(res.message);
      if (res.path) setWorkspacePath(res.path);
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: res.message, status: 'completed' });
    } catch (e) {
      setResult(e instanceof Error ? e.message : 'Import failed');
    }
    setLoading(false);
  }

  async function handleExport() {
    if (!api.github || !workspacePath || !exportName) return;
    setLoading(true);
    try {
      const res = await api.github.exportRepo(workspacePath, exportName, exportPrivate, commitMsg || 'Export from Ocean.studio');
      setResult(res.message);
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: res.message, status: 'completed' });
    } catch (e) {
      setResult(e instanceof Error ? e.message : 'Export failed');
    }
    setLoading(false);
  }

  async function handleCommit() {
    if (!api.github || !workspacePath || !commitMsg) return;
    setLoading(true);
    try {
      const res = await api.github.commitAndPush(workspacePath, commitMsg);
      setResult(res.message + (res.output ? `\n${res.output}` : ''));
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: res.message, status: 'completed' });
    } catch (e) {
      setResult(e instanceof Error ? e.message : 'Commit failed');
    }
    setLoading(false);
  }

  const filtered = repos.filter((r) =>
    !search || r.full_name.toLowerCase().includes(search.toLowerCase()) || r.description?.toLowerCase().includes(search.toLowerCase())
  );

  if (!api.github) {
    return (
      <div className="github-panel-overlay" onClick={onClose}>
        <div className="github-panel" onClick={(e) => e.stopPropagation()}>
          <p>GitHub import/export requires the Electron desktop app.</p>
          <p style={{ fontSize: '0.8125rem', color: '#78716C' }}>Use terminal: <code>git clone https://github.com/user/repo</code></p>
          <button onClick={onClose}>Close</button>
        </div>
      </div>
    );
  }

  return (
    <div className="github-panel-overlay" onClick={onClose}>
      <motion.div className="github-panel" onClick={(e) => e.stopPropagation()} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
        <div className="github-panel-header">
          <div className="github-panel-title">
            <Github size={22} />
            <div>
              <h2>GitHub</h2>
              {status?.username && <span className="github-username">@{status.username}</span>}
            </div>
          </div>
          <button className="github-close" onClick={onClose}><X size={18} /></button>
        </div>

        {!status?.authenticated ? (
          <div className="github-auth-prompt">
            <p>Authorize GitHub to import repos, export projects, commit &amp; push changes.</p>
            <button className="github-auth-btn" onClick={handleAuth}>
              <Github size={16} /> Authorize GitHub
            </button>
            <p className="github-auth-hint">Requires GITHUB_CLIENT_ID + GITHUB_CLIENT_SECRET in .env</p>
          </div>
        ) : (
          <>
            <div className="github-tabs">
              {(['repos', 'import', 'export', 'commit'] as Tab[]).map((t) => (
                <button key={t} className={`github-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
                  {t === 'repos' && 'My Repos'}
                  {t === 'import' && <><Download size={14} /> Import</>}
                  {t === 'export' && <><Upload size={14} /> Export</>}
                  {t === 'commit' && <><GitCommit size={14} /> Commit</>}
                </button>
              ))}
            </div>

            <div className="github-body">
              {tab === 'repos' && (
                <>
                  <div className="github-search">
                    <Search size={14} />
                    <input placeholder="Search repositories..." value={search} onChange={(e) => setSearch(e.target.value)} />
                    <button onClick={refresh}><RefreshCw size={14} /></button>
                  </div>
                  <div className="github-repo-list">
                    {loading && <div className="github-loading">Loading...</div>}
                    {filtered.map((repo) => (
                      <div key={repo.id} className="github-repo-row">
                        <div>
                          <strong>{repo.full_name}</strong>
                          {repo.private && <span className="github-private">private</span>}
                          <p>{repo.description ?? 'No description'}</p>
                        </div>
                        <div className="github-repo-actions">
                          <button onClick={() => handleImport(repo.clone_url)} title="Import to workspace"><FolderOpen size={14} /></button>
                          <button onClick={() => api.github?.openRepo(repo.html_url)} title="View on GitHub"><ExternalLink size={14} /></button>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}

              {tab === 'import' && (
                <div className="github-form">
                  <p>Clone any repository into your workspace. Agent can also run <code>git clone</code> in terminal with your PAT.</p>
                  <input placeholder="https://github.com/user/repo.git" value={importUrl} onChange={(e) => setImportUrl(e.target.value)} />
                  <button className="github-action-btn" onClick={() => handleImport()} disabled={!importUrl || loading}>
                    <Download size={14} /> Import Repository
                  </button>
                </div>
              )}

              {tab === 'export' && (
                <div className="github-form">
                  <p>Create a new GitHub repo and push your workspace.</p>
                  <input placeholder="Repository name" value={exportName} onChange={(e) => setExportName(e.target.value)} />
                  <label className="github-checkbox"><input type="checkbox" checked={exportPrivate} onChange={(e) => setExportPrivate(e.target.checked)} /> Private repository</label>
                  <input placeholder="Initial commit message" value={commitMsg} onChange={(e) => setCommitMsg(e.target.value)} />
                  <button className="github-action-btn" onClick={handleExport} disabled={!exportName || loading}>
                    <Upload size={14} /> Export to GitHub
                  </button>
                </div>
              )}

              {tab === 'commit' && (
                <div className="github-form">
                  <p>Commit all changes and push to origin. Works with any git repo in workspace.</p>
                  <p className="github-workspace-path">Workspace: {workspacePath || 'No workspace'}</p>
                  <input placeholder="Commit message" value={commitMsg} onChange={(e) => setCommitMsg(e.target.value)} />
                  <button className="github-action-btn" onClick={handleCommit} disabled={!commitMsg || !workspacePath || loading}>
                    <GitCommit size={14} /> Commit &amp; Push
                  </button>
                </div>
              )}

              {result && <pre className="github-result">{result}</pre>}
            </div>
          </>
        )}
      </motion.div>
    </div>
  );
}
