import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, Plug, Check, RefreshCw, Sparkles, Plus, Trash2,
  Monitor, Globe, Smartphone, Cloud, HardDrive, Link2, Key, ExternalLink,
} from 'lucide-react';
import { getOceanAPI } from '../lib/platform';
import { getCatalogForPlatform, MCP_CATALOG } from '../mcp/catalog';
import {
  getConnections, upsertConnection, removeConnection, buildConnectionFromDefinition,
  buildCustomConnection,
} from '../mcp/registry';
import { searchOpenSourceMcp, searchLocalMcpPackages } from '../mcp/researcher';
import type { McpConnection, McpConnectorDefinition, McpPlatform } from '../mcp/types';
import { getConnectorAuthInfo, hasRequiredConfig, AUTH_BADGE_COLORS } from '../mcp/auth';
import { useAppStore } from '../store/appStore';
import '../pages/PluginsPage.css';

type McpTab = 'catalog' | 'opensource' | 'local' | 'connected' | 'custom';

const PLATFORM_ICONS = { electron: Monitor, web: Globe, mobile: Smartphone };

export default function McpConnectorsPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  const [platform, setPlatform] = useState<McpPlatform>('electron');
  const [tab, setTab] = useState<McpTab>('catalog');
  const [search, setSearch] = useState('');
  const [connections, setConnections] = useState<McpConnection[]>(getConnections());
  const [ossResults, setOssResults] = useState<McpConnectorDefinition[]>([]);
  const [localResults, setLocalResults] = useState<McpConnectorDefinition[]>([]);
  const [researching, setResearching] = useState(false);
  const [configModal, setConfigModal] = useState<McpConnectorDefinition | null>(null);
  const [configValues, setConfigValues] = useState<Record<string, string>>({});
  const [customName, setCustomName] = useState('');
  const [customTransport, setCustomTransport] = useState<'stdio' | 'sse' | 'http'>('sse');
  const [customUrl, setCustomUrl] = useState('');
  const [customCommand, setCustomCommand] = useState('npx');
  const [customArgs, setCustomArgs] = useState('-y @modelcontextprotocol/server-filesystem');

  useEffect(() => {
    getOceanAPI().platform.get().then((info) => {
      if (info.isAndroid || info.hasNativeTerminal) setPlatform('mobile');
      else if (info.isElectron) setPlatform('electron');
      else setPlatform('web');
    });
  }, []);

  const refresh = useCallback(() => setConnections(getConnections()), []);

  const catalog = getCatalogForPlatform(platform);

  const runOssSearch = useCallback(async (q?: string) => {
    setResearching(true);
    setTab('opensource');
    try {
      const results = await searchOpenSourceMcp(q ?? search, platform);
      setOssResults(results);
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: `Found ${results.length} cloud MCP connectors`, status: 'completed' });
    } finally {
      setResearching(false);
    }
  }, [search, platform, addAgentLog]);

  const runLocalSearch = useCallback(async (q?: string) => {
    if (platform !== 'electron') return;
    setResearching(true);
    setTab('local');
    try {
      const results = await searchLocalMcpPackages(q ?? search);
      setLocalResults(results);
    } finally {
      setResearching(false);
    }
  }, [search, platform]);

  async function handleConnect(def: McpConnectorDefinition, config: Record<string, string> = {}) {
    const api = getOceanAPI() as {
      mcp?: {
        connect: (cfg: unknown) => Promise<McpConnection>;
        authenticate: (cfg: unknown, clientId?: string, clientSecret?: string) => Promise<{ success: boolean; message: string }>;
      };
    };

    const conn = buildConnectionFromDefinition(def, config);
    conn.status = 'connecting';
    upsertConnection(conn);
    refresh();

    try {
      if (def.auth?.type === 'oauth' && api.mcp?.authenticate) {
        const auth = await api.mcp.authenticate({
          id: def.id, name: def.name, transport: def.transport, hosting: def.hosting,
          url: def.url, command: def.command, args: def.args, env: config,
          auth: def.auth,
        }, config.clientId, config.clientSecret);
        if (!auth.success) throw new Error(auth.message);
      }

      if (api.mcp?.connect) {
        const result = await api.mcp.connect({
          id: def.id, name: def.name, transport: def.transport, hosting: def.hosting,
          command: def.command, args: def.args, url: def.url, env: config,
        });
        upsertConnection({ ...conn, status: result.status ?? 'connected', error: result.error });
      } else if (def.transport === 'sse' || def.transport === 'http') {
        upsertConnection({ ...conn, status: 'connected' });
      } else {
        throw new Error('Local stdio MCP requires the Electron desktop app');
      }

      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: `MCP connected: ${def.name}`, status: 'completed' });
    } catch (e) {
      upsertConnection({ ...conn, status: 'error', error: e instanceof Error ? e.message : 'Failed' });
      addAgentLog({ id: crypto.randomUUID(), type: 'error', timestamp: Date.now(), summary: e instanceof Error ? e.message : 'MCP connect failed', status: 'failed' });
    }
    refresh();
    setConfigModal(null);
  }

  function openConfig(def: McpConnectorDefinition) {
    const auth = getConnectorAuthInfo(def);
    const existing = getConnections().find((c) => c.connectorId === def.id);
    const config = existing?.config ?? {};

    // Instant-connect SSE / local npx — skip modal
    if (auth.canInstantConnect && auth.requiredFields.length === 0) {
      void handleConnect(def, config);
      return;
    }

    setConfigModal(def);
    setConfigValues(config);
  }

  async function handleDisconnect(id: string) {
    const api = getOceanAPI() as { mcp?: { disconnect: (id: string) => Promise<void> } };
    await api.mcp?.disconnect?.(id);
    removeConnection(id);
    refresh();
  }

  async function handleCustomConnect() {
    const config: Record<string, string> = {};
    if (customTransport === 'stdio') {
      config.command = customCommand;
      config.args = JSON.stringify(customArgs.split(' '));
    } else {
      config.url = customUrl;
    }
    const conn = buildCustomConnection(customName || 'Custom MCP', customTransport, config);
    upsertConnection({ ...conn, status: 'connected' });
    refresh();
    addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: `Custom MCP added: ${conn.name}`, status: 'completed' });
  }

  const filteredCatalog = catalog.filter((c) =>
    !search || `${c.name} ${c.description} ${c.category}`.toLowerCase().includes(search.toLowerCase())
  );

  const PlatformIcon = PLATFORM_ICONS[platform];
  const connectedIds = new Set(connections.filter((c) => c.status === 'connected').map((c) => c.connectorId));

  return (
    <div className="plugins-page">
      <motion.div className="plugins-header" initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }}>
        <div className="plugins-header-left">
          <button className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1>MCP Connectors</h1>
          <span className="plugins-platform-badge"><PlatformIcon size={14} /> {platform}</span>
        </div>
        <div className="plugins-search">
          <Search size={16} />
          <input
            placeholder="Search MCP connectors..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                if (tab === 'opensource') runOssSearch();
                else if (tab === 'local') runLocalSearch();
              }
            }}
          />
        </div>
      </motion.div>

      <div className="plugins-tabs">
        {([
          ['catalog', 'Ready to Use'],
          ['opensource', 'Open Source Cloud'],
          ...(platform === 'electron' ? [['local', 'Local (npx)'] as const] : []),
          ['connected', `Connected (${connections.filter((c) => c.status === 'connected').length})`],
          ['custom', 'Create Custom'],
        ] as [McpTab, string][]).map(([t, label]) => (
          <button key={t} className={`plugins-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>{label}</button>
        ))}
      </div>

      <div className="plugins-body">
        <AnimatePresence mode="wait">
          {tab === 'catalog' && (
            <motion.div key="cat" className="plugins-grid" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <p className="plugins-section-desc">
                {catalog.length}+ MCP connectors — {platform === 'electron' ? 'stdio + SSE' : 'cloud SSE/HTTP'} — click Connect to authorize
              </p>
              {filteredCatalog.map((def) => (
                <McpCard key={def.id} def={def} connected={connectedIds.has(def.id)} onConnect={() => openConfig(def)} onDisconnect={() => handleDisconnect(def.id)} platform={platform} />
              ))}
            </motion.div>
          )}

          {tab === 'opensource' && (
            <motion.div key="oss" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <div className="plugins-researcher-bar">
                <Sparkles size={16} />
                <span>Cloud MCP researcher — SSE &amp; HTTP connectors from GitHub</span>
                <button onClick={() => runOssSearch()} disabled={researching}>
                  <RefreshCw size={14} className={researching ? 'spin' : ''} />
                  {researching ? 'Searching...' : 'Search'}
                </button>
              </div>
              <div className="plugins-grid">
                {(ossResults.length ? ossResults : catalog.filter((c) => c.hosting === 'cloud')).filter((c) =>
                  !search || `${c.name} ${c.description}`.toLowerCase().includes(search.toLowerCase())
                ).map((def) => (
                  <McpCard key={def.id} def={def} connected={connectedIds.has(def.id)} onConnect={() => openConfig(def)} onDisconnect={() => handleDisconnect(def.id)} platform={platform} />
                ))}
              </div>
            </motion.div>
          )}

          {tab === 'local' && platform === 'electron' && (
            <motion.div key="local" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <div className="plugins-researcher-bar">
                <HardDrive size={16} />
                <span>Local MCP — npx stdio servers on your computer</span>
                <button onClick={() => runLocalSearch()} disabled={researching}>
                  <RefreshCw size={14} className={researching ? 'spin' : ''} /> Search npm
                </button>
              </div>
              <div className="plugins-grid">
                {(localResults.length ? localResults : catalog.filter((c) => c.hosting === 'local')).map((def) => (
                  <McpCard key={def.id} def={def} connected={connectedIds.has(def.id)} onConnect={() => openConfig(def)} onDisconnect={() => handleDisconnect(def.id)} platform={platform} />
                ))}
              </div>
            </motion.div>
          )}

          {tab === 'connected' && (
            <motion.div key="conn" className="plugins-installed-list" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              {connections.length === 0 && <div className="plugins-empty">No MCP connections yet</div>}
              {connections.map((c) => (
                <div key={c.connectorId} className="plugins-installed-row">
                  <div className="plugins-installed-info">
                    <strong>{c.name}</strong>
                    <span className={`plugins-status plugins-status-${c.status}`}>{c.status}</span>
                    <span className="plugins-installed-desc">{c.transport} · {c.hosting}{c.error ? ` — ${c.error}` : ''}</span>
                  </div>
                  <div className="plugins-installed-actions">
                    {c.status === 'connected' && (
                      <button onClick={() => handleDisconnect(c.connectorId)} title="Disconnect"><Trash2 size={14} /></button>
                    )}
                  </div>
                </div>
              ))}
            </motion.div>
          )}

          {tab === 'custom' && (
            <motion.div key="custom" className="plugins-create" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <h3 style={{ marginBottom: 12 }}>Create Custom MCP Connection</h3>
              <div className="plugins-create-platforms">
                {(['sse', 'http', ...(platform === 'electron' ? (['stdio'] as const) : [])] as ('sse' | 'http' | 'stdio')[]).map((t) => (
                  <button key={t} className={`plugins-platform-btn ${customTransport === t ? 'active' : ''}`} onClick={() => setCustomTransport(t)}>
                    {t === 'stdio' ? <HardDrive size={18} /> : <Cloud size={18} />}
                    {t.toUpperCase()}
                  </button>
                ))}
              </div>
              <input className="plugins-search" style={{ marginBottom: 12, width: '100%' }} placeholder="Connection name" value={customName} onChange={(e) => setCustomName(e.target.value)} />
              {customTransport === 'stdio' ? (
                <>
                  <input className="plugins-search" style={{ marginBottom: 8, width: '100%' }} placeholder="Command (npx)" value={customCommand} onChange={(e) => setCustomCommand(e.target.value)} />
                  <input className="plugins-search" style={{ marginBottom: 12, width: '100%' }} placeholder="Args (-y @package/name)" value={customArgs} onChange={(e) => setCustomArgs(e.target.value)} />
                </>
              ) : (
                <input className="plugins-search" style={{ marginBottom: 12, width: '100%' }} placeholder="SSE/HTTP URL (https://...)" value={customUrl} onChange={(e) => setCustomUrl(e.target.value)} />
              )}
              <button className="plugin-install-btn" onClick={handleCustomConnect}><Plus size={14} /> Connect</button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {configModal && (
        <div className="mcp-config-overlay" onClick={() => setConfigModal(null)}>
          <div className="mcp-config-modal" onClick={(e) => e.stopPropagation()}>
            <h3>Connect {configModal.name}</h3>
            <p style={{ fontSize: '0.8125rem', color: '#78716C', marginBottom: 8 }}>{configModal.description}</p>
            {(() => {
              const auth = getConnectorAuthInfo(configModal);
              return (
                <div className="mcp-auth-badge-row" style={{ marginBottom: 16 }}>
                  <span className="mcp-auth-badge" style={{ background: AUTH_BADGE_COLORS[auth.mode] }}>
                    {auth.label}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: '#78716C' }}>{auth.description}</span>
                </div>
              );
            })()}
            {configModal.envFields?.map((f) => (
              <label key={f.key} className="mcp-config-field">
                <span>{f.label}{f.required && ' *'}</span>
                <input
                  type={f.secret ? 'password' : 'text'}
                  placeholder={f.placeholder}
                  value={configValues[f.key] ?? ''}
                  onChange={(e) => setConfigValues((v) => ({ ...v, [f.key]: e.target.value }))}
                />
              </label>
            ))}
            {configModal.auth?.type === 'oauth' && (
              <>
                <label className="mcp-config-field"><span>OAuth Client ID</span>
                  <input value={configValues.clientId ?? ''} onChange={(e) => setConfigValues((v) => ({ ...v, clientId: e.target.value }))} />
                </label>
                <label className="mcp-config-field"><span>OAuth Client Secret</span>
                  <input type="password" value={configValues.clientSecret ?? ''} onChange={(e) => setConfigValues((v) => ({ ...v, clientSecret: e.target.value }))} />
                </label>
              </>
            )}
            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <button
                className="plugin-install-btn"
                disabled={!hasRequiredConfig(configModal, configValues)}
                onClick={() => handleConnect(configModal, configValues)}
              >
                <Plug size={14} /> Connect
              </button>
              {configModal.auth?.type === 'oauth' && (
                <button className="plugins-search-btn" onClick={() => handleConnect(configModal, configValues)}>
                  <Key size={14} /> OAuth Login
                </button>
              )}
              <button className="plugins-back" onClick={() => setConfigModal(null)}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function McpCard({ def, connected, onConnect, onDisconnect, platform }: {
  def: McpConnectorDefinition; connected: boolean; onConnect: () => void; onDisconnect: () => void;
  platform: McpPlatform;
}) {
  const auth = getConnectorAuthInfo(def);
  const instantOnPlatform = auth.canInstantConnect && (def.hosting === 'cloud' || platform === 'electron');

  return (
    <motion.div className="plugin-card" whileHover={{ y: -2, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' }}>
      <div className="plugin-card-header">
        {def.hosting === 'cloud' ? <Cloud size={20} /> : <HardDrive size={20} />}
        <div>
          <h3>{def.name}</h3>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 2 }}>
            {def.verified && <span className="plugin-verified"><Check size={10} /> Verified</span>}
            <span className="mcp-auth-badge" style={{ background: AUTH_BADGE_COLORS[auth.mode] }}>{auth.label}</span>
          </div>
        </div>
      </div>
      <p className="plugin-card-desc">{def.description}</p>
      <div className="plugin-card-meta">
        <span className="plugin-type">{def.transport}</span>
        <span className="plugin-type">{def.hosting}</span>
        <span>{def.category}</span>
      </div>
      <div className="plugin-card-actions">
        {def.docsUrl && <a href={def.docsUrl} target="_blank" rel="noreferrer" className="plugin-link"><ExternalLink size={12} /></a>}
        {connected ? (
          <button className="plugin-install-btn installed" onClick={onDisconnect}><Trash2 size={14} /> Disconnect</button>
        ) : (
          <button className="plugin-install-btn" onClick={onConnect}>
            <Plug size={14} /> {instantOnPlatform && auth.requiredFields.length === 0 ? 'Connect Now' : 'Configure'}
          </button>
        )}
      </div>
    </motion.div>
  );
}
