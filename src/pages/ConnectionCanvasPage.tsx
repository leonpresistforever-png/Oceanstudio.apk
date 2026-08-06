import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import {
  Settings, Unplug, Zap, Waves, RefreshCw, ExternalLink, Check,
} from 'lucide-react';
import { getOceanAPI } from '../lib/platform';
import { PROVIDER_CATALOG } from '../providers/catalog';
import {
  getProviderConnections, removeProviderConnection,
} from '../providers/registry';
import { syncProviderConnectionsFromBackend } from '../providers/sync';
import { useProviderStore } from '../store/providerStore';
import { useAppStore } from '../store/appStore';
import { fetchOceanProxyStatus } from '../lib/oceanProxyClient';
import type { ProviderConnection } from '../providers/types';
import './ConnectionCanvasPage.css';

export default function ConnectionCanvasPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const activeProviderIds = useProviderStore((s) => s.activeProviderIds);
  const toggleActiveProvider = useProviderStore((s) => s.toggleActiveProvider);
  const removeActiveProvider = useProviderStore((s) => s.removeActiveProvider);
  const proxyRunning = useProviderStore((s) => s.proxyRunning);
  const proxyLanUrl = useProviderStore((s) => s.proxyLanUrl);
  const setProxyStatus = useProviderStore((s) => s.setProxyStatus);

  const [connections, setConnections] = useState<ProviderConnection[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [disconnecting, setDisconnecting] = useState<string | null>(null);

  const refresh = useCallback(() => {
    void syncProviderConnectionsFromBackend().then((list) => {
      setConnections(list.filter((c) => c.status === 'connected'));
    });
  }, []);

  useEffect(() => {
    refresh();
    fetchOceanProxyStatus().then((s) => {
      if (s.running) setProxyStatus(true, s.url, s.lanUrl);
    });
  }, [refresh, setProxyStatus]);

  const connected = connections.filter((c) => c.status === 'connected');
  const selected = connected.find((c) => c.providerId === selectedId);
  const selectedDef = selected ? PROVIDER_CATALOG.find((p) => p.id === selected.providerId) : null;

  function nodePosition(index: number, total: number): { x: number; y: number } {
    if (total === 0) return { x: 0, y: 0 };
    const angle = (index / total) * Math.PI * 2 - Math.PI / 2;
    const radius = Math.min(180, 120 + total * 12);
    return { x: Math.cos(angle) * radius, y: Math.sin(angle) * radius };
  }

  async function handleDisconnect(id: string) {
    setDisconnecting(id);
    try {
      const api = getOceanAPI();
      await api.providers?.disconnect?.(id);
      removeProviderConnection(id);
      removeActiveProvider(id);
      if (selectedId === id) setSelectedId(null);
      refresh();
    } finally {
      setDisconnecting(null);
    }
  }

  return (
    <div className="connection-canvas-page">
      <header className="connection-canvas-header">
        <button className="connection-back" onClick={() => setCenterView('editor')}>← Back</button>
        <div>
          <h1>Connections</h1>
          <p>Provider network — Ocean at the center</p>
        </div>
        <div className="connection-header-actions">
          {proxyRunning && (
            <span className="connection-proxy-badge">
              <Zap size={12} /> Proxy {proxyLanUrl}
            </span>
          )}
          <button className="connection-refresh" onClick={refresh}>
            <RefreshCw size={14} />
          </button>
          <button className="connection-add" onClick={() => setCenterView('providers')}>
            + Add Provider
          </button>
        </div>
      </header>

      <div className="connection-canvas-body">
        <div className="connection-graph">
          {/* Animated connection lines */}
          <svg className="connection-lines" viewBox="-300 -300 600 600">
            {connected.map((conn, i) => {
              const pos = nodePosition(i, connected.length);
              return (
                <motion.line
                  key={conn.providerId}
                  x1={0} y1={0}
                  x2={pos.x} y2={pos.y}
                  stroke={activeProviderIds.includes(conn.providerId) ? '#0ea5e9' : '#d6d3d1'}
                  strokeWidth={activeProviderIds.includes(conn.providerId) ? 2.5 : 1.5}
                  strokeDasharray={activeProviderIds.includes(conn.providerId) ? undefined : '6 4'}
                  initial={{ pathLength: 0, opacity: 0 }}
                  animate={{ pathLength: 1, opacity: 1 }}
                  transition={{ duration: 0.8, delay: i * 0.1 }}
                />
              );
            })}
            {/* Pulse along active lines */}
            {connected.filter((c) => activeProviderIds.includes(c.providerId)).map((conn, i) => {
              const pos = nodePosition(connected.indexOf(conn), connected.length);
              return (
                <motion.circle
                  key={`pulse-${conn.providerId}`}
                  r={4}
                  fill="#0ea5e9"
                  initial={{ cx: 0, cy: 0 }}
                  animate={{ cx: [0, pos.x], cy: [0, pos.y] }}
                  transition={{ duration: 2, repeat: Infinity, ease: 'linear', delay: i * 0.4 }}
                />
              );
            })}
          </svg>

          {/* Ocean hub — center */}
          <motion.div
            className="connection-hub"
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 200 }}
          >
            <div className="connection-hub-inner">
              <Waves size={28} />
              <span>Ocean</span>
              <small>{connected.length} linked</small>
            </div>
            <motion.div
              className="connection-hub-ring"
              animate={{ rotate: 360 }}
              transition={{ duration: 20, repeat: Infinity, ease: 'linear' }}
            />
          </motion.div>

          {/* Provider nodes */}
          {connected.map((conn, i) => {
            const def = PROVIDER_CATALOG.find((p) => p.id === conn.providerId);
            const pos = nodePosition(i, connected.length);
            const isActive = activeProviderIds.includes(conn.providerId);
            const isSelected = selectedId === conn.providerId;

            return (
              <motion.button
                key={conn.providerId}
                className={`connection-node ${isActive ? 'active' : ''} ${isSelected ? 'selected' : ''}`}
                style={{
                  transform: `translate(calc(-50% + ${pos.x}px), calc(-50% + ${pos.y}px))`,
                  borderColor: def?.brandColor ?? '#78716C',
                }}
                initial={{ scale: 0, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', delay: 0.2 + i * 0.08 }}
                whileHover={{ scale: 1.08 }}
                onClick={() => setSelectedId(conn.providerId)}
              >
                <div
                  className="connection-node-logo"
                  style={{ background: def?.brandColor ?? '#78716C' }}
                >
                  {def?.logoLetter ?? conn.name[0]}
                </div>
                <span className="connection-node-name">{conn.name}</span>
                {isActive && <span className="connection-node-active-dot" />}
              </motion.button>
            );
          })}

          {connected.length === 0 && (
            <div className="connection-empty">
              <p>No providers connected yet</p>
              <button onClick={() => setCenterView('providers')}>Browse Providers</button>
            </div>
          )}
        </div>

        {/* Side panel — selected provider details */}
        <aside className="connection-detail">
          {selected && selectedDef ? (
            <>
              <div className="connection-detail-header">
                <div
                  className="connection-detail-logo"
                  style={{ background: selectedDef.brandColor }}
                >
                  {selectedDef.logoLetter}
                </div>
                <div>
                  <h2>{selected.name}</h2>
                  <span className="connection-status connected">
                    <Check size={12} /> Connected
                  </span>
                  {selected.accountLabel && (
                    <p className="connection-account">{selected.accountLabel}</p>
                  )}
                </div>
              </div>

              <div className="connection-detail-section">
                <h3>Models</h3>
                <div className="connection-tags">
                  {(selected.syncedModels ?? []).slice(0, 6).map((m) => (
                    <span key={m} className="connection-tag">{m}</span>
                  ))}
                  {(selected.syncedModels?.length ?? 0) > 6 && (
                    <span className="connection-tag">+{(selected.syncedModels?.length ?? 0) - 6}</span>
                  )}
                </div>
              </div>

              <div className="connection-detail-section">
                <h3>Skills & Tools</h3>
                <div className="connection-tags">
                  {(selected.syncedSkills ?? []).map((s) => (
                    <span key={s} className="connection-tag skill">{s}</span>
                  ))}
                  {(selected.syncedTools ?? []).map((t) => (
                    <span key={t.name} className="connection-tag tool">{t.name}</span>
                  ))}
                </div>
              </div>

              <div className="connection-detail-actions">
                <button
                  className={`connection-action ${activeProviderIds.includes(selected.providerId) ? 'active' : ''}`}
                  onClick={() => toggleActiveProvider(selected.providerId)}
                >
                  <Zap size={14} />
                  {activeProviderIds.includes(selected.providerId) ? 'Active in Chat' : 'Enable in Chat'}
                </button>
                <button
                  className="connection-action"
                  onClick={() => setCenterView('providers')}
                >
                  <Settings size={14} /> Configure
                </button>
                {selectedDef.docsUrl && (
                  <a href={selectedDef.docsUrl} target="_blank" rel="noreferrer" className="connection-action">
                    <ExternalLink size={14} /> Docs
                  </a>
                )}
                <button
                  className="connection-action danger"
                  disabled={disconnecting === selected.providerId}
                  onClick={() => handleDisconnect(selected.providerId)}
                >
                  <Unplug size={14} />
                  {disconnecting === selected.providerId ? 'Disconnecting...' : 'Disconnect'}
                </button>
              </div>
            </>
          ) : (
            <div className="connection-detail-empty">
              <Waves size={32} strokeWidth={1.5} />
              <p>Select a provider node to view details, configure, or disconnect.</p>
              <p className="connection-hint">
                Active providers pulse along their connection lines and are included in agent context.
              </p>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
