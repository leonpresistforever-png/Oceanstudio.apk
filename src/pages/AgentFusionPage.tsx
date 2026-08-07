import { useState } from 'react';
import { Layers, Zap } from 'lucide-react';
import { FUSION_PRESETS, createFusionComboFromPreset } from '../agents/fusion';
import { useMultiAgentStore } from '../store/multiAgentStore';
import { useAppStore } from '../store/appStore';
import './AgentFusionPage.css';

export default function AgentFusionPage() {
  const [toast, setToast] = useState('');
  const importCombo = useMultiAgentStore((s) => s.importCombo);
  const setActiveCombo = useMultiAgentStore((s) => s.setActiveCombo);
  const setMultiAgentEnabled = useMultiAgentStore((s) => s.setMultiAgentEnabled);
  const setCenterView = useAppStore((s) => s.setCenterView);

  function applyPreset(presetId: string) {
    const combo = createFusionComboFromPreset(presetId);
    if (!combo) return;
    const id = importCombo(combo);
    setActiveCombo(id);
    setMultiAgentEnabled(true);
    setToast(`Fusion team "${combo.name}" activated — ${combo.members.length} providers merged`);
    setTimeout(() => setToast(''), 4000);
  }

  return (
    <div className="af-page">
      <header className="af-header">
        <h1><Layers size={22} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 8 }} />Agent Fusion</h1>
        <p>Merge 2–5 agents and providers (Codex, Antigravity IDE/CLI, Cursor) into one powerful composite agent</p>
      </header>

      <div className="af-grid">
        {FUSION_PRESETS.map((preset) => (
          <article key={preset.id} className="af-card">
            <div className="af-icon">{preset.icon}</div>
            <h3>{preset.name}</h3>
            <p>{preset.description}</p>
            <div className="af-meta">
              <span className="af-badge">{preset.fusionConfig.strategy}</span>
              <span className="af-badge">{preset.fusionConfig.providerRouting}</span>
              <span className="af-badge">{preset.members.length} agents</span>
            </div>
            <p className="af-members">
              {preset.members.map((m) => m.name).join(' + ')}
            </p>
            <button type="button" className="af-btn" onClick={() => applyPreset(preset.id)}>
              <Zap size={14} style={{ display: 'inline', marginRight: 6, verticalAlign: 'middle' }} />
              Activate fusion
            </button>
          </article>
        ))}
      </div>

      <div style={{ padding: '0 28px 32px' }}>
        <button
          type="button"
          className="af-btn"
          style={{ maxWidth: 320, background: '#3f3f46' }}
          onClick={() => setCenterView('multiagent')}
        >
          Custom fusion in Multi Agent →
        </button>
      </div>

      {toast && <div className="af-toast">{toast}</div>}
    </div>
  );
}
