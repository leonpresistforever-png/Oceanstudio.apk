import { useState, useCallback } from 'react';
import { Workflow, Sparkles, Check } from 'lucide-react';
import {
  CURSOR_WORKFLOWS,
  applyWorkflow,
  getDefaultParamValues,
  type AgentWorkflow,
  type WorkflowParam,
} from '../tools/cursorWorkflows';
import { useAppStore } from '../store/appStore';
import './AgentWorkflowsPage.css';

function WorkflowCard({
  workflow,
  onToast,
}: {
  workflow: AgentWorkflow;
  onToast: (msg: string) => void;
}) {
  const [params, setParams] = useState(() => getDefaultParamValues(workflow));
  const [applying, setApplying] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const setCenterView = useAppStore((s) => s.setCenterView);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  function setParam(key: string, value: unknown) {
    setParams((p) => ({ ...p, [key]: value }));
  }

  function renderParam(p: WorkflowParam) {
    const val = params[p.key] ?? p.default;

    if (p.type === 'boolean') {
      return (
        <div key={p.key} className="aw-param">
          <label className="aw-param-check">
            <input
              type="checkbox"
              checked={Boolean(val)}
              onChange={(e) => setParam(p.key, e.target.checked)}
            />
            {p.label}
          </label>
        </div>
      );
    }

    if (p.type === 'select' && p.options) {
      return (
        <div key={p.key} className="aw-param">
          <label>{p.label}</label>
          <select
            value={String(val)}
            onChange={(e) => setParam(p.key, e.target.value)}
          >
            {p.options.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </div>
      );
    }

    return (
      <div key={p.key} className="aw-param">
        <label>{p.label}</label>
        <input
          type={p.type === 'number' ? 'number' : 'text'}
          value={String(val)}
          onChange={(e) => setParam(
            p.key,
            p.type === 'number' ? Number(e.target.value) : e.target.value
          )}
        />
      </div>
    );
  }

  async function handleApply() {
    setApplying(true);
    const result = applyWorkflow(workflow.id, params);
    setApplying(false);

    if (result.ok) {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'status',
        timestamp: Date.now(),
        summary: `Workflow applied: ${workflow.name}`,
        detail: result.prompt,
        status: 'completed',
        metadata: { workflowId: workflow.id, skills: result.skillsInstalled },
      });

      if (workflow.category === 'design') {
        setCenterView('preview');
      }

      onToast(result.message);
    } else {
      onToast(result.message);
    }
  }

  return (
    <article className="aw-card">
      <span className="aw-card-icon">{workflow.icon}</span>
      <h3>{workflow.name}</h3>
      <p className="aw-card-desc">{workflow.description}</p>
      <div className="aw-card-meta">
        <span className="aw-badge">{workflow.category}</span>
        <span className="aw-badge aw-badge--mode">{workflow.agentMode} mode</span>
      </div>
      <p className="aw-skills-list">
        Skills: {workflow.skillIds.map((id) => id.replace('builtin.', '')).join(', ')}
      </p>

      <button
        type="button"
        className="aw-apply-btn"
        style={{ background: expanded ? 'rgba(51,65,85,0.8)' : undefined }}
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? 'Hide params' : 'Configure & apply'}
      </button>

      {expanded && (
        <>
          <div className="aw-params">
            {workflow.params.map(renderParam)}
          </div>
          <button
            type="button"
            className="aw-apply-btn"
            onClick={handleApply}
            disabled={applying}
          >
            <Check size={14} style={{ display: 'inline', marginRight: 6, verticalAlign: 'middle' }} />
            {applying ? 'Applying…' : 'Apply workflow'}
          </button>
        </>
      )}
    </article>
  );
}

let toastTimer: ReturnType<typeof setTimeout> | null = null;

export default function AgentWorkflowsPage() {
  const [toast, setToast] = useState('');

  const showToast = useCallback((msg: string) => {
    setToast(msg);
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => setToast(''), 4000);
  }, []);

  return (
    <div className="aw-page">
      <header className="aw-header">
        <Workflow size={22} color="#38bdf8" />
        <div>
          <h1>
            <Sparkles size={18} /> Agent Workflows
          </h1>
          <p>Cursor-style workflows — install skills, set params, apply to agent</p>
        </div>
      </header>

      <div className="aw-grid">
        {CURSOR_WORKFLOWS.map((w) => (
          <WorkflowCard key={w.id} workflow={w} onToast={showToast} />
        ))}
      </div>

      {toast && <div className="aw-toast">{toast}</div>}
    </div>
  );
}
