import { Sparkles } from 'lucide-react';
import type { AgentCombo, AgentFusionConfig, FusionProviderRouting, FusionStrategy } from '../../agents/types';
import { FUSION_MAX_MEMBERS, FUSION_MIN_MEMBERS } from '../../agents/fusion';

interface FusionConfigPanelProps {
  combo: AgentCombo;
  onUpdate: (partial: Partial<AgentFusionConfig>) => void;
  onToggleMember: (memberId: string, included: boolean) => void;
  onOpenPresets: () => void;
}

const STRATEGIES: { id: FusionStrategy; label: string }[] = [
  { id: 'identity-merge', label: 'Identity merge (single fused call)' },
  { id: 'capability-union', label: 'Capability union (tools + skills)' },
  { id: 'weighted-vote', label: 'Weighted vote (parallel shards)' },
];

const ROUTING: { id: FusionProviderRouting; label: string }[] = [
  { id: 'parallel-merge', label: 'Parallel merge' },
  { id: 'gateway-combo', label: 'Gateway combo' },
  { id: 'best-of', label: 'Best-of routing' },
  { id: 'round-robin', label: 'Round robin' },
];

export default function FusionConfigPanel({
  combo,
  onUpdate,
  onToggleMember,
  onOpenPresets,
}: FusionConfigPanelProps) {
  const cfg = combo.fusionConfig;
  if (!cfg) return null;

  const included = new Set(cfg.memberIds);
  const count = combo.members.filter((m) => included.has(m.id) && m.enabled).length;

  return (
    <section className="ma-fusion-panel">
      <div className="ma-fusion-panel-header">
        <h3><Sparkles size={16} /> Agent Fusion</h3>
        <button type="button" className="plugins-back" onClick={onOpenPresets}>Browse presets →</button>
      </div>

      <p className="ma-fusion-hint">
        Merge {FUSION_MIN_MEMBERS}–{FUSION_MAX_MEMBERS} agents into one composite identity across Codex, Antigravity, Cursor, and more.
        {count < FUSION_MIN_MEMBERS && (
          <strong> Select at least {FUSION_MIN_MEMBERS} agents below.</strong>
        )}
      </p>

      <label className="mcp-config-field">
        <span>Fused agent name</span>
        <input value={cfg.fusedName} onChange={(e) => onUpdate({ fusedName: e.target.value })} />
      </label>

      <div className="ma-fusion-row">
        <label className="mcp-config-field">
          <span>Strategy</span>
          <select value={cfg.strategy} onChange={(e) => onUpdate({ strategy: e.target.value as FusionStrategy })}>
            {STRATEGIES.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </select>
        </label>
        <label className="mcp-config-field">
          <span>Provider routing</span>
          <select value={cfg.providerRouting} onChange={(e) => onUpdate({ providerRouting: e.target.value as FusionProviderRouting })}>
            {ROUTING.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
          </select>
        </label>
      </div>

      <label className="mcp-config-field">
        <span>Primary provider (identity merge)</span>
        <select
          value={cfg.primaryMemberId ?? ''}
          onChange={(e) => onUpdate({ primaryMemberId: e.target.value })}
        >
          {combo.members.filter((m) => included.has(m.id)).map((m) => (
            <option key={m.id} value={m.id}>{m.name} — {m.providerName}</option>
          ))}
        </select>
      </label>

      <label className="mcp-config-field">
        <span>Fused system prompt</span>
        <textarea
          className="ma-textarea"
          rows={4}
          value={cfg.fusedSystemPrompt}
          onChange={(e) => onUpdate({ fusedSystemPrompt: e.target.value })}
        />
      </label>

      <div className="ma-fusion-members">
        <span className="ma-fusion-members-label">Agents in fusion ({count}/{FUSION_MAX_MEMBERS})</span>
        {combo.members.map((m) => {
          const isIn = included.has(m.id);
          return (
            <div key={m.id} className={`ma-fusion-member ${isIn ? 'included' : ''}`}>
              <label>
                <input
                  type="checkbox"
                  checked={isIn}
                  disabled={!m.enabled || (!isIn && count >= FUSION_MAX_MEMBERS)}
                  onChange={(e) => onToggleMember(m.id, e.target.checked)}
                />
                <span className="ma-fusion-member-dot" style={{ background: m.brandColor }} />
                <span>{m.name}</span>
                <small>{m.providerName} / {m.modelLabel}</small>
              </label>
              {isIn && (
                <input
                  type="range"
                  min={0.1}
                  max={1}
                  step={0.1}
                  value={cfg.memberWeights[m.id] ?? 0.5}
                  onChange={(e) => onUpdate({
                    memberWeights: { ...cfg.memberWeights, [m.id]: Number(e.target.value) },
                  })}
                  title={`Weight: ${cfg.memberWeights[m.id] ?? 0.5}`}
                />
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}
