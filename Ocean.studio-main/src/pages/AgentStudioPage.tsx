import { useState } from 'react';
import { Settings2, FileText, Workflow, Layers, Monitor } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { usePromptEnhancerStore } from '../store/promptEnhancerStore';
import { CURSOR_WORKFLOWS } from '../tools/cursorWorkflows';
import type { PreviewViewportMode } from '../lib/previewViewport';
import './AgentStudioPage.css';

type StudioTab = 'workflows' | 'files' | 'behavior' | 'voice';

export default function AgentStudioPage() {
  const [tab, setTab] = useState<StudioTab>('workflows');
  const setCenterView = useAppStore((s) => s.setCenterView);
  const previewViewportMode = useAppStore((s) => s.previewViewportMode);
  const setPreviewViewportMode = useAppStore((s) => s.setPreviewViewportMode);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);
  const config = usePromptEnhancerStore((s) => s.config);
  const setConfig = usePromptEnhancerStore((s) => s.setConfig);

  const injectedFiles = [
    'agent/skill.md',
    'agent/tools-skill.md',
    'agent/design-skill.md',
    'agent/cursor-workflows-skill.md',
    'agent/multi-agent-skill.md',
    'agent/routing-skill.md',
    'agent/templates/default-ui.tsx',
    'agent/model-config.json',
    'agent/mcp-config.json',
  ];

  return (
    <div className="as-page">
      <header className="as-header">
        <h1><Settings2 size={20} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 8 }} />Agent Studio</h1>
        <p style={{ margin: 0, fontSize: '0.85rem', color: '#71717a' }}>Configure Cursor-style workflows, injected files, agent behavior, and voice assistant</p>
        <div className="as-tabs">
          {([
            ['workflows', 'Workflows', Workflow],
            ['files', 'Injected Files', FileText],
            ['behavior', 'Behavior', Layers],
            ['voice', 'Voice & Screen', Monitor],
          ] as const).map(([id, label, Icon]) => (
            <button
              key={id}
              type="button"
              className={`as-tab ${tab === id ? 'as-tab--active' : ''}`}
              onClick={() => setTab(id)}
            >
              <Icon size={14} style={{ display: 'inline', marginRight: 6, verticalAlign: 'middle' }} />
              {label}
            </button>
          ))}
        </div>
      </header>

      <div className="as-body">
        {tab === 'workflows' && (
          <section className="as-section">
            <h2>Cursor-style workflows</h2>
            {CURSOR_WORKFLOWS.map((w) => (
              <div key={w.id} className="as-card">
                <h3>{w.icon} {w.name}</h3>
                <p>{w.description} — {w.agentMode} mode</p>
                <span className="as-link" onClick={() => setCenterView('workflows')}>Configure in Agent Workflows →</span>
              </div>
            ))}
          </section>
        )}

        {tab === 'files' && (
          <section className="as-section">
            <h2>Injected agent files</h2>
            <p style={{ fontSize: '0.8rem', color: '#71717a', marginBottom: 16 }}>
              These files are fed to every agent session via providerContext. Designer sub-agents get additional design-skill + default-ui scaffold.
            </p>
            {injectedFiles.map((f) => (
              <div key={f} className="as-card">
                <h3>{f}</h3>
                <p>Auto-injected into buildAgentContext() → providerContext.serialized</p>
              </div>
            ))}
            <span className="as-link" onClick={() => setCenterView('skills')}>Manage Skills Store →</span>
          </section>
        )}

        {tab === 'behavior' && (
          <section className="as-section">
            <h2>Agent behavior</h2>
            <div className="as-card">
              <div className="as-config-row">
                <label>Agent mode</label>
                <span>{workspaceConfig?.agentMode ?? 'review'}</span>
              </div>
              <div className="as-config-row">
                <label>Default preview viewport</label>
                <select
                  value={previewViewportMode}
                  onChange={(e) => setPreviewViewportMode(e.target.value as PreviewViewportMode)}
                >
                  <option value="website">Website</option>
                  <option value="mobile">Mobile</option>
                  <option value="desktop">Desktop virtual screen</option>
                </select>
              </div>
              <div className="as-config-row">
                <label>Auto-enhance prompts</label>
                <input type="checkbox" checked={config.autoEnhance} onChange={(e) => setConfig({ autoEnhance: e.target.checked })} />
              </div>
              <div className="as-config-row">
                <label>Auto-compress prompts</label>
                <input type="checkbox" checked={config.autoCompress} onChange={(e) => setConfig({ autoCompress: e.target.checked })} />
              </div>
            </div>
            <span className="as-link" onClick={() => setCenterView('multiagent')}>Multi-agent fusion →</span>
            <span className="as-link" style={{ marginLeft: 16 }} onClick={() => setCenterView('fusion')}>Agent Fusion presets →</span>
          </section>
        )}

        {tab === 'voice' && (
          <section className="as-section">
            <h2>Voice & screen assistant</h2>
            <div className="as-card">
              <div className="as-config-row">
                <label>Wake phrase</label>
                <input
                  type="text"
                  value={config.wakePhrase ?? 'ocean'}
                  onChange={(e) => setConfig({ wakePhrase: e.target.value })}
                  placeholder="ocean"
                />
              </div>
              <div className="as-config-row">
                <label>STT language</label>
                <input
                  type="text"
                  value={config.sttLanguage}
                  onChange={(e) => setConfig({ sttLanguage: e.target.value })}
                />
              </div>
              <div className="as-config-row">
                <label>Continuous listening</label>
                <input type="checkbox" checked={config.sttContinuous} onChange={(e) => setConfig({ sttContinuous: e.target.checked })} />
              </div>
              <div className="as-config-row">
                <label>Narrator interval (sec)</label>
                <input
                  type="number"
                  min={5}
                  max={120}
                  value={config.narratorIntervalSec ?? 30}
                  onChange={(e) => setConfig({ narratorIntervalSec: Number(e.target.value) })}
                />
              </div>
              <div className="as-config-row">
                <label>Narrator tone</label>
                <select
                  value={config.narratorTone ?? 'helper'}
                  onChange={(e) => setConfig({ narratorTone: e.target.value as 'helper' | 'storyteller' | 'technical' | 'casual' })}
                >
                  <option value="helper">Helpful assistant</option>
                  <option value="storyteller">Storyteller</option>
                  <option value="technical">Technical narrator</option>
                  <option value="casual">Casual friend</option>
                </select>
              </div>
            </div>
            <span className="as-link" onClick={() => setCenterView('playground')}>Open Screen Narrator in Playground →</span>
          </section>
        )}
      </div>
    </div>
  );
}
