import { useState, useMemo, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Users, Plus, Trash2, Copy, GitBranch, Layers, Brain,
  ChevronRight, Cpu, Database, Cloud, Wrench, BookOpen, Crown,
  ToggleLeft, ToggleRight, LayoutGrid, Sparkles, Activity, Download, FolderOpen,
} from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { useMultiAgentStore } from '../store/multiAgentStore';
import { getProviderConnections } from '../providers/registry';
import { PROVIDER_CATALOG } from '../providers/catalog';
import { createAgentMember } from '../agents/types';
import { syncProviderConnectionsFromBackend } from '../providers/sync';
import { WORKFLOW_TEMPLATES } from '../agents/templates';
import { suggestForModel } from '../agents/providerProfiles';
import TeamCanvas from '../components/multiagent/TeamCanvas';
import FusionConfigPanel from '../components/multiagent/FusionConfigPanel';
import MissionControl from '../components/multiagent/MissionControl';
import CategoryPanel from '../components/multiagent/CategoryPanel';
import SubAgentCreator from '../components/multiagent/SubAgentCreator';
import MemberAccessPanel from '../components/multiagent/MemberAccessPanel';
import { ARCHETYPE_DEFINITIONS } from '../agents/categories';
import type { AgentCombo, AgentMember, MultiAgentMode } from '../agents/types';
import '../pages/PluginsPage.css';
import './MultiAgentPage.css';

type PageTab = 'categories' | 'combos' | 'canvas' | 'templates' | 'mission' | 'system' | 'memory';

export default function MultiAgentPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const [tab, setTab] = useState<PageTab>('categories');
  const [showAddMember, setShowAddMember] = useState(false);
  const [showSubAgentCreator, setShowSubAgentCreator] = useState(false);
  const [creatorCategoryId, setCreatorCategoryId] = useState<string | null>(null);

  const combos = useMultiAgentStore((s) => s.combos);
  const activeComboId = useMultiAgentStore((s) => s.activeComboId);
  const multiAgentEnabled = useMultiAgentStore((s) => s.multiAgentEnabled);
  const systemConfig = useMultiAgentStore((s) => s.systemConfig);
  const selectedMemberId = useMultiAgentStore((s) => s.selectedMemberId);
  const runHistory = useMultiAgentStore((s) => s.runHistory);
  const activeRunId = useMultiAgentStore((s) => s.activeRunId);
  const categories = useMultiAgentStore((s) => s.categories);
  const activeCategoryId = useMultiAgentStore((s) => s.activeCategoryId);

  const setActiveCombo = useMultiAgentStore((s) => s.setActiveCombo);
  const setActiveCategory = useMultiAgentStore((s) => s.setActiveCategory);
  const setMultiAgentEnabled = useMultiAgentStore((s) => s.setMultiAgentEnabled);
  const setSelectedMember = useMultiAgentStore((s) => s.setSelectedMember);
  const setSystemConfig = useMultiAgentStore((s) => s.setSystemConfig);
  const addCombo = useMultiAgentStore((s) => s.addCombo);
  const updateCombo = useMultiAgentStore((s) => s.updateCombo);
  const removeCombo = useMultiAgentStore((s) => s.removeCombo);
  const duplicateCombo = useMultiAgentStore((s) => s.duplicateCombo);
  const importCombo = useMultiAgentStore((s) => s.importCombo);
  const addMember = useMultiAgentStore((s) => s.addMember);
  const updateMember = useMultiAgentStore((s) => s.updateMember);
  const removeMember = useMultiAgentStore((s) => s.removeMember);
  const setComboMode = useMultiAgentStore((s) => s.setComboMode);
  const updateFusionConfig = useMultiAgentStore((s) => s.updateFusionConfig);
  const toggleFusionMember = useMultiAgentStore((s) => s.toggleFusionMember);
  const setHeadAgent = useMultiAgentStore((s) => s.setHeadAgent);
  const clearRunHistory = useMultiAgentStore((s) => s.clearRunHistory);
  const addCategory = useMultiAgentStore((s) => s.addCategory);
  const removeCategory = useMultiAgentStore((s) => s.removeCategory);

  const activeCombo = combos.find((c) => c.id === activeComboId) ?? combos[0];
  const selectedMember = activeCombo?.members.find((m) => m.id === selectedMemberId);

  const [providerRefresh, setProviderRefresh] = useState(0);

  useEffect(() => {
    void syncProviderConnectionsFromBackend().then(() => setProviderRefresh((n) => n + 1));
  }, []);

  useEffect(() => {
    if (!activeComboId && combos[0]) setActiveCombo(combos[0].id);
  }, [activeComboId, combos.length, setActiveCombo]);

  const connectedProviders = useMemo(() => {
    return getProviderConnections()
      .filter((c) => c.status === 'connected')
      .map((c) => {
        const def = PROVIDER_CATALOG.find((p) => p.id === c.providerId);
        const models = c.syncedModels?.length ? c.syncedModels : def?.defaultModels ?? [];
        return { ...c, def, models };
      });
  }, [providerRefresh, activeCombo?.members.length]);

  function ensureActiveCombo() {
    if (!activeComboId && combos[0]) setActiveCombo(combos[0].id);
  }

  function handleAddCombo() {
    const id = addCombo({ name: `Team ${combos.length + 1}`, mode: 'parallel' });
    setActiveCombo(id);
  }

  function handleAddMemberFromProvider(providerId: string, modelId: string) {
    if (!activeCombo) return;
    const def = PROVIDER_CATALOG.find((p) => p.id === providerId);
    const conn = connectedProviders.find((c) => c.providerId === providerId);
    const suggestions = suggestForModel(providerId, modelId);
    const member = createAgentMember({
      providerId,
      providerName: def?.name ?? providerId,
      modelId,
      modelLabel: modelId,
      brandColor: def?.brandColor ?? '#0ea5e9',
      role: suggestions.role,
      taskAssignment: suggestions.taskAssignment ?? '',
      tools: (conn?.syncedTools ?? []).map((t) => ({
        id: t.name, name: t.name, description: t.description, source: 'provider' as const,
      })),
      skills: (conn?.syncedSkills ?? []).map((s) => ({
        id: s, name: s, content: '', source: 'vault' as const,
      })),
    });
    if (suggestions.temperature !== undefined) member.params.temperature = suggestions.temperature;
    if (suggestions.thinkingLevel !== undefined) member.params.thinkingLevel = suggestions.thinkingLevel;
    addMember(activeCombo.id, member);
    setSelectedMember(member.id);
    setShowAddMember(false);
  }

  function handleInstallTemplate(templateId: string) {
    const tpl = WORKFLOW_TEMPLATES.find((t) => t.id === templateId);
    if (!tpl) return;
    const combo = tpl.build();
    const id = importCombo(combo);
    setActiveCombo(id);
    setTab('combos');
  }

  function handleAddCategory() {
    const id = addCategory({ name: `Category ${categories.length + 1}`, archetypes: ['custom'] });
    setActiveCategory(id);
  }

  function handleAddSubAgent(categoryId: string) {
    setCreatorCategoryId(categoryId);
    setShowSubAgentCreator(true);
  }

  function handleCreateSubAgent(member: AgentMember) {
    if (!activeCombo) return;
    addMember(activeCombo.id, member);
    setSelectedMember(member.id);
  }

  function handleExportCombo() {
    if (!activeCombo) return;
    const blob = new Blob([JSON.stringify(activeCombo, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${activeCombo.name.replace(/\s+/g, '-').toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="multi-agent-page">
      <header className="plugins-header">
        <div className="plugins-header-left">
          <button className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1><Users size={20} style={{ display: 'inline', marginRight: 8, verticalAlign: -3 }} />Multi Agent</h1>
          <button
            className={`ma-enable-btn ${multiAgentEnabled ? 'on' : ''}`}
            onClick={() => { setMultiAgentEnabled(!multiAgentEnabled); ensureActiveCombo(); }}
          >
            {multiAgentEnabled ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
            {multiAgentEnabled ? 'Team Active' : 'Enable Team'}
          </button>
        </div>
        <div className="ma-mode-pills">
          {activeCombo && tab === 'combos' && (
            <>
              <span className={`ma-mode-pill ${activeCombo.mode === 'parallel' ? 'active' : ''}`}
                onClick={() => setComboMode(activeCombo.id, 'parallel')}>
                <GitBranch size={12} /> Parallel
              </span>
              <span className={`ma-mode-pill ${activeCombo.mode === 'collaborative' ? 'active' : ''}`}
                onClick={() => setComboMode(activeCombo.id, 'collaborative')}>
                <Users size={12} /> Collaborative
              </span>
              <span className={`ma-mode-pill ${activeCombo.mode === 'fusion' ? 'active' : ''}`}
                onClick={() => setComboMode(activeCombo.id, 'fusion')}>
                <Sparkles size={12} /> Fusion
              </span>
            </>
          )}
        </div>
      </header>

      <div className="plugins-tabs">
        {([
          ['categories', 'Categories', FolderOpen],
          ['combos', 'Combos', Layers],
          ['canvas', 'Team Canvas', LayoutGrid],
          ['templates', 'Templates', Sparkles],
          ['mission', 'Mission Control', Activity],
          ['system', 'System', Cloud],
          ['memory', 'Memory', Database],
        ] as [PageTab, string, typeof Layers][]).map(([t, label, Icon]) => (
          <button key={t} className={`plugins-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
            <Icon size={14} style={{ marginRight: 6, verticalAlign: -2 }} />{label}
          </button>
        ))}
      </div>

      <div className="ma-body">
        {tab === 'categories' && activeCombo && (
          <div className="ma-categories-layout">
            <CategoryPanel
              categories={categories}
              activeCategoryId={activeCategoryId}
              members={activeCombo.members}
              onSelectCategory={setActiveCategory}
              onAddCategory={handleAddCategory}
              onRemoveCategory={removeCategory}
              onAddSubAgent={handleAddSubAgent}
              onSelectMember={setSelectedMember}
              selectedMemberId={selectedMemberId}
            />
            <main className="ma-category-main">
              {selectedMember ? (
                <MemberConfigPanel
                  member={selectedMember}
                  combo={activeCombo}
                  onUpdate={(p) => updateMember(activeCombo.id, selectedMember.id, p)}
                  onRemove={() => { removeMember(activeCombo.id, selectedMember.id); setSelectedMember(null); }}
                  onSetHead={() => setHeadAgent(activeCombo.id, selectedMember.id)}
                />
              ) : (
                <div className="ma-config-empty wide">
                  <FolderOpen size={40} strokeWidth={1.5} />
                  <h3>Category-Based Sub-Agents</h3>
                  <p>Select a category, click + to create sub-agents (Researcher, Debugger, Designer, MCP, Proxy, Plugin).</p>
                  <p>Every agent gets terminal, MCP, and plugin access by default. Customize compatibility, skills, and function calling per agent.</p>
                  <div className="ma-archetype-preview">
                    {ARCHETYPE_DEFINITIONS.slice(0, 4).map((a) => (
                      <span key={a.archetype} className="ma-archetype-pill" style={{ borderColor: a.color }}>
                        {a.icon} {a.name}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </main>
          </div>
        )}

        {tab === 'combos' && (
          <div className="ma-combos-layout">
            <aside className="ma-combo-list">
              <div className="ma-section-header">
                <span>Workflows</span>
                <button className="ma-icon-btn" onClick={handleAddCombo}><Plus size={14} /></button>
              </div>
              {combos.map((combo) => (
                <button key={combo.id} className={`ma-combo-item ${activeCombo?.id === combo.id ? 'active' : ''}`}
                  onClick={() => { setActiveCombo(combo.id); setSelectedMember(null); }}>
                  <div className="ma-combo-item-top">
                    <strong>{combo.name}</strong>
                    <span className={`ma-mode-badge ${combo.mode}`}>{combo.mode}</span>
                  </div>
                  <small>{combo.members.length} agents</small>
                  <div className="ma-combo-dots">
                    {combo.members.slice(0, 5).map((m) => (
                      <span key={m.id} style={{ background: m.brandColor }} title={m.name} />
                    ))}
                  </div>
                </button>
              ))}
            </aside>

            <main className="ma-combo-editor">
              {activeCombo ? (
                <>
                  <div className="ma-combo-header">
                    <input className="ma-combo-name" value={activeCombo.name}
                      onChange={(e) => updateCombo(activeCombo.id, { name: e.target.value })} />
                    <input className="ma-combo-desc" placeholder="Description..." value={activeCombo.description}
                      onChange={(e) => updateCombo(activeCombo.id, { description: e.target.value })} />
                    <div className="ma-combo-actions">
                      <button className="plugins-back" onClick={handleExportCombo}><Download size={12} /> Export</button>
                      <button className="plugins-back" onClick={() => duplicateCombo(activeCombo.id)}><Copy size={12} /> Duplicate</button>
                      {combos.length > 1 && (
                        <button className="plugins-back danger" onClick={() => removeCombo(activeCombo.id)}><Trash2 size={12} /></button>
                      )}
                    </div>
                  </div>
                  <div className="ma-mode-explainer">
                    {activeCombo.mode === 'parallel' ? (
                      <><GitBranch size={14} /> <strong>Parallel</strong> — Head agent coordinates sub-agents. Main results surface in chat.</>
                    ) : activeCombo.mode === 'fusion' ? (
                      <><Sparkles size={14} /> <strong>Fusion</strong> — Merge 2–5 agents and providers into one composite super-agent.</>
                    ) : (
                      <><Users size={14} /> <strong>Collaborative</strong> — All agents work on the same prompt simultaneously.</>
                    )}
                  </div>
                  {activeCombo.mode === 'fusion' && (
                    <FusionConfigPanel
                      combo={activeCombo}
                      onUpdate={(partial) => updateFusionConfig(activeCombo.id, partial)}
                      onToggleMember={(memberId, included) => toggleFusionMember(activeCombo.id, memberId, included)}
                      onOpenPresets={() => setCenterView('fusion')}
                    />
                  )}
                  <div className="ma-team-graph">
                    {activeCombo.mode === 'parallel' && (
                      <div className="ma-head-node">
                        <Crown size={16} />
                        <span>Head</span>
                        <small>{activeCombo.members.find((m) => m.id === activeCombo.headAgentId)?.name ?? 'Select head'}</small>
                      </div>
                    )}
                    <div className="ma-member-grid">
                      <AnimatePresence>
                        {activeCombo.members.map((member, i) => (
                          <motion.button key={member.id}
                            className={`ma-member-card ${selectedMemberId === member.id ? 'selected' : ''} ${!member.enabled ? 'disabled' : ''} ${member.id === activeCombo.headAgentId ? 'is-head' : ''}`}
                            style={{ borderColor: member.brandColor }}
                            initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: i * 0.05 }}
                            onClick={() => setSelectedMember(member.id)}>
                            {member.id === activeCombo.headAgentId && <Crown size={12} className="ma-head-crown" />}
                            <div className="ma-member-logo" style={{ background: member.brandColor }}>{member.name[0]}</div>
                            <strong>{member.name}</strong>
                            <small>{member.providerName}</small>
                            <span className="ma-member-model">{member.modelLabel}</span>
                            <span className="ma-member-role">{member.role}</span>
                          </motion.button>
                        ))}
                      </AnimatePresence>
                      <button className="ma-member-card add" onClick={() => setShowAddMember(true)}>
                        <Plus size={24} /><span>Add Agent</span>
                      </button>
                    </div>
                  </div>
                </>
              ) : (
                <div className="ma-empty">Create a combo workflow to get started</div>
              )}
            </main>

            <aside className="ma-member-config">
              {selectedMember && activeCombo ? (
                <MemberConfigPanel member={selectedMember} combo={activeCombo}
                  onUpdate={(p) => updateMember(activeCombo.id, selectedMember.id, p)}
                  onRemove={() => { removeMember(activeCombo.id, selectedMember.id); setSelectedMember(null); }}
                  onSetHead={() => setHeadAgent(activeCombo.id, selectedMember.id)} />
              ) : (
                <div className="ma-config-empty">
                  <Cpu size={32} strokeWidth={1.5} />
                  <p>Select an agent to configure tools, skills, parameters, and memory.</p>
                </div>
              )}
            </aside>
          </div>
        )}

        {tab === 'canvas' && activeCombo && (
          <div className="ma-canvas-layout">
            <TeamCanvas
              combo={activeCombo}
              selectedMemberId={selectedMemberId}
              onSelectMember={(id) => { setSelectedMember(id); setTab('categories'); }}
              activeRunId={activeRunId}
              activePhaseAgent={runHistory.find((r) => r.id === activeRunId)?.currentPhase ?? null}
            />
          </div>
        )}

        {tab === 'canvas' && !activeCombo && (
          <div className="ma-empty">Select or create a combo to view the team canvas</div>
        )}

        {tab === 'templates' && (
          <div className="ma-templates-grid">
            {WORKFLOW_TEMPLATES.map((tpl, i) => (
              <motion.article
                key={tpl.id}
                className="ma-template-card"
                style={{ background: `linear-gradient(135deg, ${tpl.gradient[0]}, ${tpl.gradient[1]})` }}
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.06 }}
              >
                <div className="ma-template-card-inner">
                  <span className="ma-template-icon">{tpl.icon}</span>
                  <h3>{tpl.name}</h3>
                  <p className="ma-template-tagline">{tpl.tagline}</p>
                  <div className="ma-template-meta">
                    <span className={`ma-mode-badge ${tpl.mode}`}>{tpl.mode}</span>
                    <span className="ma-template-category">{tpl.category}</span>
                  </div>
                  <button className="ma-template-install" onClick={() => handleInstallTemplate(tpl.id)}>
                    <Plus size={14} /> Install Team
                  </button>
                </div>
              </motion.article>
            ))}
          </div>
        )}

        {tab === 'mission' && (
          <div className="ma-mission-layout">
            <MissionControl
              runs={runHistory}
              activeRunId={activeRunId}
              multiAgentEnabled={multiAgentEnabled}
              comboName={activeCombo?.name}
            />
            {runHistory.length > 0 && (
              <button className="ma-clear-history" onClick={clearRunHistory}>Clear history</button>
            )}
          </div>
        )}

        {tab === 'system' && (
          <div className="ma-system-panel">
            <section className="ma-config-section">
              <h3><Cloud size={16} /> Cloud & System</h3>
              <label className="mcp-config-field">
                <span>Max parallel workers</span>
                <input type="number" min={1} max={12} value={systemConfig.maxParallelWorkers}
                  onChange={(e) => setSystemConfig({ maxParallelWorkers: Number(e.target.value) })} />
              </label>
              <label className="mcp-config-field">
                <span>Default workflow mode</span>
                <select value={systemConfig.defaultMode}
                  onChange={(e) => setSystemConfig({ defaultMode: e.target.value as MultiAgentMode })}>
                  <option value="parallel">Parallel</option>
                  <option value="collaborative">Collaborative</option>
                  <option value="fusion">Fusion</option>
                </select>
              </label>
              <label className="mcp-config-field">
                <span>Ocean proxy port</span>
                <input type="number" value={systemConfig.oceanProxyPort}
                  onChange={(e) => setSystemConfig({ oceanProxyPort: Number(e.target.value) })} />
              </label>
            </section>
            <section className="ma-config-section">
              <h3><Brain size={16} /> Head Agent Prompt</h3>
              <textarea className="ma-textarea" rows={4} value={systemConfig.headSynthesisPrompt}
                onChange={(e) => setSystemConfig({ headSynthesisPrompt: e.target.value })} />
            </section>
            <section className="ma-config-section">
              <h3><Users size={16} /> Collaborative Merge Prompt</h3>
              <textarea className="ma-textarea" rows={3} value={systemConfig.collaborativeMergePrompt}
                onChange={(e) => setSystemConfig({ collaborativeMergePrompt: e.target.value })} />
            </section>
          </div>
        )}

        {tab === 'memory' && (
          <div className="ma-system-panel">
            <section className="ma-config-section">
              <h3><Database size={16} /> Global Memory</h3>
              <label className="mcp-config-field">
                <span>Context window (tokens)</span>
                <input type="number" value={systemConfig.globalMemory.contextWindow}
                  onChange={(e) => setSystemConfig({ globalMemory: { ...systemConfig.globalMemory, contextWindow: Number(e.target.value) } })} />
              </label>
              <label className="mcp-config-field">
                <span>Max messages</span>
                <input type="number" value={systemConfig.globalMemory.maxMessages}
                  onChange={(e) => setSystemConfig({ globalMemory: { ...systemConfig.globalMemory, maxMessages: Number(e.target.value) } })} />
              </label>
              <label className="mcp-config-field">
                <span>PostgreSQL URI</span>
                <input type="password" placeholder="postgresql://..." value={systemConfig.globalMemory.postgresUri ?? ''}
                  onChange={(e) => setSystemConfig({ globalMemory: { ...systemConfig.globalMemory, postgresUri: e.target.value } })} />
              </label>
            </section>
          </div>
        )}
      </div>

      {showSubAgentCreator && creatorCategoryId && activeCombo && (
        <SubAgentCreator
          categoryId={creatorCategoryId}
          allowedArchetypes={categories.find((c) => c.id === creatorCategoryId)?.archetypes ?? ['custom']}
          connectedProviders={connectedProviders}
          onCreate={handleCreateSubAgent}
          onClose={() => { setShowSubAgentCreator(false); setCreatorCategoryId(null); }}
        />
      )}

      {showAddMember && (
        <div className="mcp-config-overlay" onClick={() => setShowAddMember(false)}>
          <div className="mcp-config-modal ma-add-modal" onClick={(e) => e.stopPropagation()}>
            <h3>Add Agent from Provider</h3>
            <p className="ma-add-hint">Roles and tasks are auto-suggested based on provider strengths.</p>
            {connectedProviders.length === 0 ? (
              <p>Connect providers first — <button className="plugin-install-btn" onClick={() => setCenterView('providers')}>Open Providers</button></p>
            ) : connectedProviders.map((p) => (
              <div key={p.providerId} className="ma-provider-picker">
                <div className="ma-provider-picker-header">
                  <span style={{ background: p.def?.brandColor, color: '#fff', padding: '4px 8px', borderRadius: 6, fontWeight: 700 }}>
                    {p.def?.logoLetter ?? p.name[0]}
                  </span>
                  <strong>{p.name}</strong>
                </div>
                <div className="ma-model-chips">
                  {p.models.map((modelId) => {
                    const hint = suggestForModel(p.providerId, modelId);
                    return (
                      <button key={modelId} className="ma-model-chip"
                        onClick={() => handleAddMemberFromProvider(p.providerId, modelId)}
                        title={`Suggested: ${hint.role} — ${hint.taskAssignment}`}>
                        {modelId} <span className="ma-chip-role">{hint.role}</span> <ChevronRight size={12} />
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function MemberConfigPanel({ member, combo, onUpdate, onRemove, onSetHead }: {
  member: AgentMember; combo: AgentCombo;
  onUpdate: (p: Partial<AgentMember>) => void;
  onRemove: () => void; onSetHead: () => void;
}) {
  return (
    <div className="ma-member-detail">
      <div className="ma-member-detail-header">
        <div className="ma-member-logo lg" style={{ background: member.brandColor }}>{member.name[0]}</div>
        <div>
          <input className="ma-combo-name sm" value={member.name} onChange={(e) => onUpdate({ name: e.target.value })} />
          <small>{member.providerName} · {member.modelLabel}</small>
        </div>
      </div>
      <label className="mcp-config-field">
        <span>Archetype</span>
        <select value={member.archetype} onChange={(e) => onUpdate({ archetype: e.target.value as AgentMember['archetype'] })}>
          {ARCHETYPE_DEFINITIONS.map((a) => (
            <option key={a.archetype} value={a.archetype}>{a.icon} {a.name}</option>
          ))}
        </select>
      </label>
      <label className="mcp-config-field">
        <span>Role</span>
        <select value={member.role} onChange={(e) => onUpdate({ role: e.target.value as AgentMember['role'] })}>
          <option value="head">Head</option><option value="worker">Worker</option>
          <option value="specialist">Specialist</option><option value="reviewer">Reviewer</option>
        </select>
      </label>
      {combo.mode === 'parallel' && member.id !== combo.headAgentId && (
        <button className="ma-set-head-btn" onClick={onSetHead}><Crown size={14} /> Set as Head Agent</button>
      )}
      <label className="mcp-config-field">
        <span>Task assignment</span>
        <textarea rows={2} value={member.taskAssignment} onChange={(e) => onUpdate({ taskAssignment: e.target.value })} />
      </label>
      <label className="mcp-config-field">
        <span>System prompt</span>
        <textarea className="ma-textarea" rows={3} value={member.systemPrompt} onChange={(e) => onUpdate({ systemPrompt: e.target.value })} />
      </label>
      <MemberAccessPanel member={member} onUpdate={onUpdate} />
      <section className="ma-mini-section">
        <h4>Parameters</h4>
        <label className="ma-slider-field">
          <span>Temperature {member.params.temperature}</span>
          <input type="range" min={0} max={2} step={0.1} value={member.params.temperature}
            onChange={(e) => onUpdate({ params: { ...member.params, temperature: Number(e.target.value) } })} />
        </label>
        <label className="ma-slider-field">
          <span>Max tokens {member.params.maxTokens}</span>
          <input type="range" min={512} max={128000} step={512} value={member.params.maxTokens}
            onChange={(e) => onUpdate({ params: { ...member.params, maxTokens: Number(e.target.value) } })} />
        </label>
        <label className="mcp-config-field">
          <span>Thinking level</span>
          <select value={member.params.thinkingLevel}
            onChange={(e) => onUpdate({ params: { ...member.params, thinkingLevel: e.target.value as AgentMember['params']['thinkingLevel'] } })}>
            <option value="off">Off</option><option value="low">Low</option>
            <option value="medium">Medium</option><option value="high">High</option><option value="max">Max</option>
          </select>
        </label>
      </section>
      <section className="ma-mini-section">
        <h4><Database size={12} /> Memory</h4>
        <label className="mcp-config-field">
          <span>Backend</span>
          <select value={member.memory.backend}
            onChange={(e) => onUpdate({ memory: { ...member.memory, backend: e.target.value as AgentMember['memory']['backend'] } })}>
            <option value="session">Session</option><option value="vault">Vault</option>
            <option value="postgres">PostgreSQL</option><option value="shared">Shared team</option>
          </select>
        </label>
        <label className="mcp-config-field">
          <span>Context window</span>
          <input type="number" value={member.memory.contextWindow}
            onChange={(e) => onUpdate({ memory: { ...member.memory, contextWindow: Number(e.target.value) } })} />
        </label>
        <label className="mcp-config-field">
          <span>Max messages</span>
          <input type="number" value={member.memory.maxMessages}
            onChange={(e) => onUpdate({ memory: { ...member.memory, maxMessages: Number(e.target.value) } })} />
        </label>
      </section>
      <section className="ma-mini-section">
        <h4><Wrench size={12} /> Tools ({member.tools.length})</h4>
        <div className="ma-tags">
          {member.tools.map((t) => <span key={t.id} className="ma-tag tool">{t.name}</span>)}
          {!member.tools.length && <span className="ma-tag muted">Provider defaults</span>}
        </div>
      </section>
      <section className="ma-mini-section">
        <h4><BookOpen size={12} /> Skills ({member.skills.length})</h4>
        <div className="ma-tags">
          {member.skills.map((s) => <span key={s.id} className="ma-tag skill">{s.name}</span>)}
          {!member.skills.length && <span className="ma-tag muted">Vault + injected docs</span>}
        </div>
      </section>
      <label className="mcp-config-field checkbox">
        <input type="checkbox" checked={member.enabled} onChange={(e) => onUpdate({ enabled: e.target.checked })} />
        <span>Enabled in workflow</span>
      </label>
      <button className="ma-remove-btn" onClick={onRemove}><Trash2 size={14} /> Remove from combo</button>
    </div>
  );
}
