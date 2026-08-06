import { useState, useMemo } from 'react';
import { Plug, Search, Pin } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import {
  INTEGRATIONS_CATALOG,
  INTEGRATION_CATEGORIES,
  searchIntegrations,
  type IntegrationCategory,
  type IntegrationDefinition,
} from '../integrations/catalog';
import { useIntegrationsStore } from '../store/integrationsStore';
import './IntegrationsHubPage.css';

export default function IntegrationsHubPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<IntegrationCategory | 'all'>('all');
  const connected = useIntegrationsStore((s) => s.connected);
  const pinned = useIntegrationsStore((s) => s.pinned);
  const connect = useIntegrationsStore((s) => s.connect);
  const disconnect = useIntegrationsStore((s) => s.disconnect);
  const isConnected = useIntegrationsStore((s) => s.isConnected);
  const togglePin = useIntegrationsStore((s) => s.togglePin);

  const items = useMemo(() => {
    let list = search ? searchIntegrations(search) : INTEGRATIONS_CATALOG;
    if (category !== 'all') list = list.filter((i) => i.category === category);
    return list.sort((a, b) => {
      const ap = pinned.includes(a.id) ? 0 : 1;
      const bp = pinned.includes(b.id) ? 0 : 1;
      if (ap !== bp) return ap - bp;
      const ac = isConnected(a.id) ? 0 : 1;
      const bc = isConnected(b.id) ? 0 : 1;
      return ac - bc;
    });
  }, [search, category, pinned, isConnected]);

  function handleConnect(integration: IntegrationDefinition) {
    if (integration.providerId) setCenterView('providers');
    else if (integration.mcpId) setCenterView('mcp');
    else connect(integration.id, integration.name);
  }

  return (
    <div className="ih-page">
      <header className="ih-hero">
        <h1><Plug size={22} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 8 }} />Integrations Hub</h1>
        <p>Connect GitLab, Slack, Windsurf, Cursor, Antigravity, cloud providers, and 40+ services</p>
        <div className="ih-stats">
          <div className="ih-stat"><strong>{INTEGRATIONS_CATALOG.length}</strong><span>Available</span></div>
          <div className="ih-stat"><strong>{connected.length}</strong><span>Connected</span></div>
          <div className="ih-stat"><strong>{pinned.length}</strong><span>Pinned</span></div>
        </div>
      </header>

      <div className="ih-toolbar">
        <Search size={16} color="#71717a" />
        <input
          className="ih-search"
          placeholder="Search integrations…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <div className="ih-cats">
          <button
            type="button"
            className={`ih-cat-btn ${category === 'all' ? 'ih-cat-btn--active' : ''}`}
            onClick={() => setCategory('all')}
          >All</button>
          {INTEGRATION_CATEGORIES.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`ih-cat-btn ${category === c.id ? 'ih-cat-btn--active' : ''}`}
              onClick={() => setCategory(c.id)}
            >{c.icon} {c.label}</button>
          ))}
        </div>
      </div>

      <div className="ih-grid">
        {items.map((integration) => (
          <IntegrationCard
            key={integration.id}
            integration={integration}
            connected={isConnected(integration.id)}
            pinned={pinned.includes(integration.id)}
            onConnect={() => handleConnect(integration)}
            onDisconnect={() => disconnect(integration.id)}
            onPin={() => togglePin(integration.id)}
          />
        ))}
      </div>
    </div>
  );
}

function IntegrationCard({
  integration,
  connected,
  pinned,
  onConnect,
  onDisconnect,
  onPin,
}: {
  integration: IntegrationDefinition;
  connected: boolean;
  pinned: boolean;
  onConnect: () => void;
  onDisconnect: () => void;
  onPin: () => void;
}) {
  const status = connected ? 'connected' : integration.status;
  return (
    <article className="ih-card">
      <div className="ih-card-top">
        <div className="ih-card-icon" style={{ background: `${integration.brandColor}22` }}>
          {integration.icon}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <h3>{integration.name}</h3>
          <p>{integration.description}</p>
        </div>
        <span className={`ih-status ih-status--${status}`}>{status.replace('_', ' ')}</span>
      </div>
      <div className="ih-card-tags">
        {integration.features.slice(0, 3).map((f) => (
          <span key={f} className="ih-tag">{f}</span>
        ))}
      </div>
      <div className="ih-card-actions">
        <button type="button" className="ih-btn" onClick={onPin} title="Pin">
          <Pin size={12} style={{ opacity: pinned ? 1 : 0.4 }} />
        </button>
        {connected ? (
          <button type="button" className="ih-btn" onClick={onDisconnect}>Disconnect</button>
        ) : (
          <button type="button" className="ih-btn ih-btn--primary" onClick={onConnect}>Connect</button>
        )}
      </div>
    </article>
  );
}
