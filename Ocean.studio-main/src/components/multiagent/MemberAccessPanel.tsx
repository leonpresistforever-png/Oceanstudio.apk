import { useState } from 'react';
import { Terminal, Plug, Puzzle, Settings2, Plus, Trash2, FileText, Code } from 'lucide-react';
import type { AgentMember, FunctionCallDef, SkillFileRef } from '../../agents/types';
import { listAssignableMcp, listAssignablePlugins } from '../../agents/memberContext';
import CompatibilityModal from './CompatibilityModal';

interface MemberAccessPanelProps {
  member: AgentMember;
  onUpdate: (patch: Partial<AgentMember>) => void;
}

export default function MemberAccessPanel({ member, onUpdate }: MemberAccessPanelProps) {
  const [showCompat, setShowCompat] = useState(false);
  const mcpList = listAssignableMcp();
  const pluginList = listAssignablePlugins();

  function toggleMcp(id: string) {
    const ids = member.assignedMcpIds.includes(id)
      ? member.assignedMcpIds.filter((x) => x !== id)
      : [...member.assignedMcpIds, id];
    onUpdate({ assignedMcpIds: ids, access: { ...member.access, mcpMode: 'assigned' } });
  }

  function togglePlugin(id: string) {
    const ids = member.assignedPluginIds.includes(id)
      ? member.assignedPluginIds.filter((x) => x !== id)
      : [...member.assignedPluginIds, id];
    onUpdate({ assignedPluginIds: ids, access: { ...member.access, pluginMode: 'assigned' } });
  }

  function addFunctionCall() {
    const fn: FunctionCallDef = {
      id: crypto.randomUUID(),
      name: 'new_function',
      description: '',
      parameters: { type: 'object', properties: {} },
      enabled: true,
    };
    onUpdate({ functionCalls: [...member.functionCalls, fn] });
  }

  function updateFunction(id: string, patch: Partial<FunctionCallDef>) {
    onUpdate({
      functionCalls: member.functionCalls.map((f) => (f.id === id ? { ...f, ...patch } : f)),
    });
  }

  function removeFunction(id: string) {
    onUpdate({ functionCalls: member.functionCalls.filter((f) => f.id !== id) });
  }

  function addSkillFile() {
    const file: SkillFileRef = {
      id: crypto.randomUUID(),
      name: 'skills.md',
      content: '# Agent Skills\n\nDescribe capabilities here.\n',
      format: 'markdown',
    };
    onUpdate({ skillFiles: [...member.skillFiles, file] });
  }

  function updateSkillFile(id: string, patch: Partial<SkillFileRef>) {
    onUpdate({
      skillFiles: member.skillFiles.map((f) => (f.id === id ? { ...f, ...patch } : f)),
    });
  }

  function removeSkillFile(id: string) {
    onUpdate({ skillFiles: member.skillFiles.filter((f) => f.id !== id) });
  }

  return (
    <>
      <section className="ma-mini-section">
        <h4><Settings2 size={12} /> Access (all enabled by default)</h4>
        <div className="ma-access-toggles">
          <label className={`ma-access-toggle ${member.access.terminal ? 'on' : ''}`}>
            <input type="checkbox" checked={member.access.terminal}
              onChange={(e) => onUpdate({ access: { ...member.access, terminal: e.target.checked } })} />
            <Terminal size={14} /> Terminal
          </label>
          <label className={`ma-access-toggle ${member.access.mcp ? 'on' : ''}`}>
            <input type="checkbox" checked={member.access.mcp}
              onChange={(e) => onUpdate({ access: { ...member.access, mcp: e.target.checked } })} />
            <Plug size={14} /> MCP
          </label>
          <label className={`ma-access-toggle ${member.access.plugins ? 'on' : ''}`}>
            <input type="checkbox" checked={member.access.plugins}
              onChange={(e) => onUpdate({ access: { ...member.access, plugins: e.target.checked } })} />
            <Puzzle size={14} /> Plugins
          </label>
        </div>
        <button className="ma-compat-btn" onClick={() => setShowCompat(true)}>
          <Settings2 size={12} /> Compatibility: {member.compatibility}
        </button>
      </section>

      {member.access.mcp && (
        <section className="ma-mini-section">
          <h4><Plug size={12} /> MCP Connections</h4>
          <label className="mcp-config-field">
            <span>Mode</span>
            <select value={member.access.mcpMode}
              onChange={(e) => onUpdate({ access: { ...member.access, mcpMode: e.target.value as 'all' | 'assigned' } })}>
              <option value="all">All connected</option>
              <option value="assigned">Assigned only</option>
            </select>
          </label>
          {member.access.mcpMode === 'assigned' && (
            <div className="ma-assign-list">
              {mcpList.length === 0 ? (
                <span className="ma-tag muted">No MCP connectors — add in MCP Connectors page</span>
              ) : mcpList.map((m) => (
                <label key={m.id} className={`ma-assign-item ${member.assignedMcpIds.includes(m.id) ? 'selected' : ''}`}>
                  <input type="checkbox" checked={member.assignedMcpIds.includes(m.id)} onChange={() => toggleMcp(m.id)} />
                  <span>{m.name}</span>
                  <small>{m.status}</small>
                </label>
              ))}
            </div>
          )}
        </section>
      )}

      {member.access.plugins && (
        <section className="ma-mini-section">
          <h4><Puzzle size={12} /> Plugin Assignment</h4>
          <label className="mcp-config-field">
            <span>Mode</span>
            <select value={member.access.pluginMode}
              onChange={(e) => onUpdate({ access: { ...member.access, pluginMode: e.target.value as 'all' | 'assigned' } })}>
              <option value="all">All enabled plugins</option>
              <option value="assigned">Assigned only (manual bypass)</option>
            </select>
          </label>
          {member.access.pluginMode === 'assigned' && (
            <div className="ma-assign-list">
              {pluginList.length === 0 ? (
                <span className="ma-tag muted">No plugins installed</span>
              ) : pluginList.map((p) => (
                <label key={p.id} className={`ma-assign-item ${member.assignedPluginIds.includes(p.id) ? 'selected' : ''}`}>
                  <input type="checkbox" checked={member.assignedPluginIds.includes(p.id)} onChange={() => togglePlugin(p.id)} />
                  <span>{p.name}</span>
                  <small>{p.enabled ? 'enabled' : 'disabled'}</small>
                </label>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="ma-mini-section">
        <h4><Code size={12} /> Skills JSON</h4>
        <textarea
          className="ma-textarea code"
          rows={4}
          placeholder='{"capabilities": ["search", "code"]}'
          value={member.skillsJson ? JSON.stringify(member.skillsJson, null, 2) : ''}
          onChange={(e) => {
            try {
              const parsed = e.target.value ? JSON.parse(e.target.value) : undefined;
              onUpdate({ skillsJson: parsed });
            } catch { /* allow partial JSON while typing */ }
          }}
        />
      </section>

      <section className="ma-mini-section">
        <div className="ma-section-header inline">
          <h4><FileText size={12} /> Skill Files</h4>
          <button className="ma-icon-btn" onClick={addSkillFile}><Plus size={12} /></button>
        </div>
        {member.skillFiles.map((f) => (
          <div key={f.id} className="ma-skill-file">
            <input className="ma-combo-name sm" value={f.name}
              onChange={(e) => updateSkillFile(f.id, { name: e.target.value })} />
            <select value={f.format} onChange={(e) => updateSkillFile(f.id, { format: e.target.value as SkillFileRef['format'] })}>
              <option value="markdown">Markdown</option>
              <option value="json">JSON</option>
              <option value="text">Text</option>
            </select>
            <textarea className="ma-textarea" rows={3} value={f.content}
              onChange={(e) => updateSkillFile(f.id, { content: e.target.value })} />
            <button className="ma-remove-btn sm" onClick={() => removeSkillFile(f.id)}><Trash2 size={12} /></button>
          </div>
        ))}
      </section>

      <section className="ma-mini-section">
        <div className="ma-section-header inline">
          <h4><Code size={12} /> Function Calling</h4>
          <button className="ma-icon-btn" onClick={addFunctionCall}><Plus size={12} /></button>
        </div>
        {member.functionCalls.map((fn) => (
          <div key={fn.id} className="ma-fn-card">
            <input className="ma-combo-name sm" placeholder="function_name" value={fn.name}
              onChange={(e) => updateFunction(fn.id, { name: e.target.value })} />
            <input className="ma-combo-desc" placeholder="Description" value={fn.description}
              onChange={(e) => updateFunction(fn.id, { description: e.target.value })} />
            <input className="ma-combo-desc" placeholder="Endpoint (optional)" value={fn.endpoint ?? ''}
              onChange={(e) => updateFunction(fn.id, { endpoint: e.target.value })} />
            <textarea className="ma-textarea code" rows={3} placeholder="JSON Schema parameters"
              value={JSON.stringify(fn.parameters, null, 2)}
              onChange={(e) => {
                try { updateFunction(fn.id, { parameters: JSON.parse(e.target.value) }); } catch { /* partial */ }
              }} />
            <div className="ma-fn-actions">
              <label className="mcp-config-field checkbox">
                <input type="checkbox" checked={fn.enabled} onChange={(e) => updateFunction(fn.id, { enabled: e.target.checked })} />
                <span>Enabled</span>
              </label>
              <button className="ma-remove-btn sm" onClick={() => removeFunction(fn.id)}><Trash2 size={12} /></button>
            </div>
          </div>
        ))}
      </section>

      {showCompat && (
        <CompatibilityModal member={member} onUpdate={onUpdate} onClose={() => setShowCompat(false)} />
      )}
    </>
  );
}
