import { useState, useEffect, useCallback } from 'react';
import {
  Server, Zap, Key, GitBranch, Workflow, Copy, Check, RefreshCw,
  Activity, Shield, Cpu, ExternalLink,
} from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';

interface GatewayStatus {
  stats: { totalRequests: number; successRate: number; avgLatencyMs: number; totalCostUsd: number };
  config: { enableCursorBridge: boolean; enableCompression: boolean; enableAutoCombo: boolean; forceHttp11: boolean };
  combos: { id: string; name: string; strategy: string; fallbackSequence: string[]; enabled: boolean }[];
  workflows: { id: string; name: string; description: string; triggers: string[]; enabled: boolean; steps: { type: string }[] }[];
  virtualKeys: { id: string; name: string; keyPrefix: string; isActive: boolean }[];
}

export default function GatewayPanel({ proxyPort, proxyRunning }: { proxyPort: number; proxyRunning: boolean }) {
  const [status, setStatus] = useState<GatewayStatus | null>(null);
  const [ideConfigs, setIdeConfigs] = useState<Record<string, unknown> | null>(null);
  const [newKeyName, setNewKeyName] = useState('');
  const [createdKey, setCreatedKey] = useState<string | null>(null);
  const [copied, setCopied] = useState('');
  const [loading, setLoading] = useState(false);

  const api = getOceanAPI() as {
    gateway?: {
      getStatus: () => Promise<GatewayStatus>;
      getIdeConfigs: (port?: number) => Promise<Record<string, unknown>>;
      createVirtualKey: (name: string) => Promise<{ key: string; record: { id: string } }>;
      revokeVirtualKey: (id: string) => Promise<void>;
      saveConfig: (config: GatewayStatus['config']) => Promise<void>;
    };
  };

  const refresh = useCallback(async () => {
    if (!api.gateway) return;
    setLoading(true);
    try {
      const [s, ide] = await Promise.all([
        api.gateway.getStatus(),
        api.gateway.getIdeConfigs(proxyPort),
      ]);
      setStatus(s);
      setIdeConfigs(ide);
    } finally {
      setLoading(false);
    }
  }, [api.gateway, proxyPort]);

  useEffect(() => {
    void refresh();
  }, [refresh, proxyRunning]);

  async function createKey() {
    if (!newKeyName.trim() || !api.gateway) return;
    const { key } = await api.gateway.createVirtualKey(newKeyName.trim());
    setCreatedKey(key);
    setNewKeyName('');
    void refresh();
  }

  function copyText(text: string, label: string) {
    void navigator.clipboard.writeText(text);
    setCopied(label);
    setTimeout(() => setCopied(''), 2000);
  }

  const baseUrl = `http://127.0.0.1:${proxyPort}`;

  return (
    <div className="gw-panel">
      <div className="gw-hero">
        <h3><Zap size={18} /> Ocean Gateway Engine</h3>
        <p>
          Advanced multi-provider routing beyond OmniRoute — strategic workflows, Auto-Combo scoring,
          Cursor IDE bridge, context compression, circuit breakers, and virtual keys for IDEs.
        </p>
        {!proxyRunning && (
          <p className="gw-warn">Start the Ocean proxy above to activate the gateway at {baseUrl}/v1</p>
        )}
        {proxyRunning && (
          <div className="gw-endpoints">
            <code>{baseUrl}/v1/chat/completions</code>
            <code>{baseUrl}/cursor/v1/chat/completions</code>
            <code>{baseUrl}/gateway/status</code>
          </div>
        )}
      </div>

      <div className="gw-stats-row">
        <div className="gw-stat"><Activity size={14} /><strong>{status?.stats.totalRequests ?? 0}</strong><span>Requests</span></div>
        <div className="gw-stat"><Shield size={14} /><strong>{((status?.stats.successRate ?? 1) * 100).toFixed(0)}%</strong><span>Success</span></div>
        <div className="gw-stat"><Cpu size={14} /><strong>{Math.round(status?.stats.avgLatencyMs ?? 0)}ms</strong><span>Avg Latency</span></div>
        <button className="gw-refresh" onClick={() => void refresh()} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
        </button>
      </div>

      <section className="gw-section">
        <h4><GitBranch size={14} /> Model Combos &amp; Auto-Route</h4>
        <p className="gw-desc">12-factor scoring: health, quota, latency, cost, cache alignment. Fallback chains with circuit breaker.</p>
        <div className="gw-combo-list">
          {(status?.combos ?? []).map((c) => (
            <div key={c.id} className={`gw-combo-card ${c.enabled ? '' : 'disabled'}`}>
              <strong>{c.name}</strong>
              <span className="gw-badge">{c.strategy}</span>
              <small>{c.fallbackSequence.join(' → ')}</small>
            </div>
          ))}
        </div>
      </section>

      <section className="gw-section">
        <h4><Workflow size={14} /> Strategic Workflows</h4>
        <p className="gw-desc">Multi-step pipelines: transform → compress → route → fallback → webhook. Beyond simple proxy pass-through.</p>
        <div className="gw-workflow-list">
          {(status?.workflows ?? []).map((w) => (
            <div key={w.id} className={`gw-workflow-card ${w.enabled ? '' : 'disabled'}`}>
              <strong>{w.name}</strong>
              <small>{w.description}</small>
              <div className="gw-tags">
                {w.triggers.map((t) => <span key={t} className="gw-tag">{t}</span>)}
                {w.steps.map((s, i) => <span key={i} className="gw-tag step">{s.type}</span>)}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="gw-section">
        <h4><Key size={14} /> Virtual Keys (IDE Auth)</h4>
        <p className="gw-desc">Scoped keys for Cursor, Cline, Windsurf — budget caps and model allowlists.</p>
        <div className="gw-key-form">
          <input placeholder="Key name (e.g. Cursor IDE)" value={newKeyName} onChange={(e) => setNewKeyName(e.target.value)} />
          <button className="plugin-install-btn" onClick={() => void createKey()} disabled={!newKeyName.trim()}>Create Key</button>
        </div>
        {createdKey && (
          <div className="gw-key-created">
            <code>{createdKey}</code>
            <button onClick={() => copyText(createdKey, 'key')}>{copied === 'key' ? <Check size={14} /> : <Copy size={14} />}</button>
            <small>Copy now — shown once only</small>
          </div>
        )}
        {(status?.virtualKeys ?? []).map((k) => (
          <div key={k.id} className="gw-key-row">
            <span>{k.name}</span>
            <code>{k.keyPrefix}…</code>
          </div>
        ))}
      </section>

      <section className="gw-section">
        <h4><Server size={14} /> IDE Integration Export</h4>
        <p className="gw-desc">Copy configs for Cursor, Cline, Windsurf, Claude Code, Zed. Set cursor.general.disableHttp2: true for stable SSE.</p>
        {ideConfigs && (
          <div className="gw-ide-configs">
            {Object.entries(ideConfigs).map(([ide, cfg]) => (
              <div key={ide} className="gw-ide-card">
                <div className="gw-ide-header">
                  <strong>{ide}</strong>
                  <button onClick={() => copyText(JSON.stringify(cfg, null, 2), ide)}>
                    {copied === ide ? <Check size={12} /> : <Copy size={12} />}
                  </button>
                </div>
                <pre>{JSON.stringify(cfg, null, 2)}</pre>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="gw-section gw-features">
        <h4>Gateway Features (vs OmniRoute)</h4>
        <ul>
          <li><strong>Cursor /cursor bridge</strong> — normalizes input[], previous_response_id, Anthropic-in-OpenAI payloads</li>
          <li><strong>Caveman + RTK compression</strong> — 15–40% token savings before upstream call</li>
          <li><strong>Strategic workflows</strong> — agent, IDE, playground pipelines with step chains</li>
          <li><strong>Auto-Combo 12-factor</strong> — health, quota, latency, cost scoring with mutex-safe routing</li>
          <li><strong>Circuit breaker + cooldown retry</strong> — 429/502/503 automatic failover</li>
          <li><strong>Virtual keys + telemetry</strong> — IDE auth, usage tracking, decision audit trail</li>
          <li><strong>ocean:// PKCE deep links</strong> — OAuth callbacks via protocol handler (desktop)</li>
          <li><strong>Optional OmniRoute/LiteLLM</strong> — still available as external gateway layer</li>
        </ul>
        <a href="https://github.com/diegosouzapw/OmniRoute" target="_blank" rel="noreferrer" className="plugin-link">
          <ExternalLink size={12} /> OmniRoute reference architecture
        </a>
      </section>
    </div>
  );
}
