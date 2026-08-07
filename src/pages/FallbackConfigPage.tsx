import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Shield, Zap, ArrowLeft, Key, RefreshCw, ChevronUp, ChevronDown,
  ToggleLeft, ToggleRight, Plus, Trash2, AlertTriangle, CheckCircle2,
} from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { useFallbackStore } from '../store/fallbackStore';
import { HUGGINGFACE_FREE_MODELS, NVIDIA_NIM_MODELS } from '../fallback/catalog';
import type { FallbackTrigger, FallbackDistribution } from '../fallback/types';
import './FallbackConfigPage.css';

const TRIGGERS: { id: FallbackTrigger; label: string; desc: string }[] = [
  { id: '429', label: 'Rate limit (429)', desc: 'Too many requests' },
  { id: 'quota', label: 'Quota exceeded', desc: 'Billing / credit limits' },
  { id: '5xx', label: 'Server errors', desc: '502, 503, 529 upstream failures' },
  { id: 'timeout', label: 'Timeout', desc: 'Request exceeded agent timeout' },
];

const DISTRIBUTIONS: { id: FallbackDistribution; label: string; desc: string }[] = [
  { id: 'sequential', label: 'Sequential', desc: 'Try chain top-to-bottom' },
  { id: 'round_robin', label: 'Round robin', desc: 'Rotate start per subagent' },
  { id: 'least_used', label: 'Least used', desc: 'Pick model with lowest usage' },
];

export default function FallbackConfigPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const config = useFallbackStore((s) => s.config);
  const setEnabled = useFallbackStore((s) => s.setEnabled);
  const setApplyToSubagents = useFallbackStore((s) => s.setApplyToSubagents);
  const setTriggers = useFallbackStore((s) => s.setTriggers);
  const setDistribution = useFallbackStore((s) => s.setDistribution);
  const setHuggingFaceToken = useFallbackStore((s) => s.setHuggingFaceToken);
  const setNvidiaApiKey = useFallbackStore((s) => s.setNvidiaApiKey);
  const resetBuiltinChain = useFallbackStore((s) => s.resetBuiltinChain);
  const toggleModel = useFallbackStore((s) => s.toggleModel);
  const reorderModel = useFallbackStore((s) => s.reorderModel);
  const addCustomModel = useFallbackStore((s) => s.addCustomModel);
  const syncProviderAuth = useFallbackStore((s) => s.syncProviderAuth);

  const [hfToken, setHfToken] = useState(config.auth.huggingfaceToken ?? '');
  const [nvidiaKey, setNvidiaKey] = useState(config.auth.nvidiaApiKey ?? '');
  const [customProvider, setCustomProvider] = useState<'huggingface' | 'nvidia'>('huggingface');
  const [customModelId, setCustomModelId] = useState('');
  const [customName, setCustomName] = useState('');
  const [saved, setSaved] = useState(false);
  const [filter, setFilter] = useState<'all' | 'huggingface' | 'nvidia'>('all');

  const enabledCount = config.chain.filter((m) => m.enabled).length;
  const hfCount = HUGGINGFACE_FREE_MODELS.length;
  const nimCount = NVIDIA_NIM_MODELS.length;

  function flashSaved() {
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  }

  function handleSaveAuth() {
    if (hfToken.trim()) setHuggingFaceToken(hfToken.trim());
    if (nvidiaKey.trim()) setNvidiaApiKey(nvidiaKey.trim());
    syncProviderAuth();
    flashSaved();
  }

  function toggleTrigger(t: FallbackTrigger) {
    const next = config.triggers.includes(t)
      ? config.triggers.filter((x) => x !== t)
      : [...config.triggers, t];
    setTriggers(next);
    flashSaved();
  }

  function handleAddCustom() {
    if (!customModelId.trim() || !customName.trim()) return;
    addCustomModel({
      providerId: customProvider,
      modelId: customModelId.trim(),
      displayName: customName.trim(),
      enabled: true,
      tier: 'custom',
    });
    setCustomModelId('');
    setCustomName('');
    flashSaved();
  }

  const filteredChain = config.chain.filter((m) => {
    if (filter === 'all') return true;
    return m.providerId === filter;
  });

  return (
    <div className="fb-page">
      <header className="fb-header">
        <div className="fb-header-left">
          <button type="button" className="fb-back" onClick={() => setCenterView('providers')}>
            <ArrowLeft size={14} /> Providers
          </button>
          <h1><Shield size={20} /> Fallback &amp; Backup Models</h1>
          {saved && <span className="fb-saved">Saved</span>}
        </div>
      </header>

      {/* Hero toggle */}
      <section className={`fb-hero ${config.enabled ? 'fb-hero-on' : ''}`}>
        <div className="fb-hero-content">
          <div className="fb-hero-icon"><Zap size={28} /></div>
          <div>
            <h2>Smart Fallback Routing</h2>
            <p>
              When a provider hits rate limits or quota, automatically route to Hugging Face free-tier
              and NVIDIA NIM backup models. Only active when enabled.
            </p>
          </div>
        </div>
        <button
          type="button"
          className={`fb-master-toggle ${config.enabled ? 'on' : ''}`}
          onClick={() => { setEnabled(!config.enabled); flashSaved(); }}
          aria-pressed={config.enabled}
        >
          {config.enabled ? <ToggleRight size={36} /> : <ToggleLeft size={36} />}
          <span>{config.enabled ? 'Fallback ON' : 'Fallback OFF'}</span>
        </button>
      </section>

      {!config.enabled && (
        <div className="fb-off-banner">
          <AlertTriangle size={16} />
          Fallback is disabled — primary providers only. Enable above to activate backup routing.
        </div>
      )}

      <div className="fb-grid">
        {/* Auth */}
        <section className="fb-card">
          <h3><Key size={16} /> Provider Authentication</h3>
          <p className="fb-card-desc">Connect Hugging Face and NVIDIA NIM for backup inference.</p>

          <div className="fb-auth-block">
            <label>
              <span className="fb-auth-label">
                <span className="fb-badge hf">HF</span> Hugging Face Access Token
              </span>
              <input
                type="password"
                placeholder="hf_…"
                value={hfToken}
                onChange={(e) => setHfToken(e.target.value)}
                autoComplete="off"
              />
              <small>Free tier inference — <a href="https://huggingface.co/settings/tokens" target="_blank" rel="noreferrer">Get token</a></small>
            </label>

            <label>
              <span className="fb-auth-label">
                <span className="fb-badge nim">NIM</span> NVIDIA API Key
              </span>
              <input
                type="password"
                placeholder="nvapi-…"
                value={nvidiaKey}
                onChange={(e) => setNvidiaKey(e.target.value)}
                autoComplete="off"
              />
              <small>NIM microservices — <a href="https://build.nvidia.com/" target="_blank" rel="noreferrer">Get API key</a></small>
            </label>

            <button type="button" className="fb-btn primary" onClick={handleSaveAuth}>
              Save &amp; Connect
            </button>
          </div>

          <div className="fb-auth-status">
            <span className={config.auth.huggingfaceToken ? 'ok' : 'pending'}>
              {config.auth.huggingfaceToken ? <CheckCircle2 size={14} /> : null}
              Hugging Face {config.auth.huggingfaceToken ? 'connected' : 'not configured'}
            </span>
            <span className={config.auth.nvidiaApiKey ? 'ok' : 'pending'}>
              {config.auth.nvidiaApiKey ? <CheckCircle2 size={14} /> : null}
              NVIDIA NIM {config.auth.nvidiaApiKey ? 'connected' : 'not configured'}
            </span>
          </div>
        </section>

        {/* Triggers & distribution */}
        <section className="fb-card">
          <h3>Trigger Conditions</h3>
          <p className="fb-card-desc">When should fallback activate?</p>
          <div className="fb-chips">
            {TRIGGERS.map((t) => (
              <button
                key={t.id}
                type="button"
                className={`fb-chip ${config.triggers.includes(t.id) ? 'active' : ''}`}
                onClick={() => toggleTrigger(t.id)}
              >
                <strong>{t.label}</strong>
                <small>{t.desc}</small>
              </button>
            ))}
          </div>

          <h3 className="fb-subhead">Distribution</h3>
          <div className="fb-radio-group">
            {DISTRIBUTIONS.map((d) => (
              <label key={d.id} className={`fb-radio ${config.distribution === d.id ? 'active' : ''}`}>
                <input
                  type="radio"
                  name="distribution"
                  checked={config.distribution === d.id}
                  onChange={() => { setDistribution(d.id); flashSaved(); }}
                />
                <span>
                  <strong>{d.label}</strong>
                  <small>{d.desc}</small>
                </span>
              </label>
            ))}
          </div>

          <label className="fb-check">
            <input
              type="checkbox"
              checked={config.applyToSubagents}
              onChange={(e) => { setApplyToSubagents(e.target.checked); flashSaved(); }}
            />
            Apply fallback to multi-agent subagents
          </label>
        </section>
      </div>

      {/* Model chain */}
      <section className="fb-chain-section">
        <div className="fb-chain-header">
          <div>
            <h3>Fallback Model Chain</h3>
            <p>{enabledCount} enabled · {hfCount} HF free tier · {nimCount} NVIDIA NIM backup</p>
          </div>
          <div className="fb-chain-actions">
            <div className="fb-filter-tabs">
              {(['all', 'huggingface', 'nvidia'] as const).map((f) => (
                <button
                  key={f}
                  type="button"
                  className={filter === f ? 'active' : ''}
                  onClick={() => setFilter(f)}
                >
                  {f === 'all' ? 'All' : f === 'huggingface' ? 'Hugging Face' : 'NVIDIA NIM'}
                </button>
              ))}
            </div>
            <button type="button" className="fb-btn ghost" onClick={() => { resetBuiltinChain(); flashSaved(); }}>
              <RefreshCw size={14} /> Reset defaults
            </button>
          </div>
        </div>

        <div className="fb-chain-list">
          <AnimatePresence>
            {filteredChain.map((m, idx) => (
              <motion.div
                key={m.id}
                className={`fb-chain-item ${m.enabled ? '' : 'disabled'}`}
                layout
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
              >
                <div className="fb-chain-order">{idx + 1}</div>
                <div className={`fb-chain-badge ${m.providerId}`}>
                  {m.providerId === 'huggingface' ? 'HF' : 'NIM'}
                </div>
                <div className="fb-chain-info">
                  <strong>{m.displayName}</strong>
                  <code>{m.modelId}</code>
                  <span className={`fb-tier ${m.tier}`}>{m.tier}</span>
                  {config.usageCounts[m.modelId] ? (
                    <span className="fb-usage">{config.usageCounts[m.modelId]} uses</span>
                  ) : null}
                </div>
                <div className="fb-chain-controls">
                  <button type="button" title="Move up" onClick={() => reorderModel(m.id, 'up')}>
                    <ChevronUp size={14} />
                  </button>
                  <button type="button" title="Move down" onClick={() => reorderModel(m.id, 'down')}>
                    <ChevronDown size={14} />
                  </button>
                  <button
                    type="button"
                    className={`fb-toggle-sm ${m.enabled ? 'on' : ''}`}
                    onClick={() => { toggleModel(m.id, !m.enabled); flashSaved(); }}
                  >
                    {m.enabled ? 'On' : 'Off'}
                  </button>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>

        {/* Add custom */}
        <div className="fb-add-custom">
          <h4><Plus size={14} /> Add custom fallback model</h4>
          <div className="fb-add-row">
            <select value={customProvider} onChange={(e) => setCustomProvider(e.target.value as 'huggingface' | 'nvidia')}>
              <option value="huggingface">Hugging Face</option>
              <option value="nvidia">NVIDIA NIM</option>
            </select>
            <input placeholder="Model ID" value={customModelId} onChange={(e) => setCustomModelId(e.target.value)} />
            <input placeholder="Display name" value={customName} onChange={(e) => setCustomName(e.target.value)} />
            <button type="button" className="fb-btn primary" onClick={handleAddCustom}>
              <Plus size={14} /> Add
            </button>
          </div>
        </div>

        {config.lastFallbackModelId && (
          <div className="fb-last-fallback">
            Last fallback: <code>{config.lastFallbackModelId}</code>
            {config.lastFallbackAt && (
              <span> at {new Date(config.lastFallbackAt).toLocaleString()}</span>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
