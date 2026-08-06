import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, Plug, Check, RefreshCw, Trash2, Key, ExternalLink,
  Monitor, Globe, Smartphone, Zap, Server, Filter,
} from 'lucide-react';
import { getOceanAPI } from '../lib/platform';
import { PROVIDER_CATALOG, getProvidersForPlatform } from '../providers/catalog';
import {
  getProviderConnections, upsertProviderConnection, removeProviderConnection,
} from '../providers/registry';
import { syncProviderConnectionsFromBackend } from '../providers/sync';
import {
  getProviderAuthBadge, AUTH_BADGE_COLORS, hasRequiredConfig, supportsOAuthOnPlatform, isBuiltinOAuthProvider,
} from '../providers/auth';
import { fetchOceanProxyStatus } from '../lib/oceanProxyClient';
import { useProviderStore } from '../store/providerStore';
import { useAppStore } from '../store/appStore';
import type { ProviderConnection, ProviderDefinition, ProviderPlatform } from '../providers/types';
import GatewayPanel from '../components/gateway/GatewayPanel';
import '../pages/PluginsPage.css';
import '../pages/GatewayPage.css';

type ProviderTab = 'all' | 'oauth' | 'apikey' | 'connected' | 'proxy' | 'gateway' | 'fallback';

const PLATFORM_ICONS = { electron: Monitor, web: Globe, mobile: Smartphone };

export default function ProvidersPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const addAgentLog = useAppStore((s) => s.addAgentLog);
  const addActiveProvider = useProviderStore((s) => s.addActiveProvider);
  const removeActiveProvider = useProviderStore((s) => s.removeActiveProvider);
  const activeProviderIds = useProviderStore((s) => s.activeProviderIds);
  const applyProviderModels = useProviderStore((s) => s.applyProviderModels);
  const proxyRunning = useProviderStore((s) => s.proxyRunning);
  const proxyLanUrl = useProviderStore((s) => s.proxyLanUrl);
  const setProxyStatus = useProviderStore((s) => s.setProxyStatus);

  const [platform, setPlatform] = useState<ProviderPlatform>('electron');
  const [tab, setTab] = useState<ProviderTab>('all');
  const [search, setSearch] = useState('');
  const [connections, setConnections] = useState<ProviderConnection[]>(getProviderConnections());
  const [configModal, setConfigModal] = useState<ProviderDefinition | null>(null);
  const [configValues, setConfigValues] = useState<Record<string, string>>({});
  const [connecting, setConnecting] = useState<string | null>(null);
  const [proxyType, setProxyType] = useState<'ocean' | 'omniroute' | 'litellm'>('ocean');
  const [proxyPort, setProxyPort] = useState(20128);

  useEffect(() => {
    fetchOceanProxyStatus().then((s) => {
      if (s.running) setProxyStatus(true, s.url, s.lanUrl);
    });
  }, [setProxyStatus]);

  useEffect(() => {
    getOceanAPI().platform.get().then((info) => {
      if (info.isAndroid) setPlatform('mobile');
      else if (info.isElectron) setPlatform('electron');
      else setPlatform('web');
    });
    void syncProviderConnectionsFromBackend().then(setConnections);
  }, []);

  const refresh = useCallback(() => {
    void syncProviderConnectionsFromBackend().then(setConnections);
  }, []);
  const catalog = getProvidersForPlatform(platform);
  const connectedIds = new Set(connections.filter((c) => c.status === 'connected').map((c) => c.providerId));

  const filtered = catalog.filter((p) => {
    if (tab === 'oauth' && p.authType !== 'oauth_redirect' && p.authType !== 'oauth_pkce') return false;
    if (tab === 'apikey' && p.authType !== 'api_key' && p.authType !== 'bearer_token' && p.authType !== 'proxy_gateway') return false;
    if (tab === 'connected' && !connectedIds.has(p.id)) return false;
    if (search && !`${p.name} ${p.description} ${p.category}`.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  async function handleConnect(def: ProviderDefinition, config: Record<string, string> = {}) {
    const api = getOceanAPI() as {
      providers?: {
        connect: (cfg: unknown) => Promise<ProviderConnection>;
        authenticate: (cfg: unknown) => Promise<{ success: boolean; message: string }>;
      };
    };

    setConnecting(def.id);
    const conn: ProviderConnection = {
      providerId: def.id, name: def.name, status: 'connecting', config,
    };
    upsertProviderConnection(conn);
    refresh();

    try {
      const payload = {
        providerId: def.id, name: def.name, authType: def.authType, config,
        oauth: def.oauth, pipeline: def.pipeline, defaultModels: def.defaultModels,
        embeddedService: def.embeddedService,
      };

      if ((def.authType === 'oauth_redirect' || def.authType === 'oauth_pkce') && !config._oauthConnected) {
        if (!supportsOAuthOnPlatform(def, platform, proxyRunning)) {
          throw new Error('Start Ocean proxy first — OAuth works on mobile via LAN callback');
        }
        const auth = await api.providers?.authenticate?.(payload);
        if (!auth?.success) throw new Error(auth?.message ?? 'OAuth failed');
        config._oauthConnected = 'true';
      }

      if (api.providers?.connect) {
        const result = await api.providers.connect({ ...payload, config });
        upsertProviderConnection(result);
        if (result.status === 'connected') {
          addActiveProvider(def.id);
          applyProviderModels(result);
          addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: `Provider connected: ${def.name}`, status: 'completed' });
        }
      } else {
        upsertProviderConnection({ ...conn, status: 'connected', connectedAt: Date.now(), syncedModels: def.defaultModels });
        addActiveProvider(def.id);
      }
    } catch (e) {
      upsertProviderConnection({ ...conn, status: 'error', error: e instanceof Error ? e.message : 'Failed' });
      addAgentLog({ id: crypto.randomUUID(), type: 'error', timestamp: Date.now(), summary: e instanceof Error ? e.message : 'Connect failed', status: 'failed' });
    } finally {
      setConnecting(null);
      refresh();
      setConfigModal(null);
    }
  }

  function openConfig(def: ProviderDefinition) {
    const existing = getProviderConnections().find((c) => c.providerId === def.id);
    setConfigModal(def);
    setConfigValues(existing?.config ?? {});
  }

  async function handleDisconnect(id: string) {
    const api = getOceanAPI() as { providers?: { disconnect: (id: string) => Promise<void> } };
    await api.providers?.disconnect?.(id);
    removeProviderConnection(id);
    if (activeProviderIds.includes(id)) removeActiveProvider(id);
    refresh();
  }

  async function startProxy() {
    const api = getOceanAPI() as {
      providers?: {
        startProxy: (type: string, port: number) => Promise<{ running: boolean; url: string; lanUrl?: string; message: string }>;
        startOceanProxy?: (port: number) => Promise<{ running: boolean; url: string; lanUrl: string; message: string }>;
      };
    };
    const status = proxyType === 'ocean' && api.providers?.startOceanProxy
      ? await api.providers.startOceanProxy(proxyPort)
      : await api.providers?.startProxy?.(proxyType, proxyPort);
    if (status) {
      setProxyStatus(status.running, status.url, status.lanUrl);
      addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: status.message, status: 'completed' });
    }
  }

  async function stopProxy() {
    const api = getOceanAPI() as {
      providers?: { stopProxy: () => Promise<void>; stopOceanProxy?: () => Promise<void> };
    };
    await api.providers?.stopOceanProxy?.();
    await api.providers?.stopProxy?.();
    setProxyStatus(false);
  }

  const PlatformIcon = PLATFORM_ICONS[platform];

  return (
    <div className="plugins-page">
      <motion.div className="plugins-header" initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }}>
        <div className="plugins-header-left">
          <button className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1>Providers</h1>
          <span className="plugins-platform-badge"><PlatformIcon size={14} /> {platform}</span>
          {activeProviderIds.length > 0 && (
            <span className="plugins-platform-badge" style={{ background: '#22c55e20', color: '#16a34a' }}>
              Active: {activeProviderIds.length} provider{activeProviderIds.length > 1 ? 's' : ''}
            </span>
          )}
        </div>
        <div className="plugins-search">
          <Search size={16} />
          <input placeholder="Search 50+ providers..." value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
      </motion.div>

      <div className="plugins-tabs">
        {([
          ['all', 'All Providers'],
          ['oauth', 'OAuth / Redirect'],
          ['apikey', 'API Key'],
          ['connected', `Connected (${connections.filter((c) => c.status === 'connected').length})`],
          ['fallback', 'Fallback & Backup'],
          ['proxy', 'Proxy Server'],
          ['gateway', 'Gateway Engine'],
        ] as [ProviderTab, string][]).map(([t, label]) => (
          <button key={t} className={`plugins-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>{label}</button>
        ))}
      </div>

      <div className="plugins-body">
        <AnimatePresence mode="wait">
          {tab === 'gateway' ? (
            <motion.div key="gateway" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <GatewayPanel proxyPort={proxyPort} proxyRunning={proxyRunning} />
            </motion.div>
          ) : tab === 'fallback' ? (
            <motion.div key="fallback" initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="providers-fallback-cta">
              <p>Configure Hugging Face free-tier and NVIDIA NIM backup models for rate-limit failover.</p>
              <button type="button" className="plugin-install-btn" onClick={() => setCenterView('fallback')}>
                Open Fallback Configuration
              </button>
            </motion.div>
          ) : tab === 'proxy' ? (
            <motion.div key="proxy" className="plugins-create" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <h3 style={{ marginBottom: 8 }}><Server size={18} style={{ display: 'inline', marginRight: 8 }} />Ocean Proxy Server</h3>
              <p className="plugins-section-desc">
                Ocean native proxy — OAuth callbacks, embedded Gateway Engine (/v1, /cursor), and LAN bind for mobile OAuth.
                Start proxy then open <strong>Gateway Engine</strong> tab for workflows, virtual keys, and IDE configs.
              </p>
              <div className="plugins-create-platforms" style={{ marginBottom: 12 }}>
                {(['ocean', 'omniroute', 'litellm'] as const).map((t) => (
                  <button key={t} className={`plugins-platform-btn ${proxyType === t ? 'active' : ''}`} onClick={() => setProxyType(t)}>
                    {t === 'ocean' ? <Zap size={18} /> : <Server size={18} />}
                    {t === 'ocean' ? 'Ocean (native)' : t === 'omniroute' ? 'OmniRoute' : 'LiteLLM'}
                  </button>
                ))}
              </div>
              <input className="plugins-search" style={{ marginBottom: 12, width: 200 }} type="number"
                placeholder="Port" value={proxyPort} onChange={(e) => setProxyPort(Number(e.target.value))} />
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <button className="plugin-install-btn" onClick={startProxy} disabled={proxyRunning}>
                  <Plug size={14} /> {proxyRunning ? 'Proxy Running' : 'Start Proxy'}
                </button>
                {proxyRunning && <button className="plugins-back" onClick={stopProxy}>Stop</button>}
              </div>
              {proxyRunning && (
                <div style={{ marginTop: 12, fontSize: '0.8125rem', color: '#16a34a' }}>
                  <p>✓ Local: http://localhost:{proxyPort}</p>
                  {proxyLanUrl && proxyLanUrl !== `http://localhost:${proxyPort}` && (
                    <p>✓ LAN (mobile OAuth): {proxyLanUrl}</p>
                  )}
                </div>
              )}
              {platform === 'mobile' && !proxyRunning && (
                <p style={{ marginTop: 8, fontSize: '0.8125rem', color: '#78716C' }}>
                  On APK: start proxy here, then OAuth providers redirect to your device LAN IP.
                </p>
              )}
            </motion.div>
          ) : (
            <motion.div key="grid" className="plugins-grid" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <p className="plugins-section-desc">
                <Filter size={14} style={{ display: 'inline', marginRight: 4 }} />
                {filtered.length} providers — OAuth redirect, API key, gateway, and built-in connections
              </p>
              {filtered.map((def) => (
                <ProviderCard
                  key={def.id}
                  def={def}
                  connected={connectedIds.has(def.id)}
                  active={activeProviderIds.includes(def.id)}
                  connecting={connecting === def.id}
                  platform={platform}
                  onConnect={() => openConfig(def)}
                  onDisconnect={() => handleDisconnect(def.id)}
                  onSetActive={() => addActiveProvider(def.id)}
                />
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {configModal && (
        <div className="mcp-config-overlay" onClick={() => setConfigModal(null)}>
          <div className="mcp-config-modal" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <div style={{
                width: 40, height: 40, borderRadius: 10, background: configModal.brandColor,
                color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700,
              }}>{configModal.logoLetter}</div>
              <div>
                <h3 style={{ margin: 0 }}>Connect {configModal.name}</h3>
                <span className="mcp-auth-badge" style={{ background: AUTH_BADGE_COLORS[getProviderAuthBadge(configModal)] }}>
                  {getProviderAuthBadge(configModal)}
                </span>
              </div>
            </div>
            <p style={{ fontSize: '0.8125rem', color: '#78716C', marginBottom: 16 }}>{configModal.description}</p>

            {configModal.envFields?.map((f) => (
              <label key={f.key} className="mcp-config-field">
                <span>{f.label}{f.required !== false && ' *'}</span>
                <input type={f.secret ? 'password' : 'text'} placeholder={f.placeholder}
                  value={configValues[f.key] ?? ''} onChange={(e) => setConfigValues((v) => ({ ...v, [f.key]: e.target.value }))} />
              </label>
            ))}

            {(configModal.authType === 'oauth_redirect' || configModal.authType === 'oauth_pkce') && (
              isBuiltinOAuthProvider(configModal.id) ? (
                <p style={{ fontSize: '0.8125rem', color: '#16a34a', padding: '8px 12px', background: '#dcfce7', borderRadius: 8 }}>
                  ✓ Built-in OAuth — redirect URI auto-configured via Ocean proxy. Just click Sign in.
                  {proxyRunning && proxyLanUrl && (
                    <span style={{ display: 'block', marginTop: 4, fontSize: '0.75rem', color: '#57534e' }}>
                      Callback: {proxyLanUrl}/oauth/{configModal.id.replace(/\./g, '-')}/callback
                    </span>
                  )}
                </p>
              ) : (
                <>
                  <label className="mcp-config-field"><span>Client ID (or set in .env)</span>
                    <input value={configValues.clientId ?? ''} onChange={(e) => setConfigValues((v) => ({ ...v, clientId: e.target.value }))} />
                  </label>
                  <label className="mcp-config-field"><span>Client Secret</span>
                    <input type="password" value={configValues.clientSecret ?? ''} onChange={(e) => setConfigValues((v) => ({ ...v, clientSecret: e.target.value }))} />
                  </label>
                </>
              )
            )}

            <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
              <button className="plugin-install-btn" disabled={!hasRequiredConfig(configModal, configValues) && configModal.authType !== 'oauth_pkce' && configModal.authType !== 'oauth_redirect'}
                onClick={() => handleConnect(configModal, configValues)}>
                <Plug size={14} />
                {(configModal.authType === 'oauth_redirect' || configModal.authType === 'oauth_pkce') ? 'Sign in & Connect' : 'Connect'}
              </button>
              <button className="plugins-back" onClick={() => setConfigModal(null)}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ProviderCard({ def, connected, active, connecting, platform, onConnect, onDisconnect, onSetActive }: {
  def: ProviderDefinition; connected: boolean; active: boolean; connecting: boolean;
  platform: ProviderPlatform; onConnect: () => void; onDisconnect: () => void; onSetActive: () => void;
}) {
  const badge = getProviderAuthBadge(def);
  return (
    <motion.div className="plugin-card" whileHover={{ y: -2, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' }}
      style={active ? { borderColor: def.brandColor, boxShadow: `0 0 0 1px ${def.brandColor}40` } : undefined}>
      <div className="plugin-card-header">
        <div style={{
          width: 36, height: 36, borderRadius: 8, background: def.brandColor, color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: '1rem',
        }}>{def.logoLetter}</div>
        <div>
          <h3>{def.name}</h3>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 2 }}>
            {def.verified && <span className="plugin-verified"><Check size={10} /> Verified</span>}
            <span className="mcp-auth-badge" style={{ background: AUTH_BADGE_COLORS[badge] }}>{badge}</span>
            {def.freeTier && <span className="mcp-auth-badge" style={{ background: '#22c55e' }}>Free</span>}
            {active && <span className="mcp-auth-badge" style={{ background: def.brandColor }}>Active</span>}
          </div>
        </div>
      </div>
      <p className="plugin-card-desc">{def.description}</p>
      <div className="plugin-card-meta">
        <span className="plugin-type">{def.category}</span>
        {def.features.models && <span>models</span>}
        {def.features.skills && <span>skills</span>}
        {def.features.tools && <span>tools</span>}
      </div>
      <div className="plugin-card-actions">
        {def.docsUrl && <a href={def.docsUrl} target="_blank" rel="noreferrer" className="plugin-link"><ExternalLink size={12} /></a>}
        {connected ? (
          <>
            {!active && <button className="plugins-back" style={{ fontSize: '0.75rem', padding: '4px 8px' }} onClick={onSetActive}>Use in Chat</button>}
            <button className="plugin-install-btn installed" onClick={onDisconnect}><Trash2 size={14} /> Disconnect</button>
          </>
        ) : (
          <button className="plugin-install-btn" onClick={onConnect} disabled={connecting}>
            {connecting ? <RefreshCw size={14} className="spin" /> : def.authType === 'oauth_pkce' || def.authType === 'oauth_redirect' ? <Key size={14} /> : <Plug size={14} />}
            {connecting ? 'Connecting...' : def.authType === 'embedded' ? 'Connect' : 'Configure'}
          </button>
        )}
      </div>
    </motion.div>
  );
}
