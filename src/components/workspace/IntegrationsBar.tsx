import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Github, Cloud, Plug, Puzzle, GitBranch, Upload, Download,
  ExternalLink, Check,
} from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';
import GitHubPanel from './GitHubPanel';
import './IntegrationsBar.css';

interface Integration {
  id: string;
  name: string;
  icon: typeof Github;
  color: string;
  connected: boolean;
  onClick: () => void;
}

export default function IntegrationsBar() {
  const [expanded, setExpanded] = useState(false);
  const [showGitHub, setShowGitHub] = useState(false);
  const [ghStatus, setGhStatus] = useState({ connected: false, username: '' });
  const [cloudStatus, setCloudStatus] = useState({ connected: false });
  const setCenterView = useAppStore((s) => s.setCenterView);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  const refreshStatus = useCallback(async () => {
    const api = getOceanAPI();
    try {
      const gh = await (api as { github?: { getStatus: () => Promise<{ connected: boolean; username?: string }> } }).github?.getStatus();
      if (gh) setGhStatus({ connected: gh.connected, username: gh.username ?? '' });
    } catch { /* web fallback */ }
    try {
      const cs = await api.cloudshell.getStatus();
      setCloudStatus({ connected: cs.authenticated });
    } catch { /* ok */ }
  }, []);

  useEffect(() => { refreshStatus(); }, [refreshStatus]);

  async function handleGitHubAuth() {
    const api = getOceanAPI() as { github?: { authenticate: () => Promise<{ message: string }> } };
    if (!api.github) {
      addAgentLog({ id: crypto.randomUUID(), type: 'error', timestamp: Date.now(), summary: 'GitHub workflow requires Electron desktop app', status: 'failed' });
      return;
    }
    try {
      const result = await api.github.authenticate();
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: result.message, status: 'completed' });
      refreshStatus();
    } catch (e) {
      addAgentLog({ id: crypto.randomUUID(), type: 'error', timestamp: Date.now(), summary: e instanceof Error ? e.message : 'GitHub auth failed', status: 'failed' });
    }
  }

  const integrations: Integration[] = [
    {
      id: 'github',
      name: 'GitHub',
      icon: Github,
      color: '#1C1917',
      connected: ghStatus.connected,
      onClick: () => ghStatus.connected ? setShowGitHub(true) : handleGitHubAuth(),
    },
    {
      id: 'cloud',
      name: 'Cloud Shell',
      icon: Cloud,
      color: '#4285F4',
      connected: cloudStatus.connected,
      onClick: async () => {
        const api = getOceanAPI();
        if (!cloudStatus.connected) await api.cloudshell.authenticate();
        refreshStatus();
      },
    },
    {
      id: 'mcp',
      name: 'MCP',
      icon: Plug,
      color: '#7C3AED',
      connected: false,
      onClick: () => setCenterView('mcp'),
    },
    {
      id: 'plugins',
      name: 'Plugins',
      icon: Puzzle,
      color: '#059669',
      connected: false,
      onClick: () => setCenterView('plugins'),
    },
  ];

  return (
    <>
      <motion.div
        className={`integrations-bar ${expanded ? 'expanded' : ''}`}
        initial={{ y: 60 }}
        animate={{ y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <button className="integrations-toggle" onClick={() => setExpanded(!expanded)} title="Integrations">
          {expanded ? '▼' : '▲'} Integrations
        </button>

        <div className="integrations-track">
          {integrations.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                className={`integration-chip ${item.connected ? 'connected' : ''}`}
                onClick={item.onClick}
                title={item.connected ? `${item.name} — connected` : `Connect ${item.name}`}
              >
                <Icon size={18} />
                <span>{item.name}</span>
                {item.connected && <Check size={12} className="integration-check" />}
              </button>
            );
          })}
          <button className="integration-chip" onClick={() => setShowGitHub(true)} title="GitHub Import / Export">
            <Download size={16} /><Upload size={16} style={{ marginLeft: -6 }} />
            <span>Import/Export</span>
          </button>
          <button className="integration-chip" onClick={() => setShowGitHub(true)} title="Commit & Push">
            <GitBranch size={18} />
            <span>Commit</span>
          </button>
        </div>
      </motion.div>

      <AnimatePresence>
        {showGitHub && (
          <GitHubPanel onClose={() => { setShowGitHub(false); refreshStatus(); }} />
        )}
      </AnimatePresence>
    </>
  );
}
