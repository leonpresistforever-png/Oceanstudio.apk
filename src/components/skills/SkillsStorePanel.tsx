import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  BookOpen, Search, Download, Trash2, Upload, Sparkles, Loader2,
  Check, Plus, ExternalLink, Bot, Store,
} from 'lucide-react';
import { useSkillsStore, MAX_INSTALLED_SKILLS } from '../../skills/skillsStore';
import { BUILTIN_SKILLS, BUILTIN_SKILL_COUNT } from '../../skills/builtinCatalog';
import { MARKETPLACE_SKILL_COUNT } from '../../skills/marketplaceCounts';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import {
  fetchSkillContent,
  loadSkillsMarketplace,
  populateMarketplaceOnOpen,
  preloadSkillMarketplaceSeed,
} from '../../skills/researcher';
import { parseSkillMd, SKILL_CATEGORIES, type SkillDefinition, type SkillScope } from '../../skills/types';

interface Props {
  scope?: SkillScope;
  title?: string;
  showUpload?: boolean;
  showAgentCreate?: boolean;
}

export default function SkillsStorePanel({
  scope = 'both',
  title = 'Skills Store',
  showUpload = true,
  showAgentCreate = true,
}: Props) {
  const installed = useSkillsStore((s) => s.installed);
  const marketplaceSkills = useSkillsStore((s) => s.marketplaceSkills);
  const marketplaceLoading = useSkillsStore((s) => s.marketplaceLoading);
  const marketplaceLoaded = useSkillsStore((s) => s.marketplaceLoaded);
  const installSkill = useSkillsStore((s) => s.installSkill);
  const uninstallSkill = useSkillsStore((s) => s.uninstallSkill);
  const toggleSkill = useSkillsStore((s) => s.toggleSkill);
  const addCustomSkill = useSkillsStore((s) => s.addCustomSkill);
  const addAgentSkill = useSkillsStore((s) => s.addAgentSkill);
  const setMarketplaceSkills = useSkillsStore((s) => s.setMarketplaceSkills);
  const setMarketplaceLoading = useSkillsStore((s) => s.setMarketplaceLoading);
  const setMarketplaceLoaded = useSkillsStore((s) => s.setMarketplaceLoaded);

  const [tab, setTab] = useState<'marketplace' | 'installed' | 'custom'>('marketplace');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string>('all');
  const [installing, setInstalling] = useState<string | null>(null);
  const [customName, setCustomName] = useState('');
  const [customContent, setCustomContent] = useState('');
  const [agentPrompt, setAgentPrompt] = useState('');
  const [message, setMessage] = useState('');
  const loadGen = useRef(0);
  const debouncedSearch = useDebouncedValue(search, 300);

  const loadMarketplace = useCallback(async (q = '') => {
    const gen = ++loadGen.current;
    setMarketplaceLoading(true);
    try {
      const skills = q ? await loadSkillsMarketplace(q) : await populateMarketplaceOnOpen();
      if (gen !== loadGen.current) return;
      setMarketplaceSkills(skills);
      setMarketplaceLoaded(true);
    } finally {
      if (gen === loadGen.current) setMarketplaceLoading(false);
    }
  }, [setMarketplaceLoading, setMarketplaceSkills, setMarketplaceLoaded]);

  useEffect(() => {
    preloadSkillMarketplaceSeed();
  }, []);

  useEffect(() => {
    if (debouncedSearch.length >= 2) void loadMarketplace(debouncedSearch);
  }, [debouncedSearch, loadMarketplace]);

  useEffect(() => {
    if (!marketplaceLoaded && !marketplaceLoading) void loadMarketplace();
  }, [marketplaceLoaded, marketplaceLoading, loadMarketplace]);

  const installedIds = new Set(installed.map((s) => s.id));

  const catalog = marketplaceLoaded
    ? marketplaceSkills
    : [...BUILTIN_SKILLS];

  const filtered = useMemo(() => catalog.filter((s) => {
    if (scope !== 'both' && s.scope !== 'both' && s.scope !== scope) return false;
    if (category !== 'all' && s.category !== category) return false;
    if (debouncedSearch) {
      const hay = `${s.name} ${s.description} ${s.tags.join(' ')}`.toLowerCase();
      if (!hay.includes(debouncedSearch.toLowerCase())) return false;
    }
    return true;
  }), [catalog, scope, category, debouncedSearch]);

  async function handleInstall(skill: SkillDefinition) {
    setInstalling(skill.id);
    try {
      const content = await fetchSkillContent(skill);
      const result = installSkill({ ...skill, content });
      if (!result.ok) {
        setMessage(result.reason === 'cap'
          ? `Install limit (${MAX_INSTALLED_SKILLS}) reached — uninstall a skill first`
          : 'Already installed');
        return;
      }
      setMessage(`Installed: ${skill.name}`);
    } finally {
      setInstalling(null);
    }
  }

  function handleUploadFile(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      const raw = String(reader.result ?? '');
      const parsed = parseSkillMd(raw, file.name.replace(/\.md$/i, ''));
      const result = addCustomSkill({
        id: `custom.${crypto.randomUUID()}`,
        name: parsed.name,
        description: parsed.description || 'Uploaded skill.md',
        category: 'coding',
        scope: 'both',
        tags: ['upload', 'custom'],
        source: 'custom',
        content: parsed.content,
      });
      if (!result.ok) {
        setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_SKILLS}) reached` : 'Already installed');
        return;
      }
      setMessage(`Uploaded: ${parsed.name}`);
      setTab('installed');
    };
    reader.readAsText(file);
  }

  function handleAgentCreate() {
    if (!agentPrompt.trim()) return;
    const content = `---\nname: agent-skill\ndescription: Agent-created skill\n---\n\n# Agent Skill\n\n${agentPrompt.trim()}\n`;
    const parsed = parseSkillMd(content, 'Agent Skill');
    const result = addAgentSkill(parsed.name, parsed.content);
    if (!result.ok) {
      setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_SKILLS}) reached` : 'Already installed');
      return;
    }
    setAgentPrompt('');
    setMessage('Agent skill created and installed');
    setTab('installed');
  }

  function handleCustomAdd() {
    if (!customName.trim() || !customContent.trim()) return;
    const parsed = parseSkillMd(customContent, customName);
    const result = addCustomSkill({
      id: `custom.${crypto.randomUUID()}`,
      name: parsed.name,
      description: parsed.description || customName,
      category: 'coding',
      scope: 'both',
      tags: ['custom'],
      source: 'custom',
      content: parsed.content,
    });
    if (!result.ok) {
      setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_SKILLS}) reached` : 'Already installed');
      return;
    }
    setCustomName('');
    setCustomContent('');
    setMessage(`Added: ${parsed.name}`);
    setTab('installed');
  }

  const scopeInstalled = installed.filter(
    (s) => scope === 'both' || s.scope === scope || s.scope === 'both'
  );

  return (
    <div className="skills-store">
      <div className="skills-store-header">
        <BookOpen size={20} />
        <div>
          <h3>{title}</h3>
          <p>
            {BUILTIN_SKILL_COUNT}+ builtin · {MARKETPLACE_SKILL_COUNT}+ open-source · install skill.md for agents
            {scope !== 'both' && ` · ${scope} scope`}
          </p>
        </div>
        {marketplaceLoading && <Loader2 size={16} className="pg-spin" />}
      </div>

      <div className="skills-store-tabs">
        {(['marketplace', 'installed', 'custom'] as const).map((t) => (
          <button
            key={t}
            type="button"
            className={`skills-tab ${tab === t ? 'active' : ''}`}
            onClick={() => setTab(t)}
          >
            {t === 'marketplace' && <Store size={14} />}
            {t === 'installed' && <Check size={14} />}
            {t === 'custom' && <Plus size={14} />}
            {t.charAt(0).toUpperCase() + t.slice(1)}
            {t === 'installed' && ` (${scopeInstalled.length})`}
          </button>
        ))}
      </div>

      {tab === 'marketplace' && (
        <>
          <div className="skills-search-row">
            <Search size={16} />
            <input
              placeholder={`Search ${MARKETPLACE_SKILL_COUNT + BUILTIN_SKILL_COUNT}+ skills...`}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void loadMarketplace(search)}
            />
            <button type="button" onClick={() => void loadMarketplace(search)}>Search</button>
          </div>
          <div className="skills-filters">
            <button type="button" className={`skills-chip ${category === 'all' ? 'active' : ''}`} onClick={() => setCategory('all')}>All</button>
            {SKILL_CATEGORIES.map((c) => (
              <button key={c.id} type="button" className={`skills-chip ${category === c.id ? 'active' : ''}`} onClick={() => setCategory(c.id)}>{c.label}</button>
            ))}
          </div>
          <p className="skills-count">
            Showing {Math.min(filtered.length, 80).toLocaleString()} of {filtered.length.toLocaleString()} skills
          </p>
          <div className="skills-list">
            {filtered.slice(0, 80).map((skill) => (
              <div key={skill.id} className="skills-item">
                <div className="skills-item-body">
                  <strong>{skill.name}</strong>
                  {skill.verified && <span className="skills-verified">verified</span>}
                  <small>{skill.description}</small>
                  <div className="skills-tags">
                    {skill.tags.slice(0, 4).map((t) => <span key={t} className="skills-tag">{t}</span>)}
                    {skill.source === 'oss' && skill.author && <span className="skills-tag">{skill.author}</span>}
                  </div>
                </div>
                <div className="skills-item-actions">
                  {skill.repoUrl && (
                    <a href={skill.repoUrl} target="_blank" rel="noreferrer" className="skills-link"><ExternalLink size={12} /></a>
                  )}
                  {installedIds.has(skill.id) ? (
                    <span className="skills-installed-badge"><Check size={12} /> Installed</span>
                  ) : (
                    <button type="button" className="skills-install-btn" disabled={installing === skill.id} onClick={() => void handleInstall(skill)}>
                      {installing === skill.id ? <Loader2 size={12} className="pg-spin" /> : <Download size={12} />}
                      Install
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'installed' && (
        <div className="skills-list">
          {scopeInstalled.length === 0 && <p className="skills-empty">No skills installed yet. Browse the marketplace.</p>}
          {scopeInstalled.map((skill) => (
            <div key={skill.id} className="skills-item installed">
              <div className="skills-item-body">
                <strong>{skill.name}</strong>
                <small>{skill.description}</small>
                <span className="skills-source">{skill.source}</span>
              </div>
              <div className="skills-item-actions">
                <label className="skills-toggle">
                  <input type="checkbox" checked={skill.enabled} onChange={(e) => toggleSkill(skill.id, e.target.checked)} />
                  Active
                </label>
                <button type="button" className="skills-remove-btn" onClick={() => uninstallSkill(skill.id)}><Trash2 size={12} /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'custom' && (
        <div className="skills-custom">
          {showUpload && (
            <div className="skills-upload">
              <Upload size={18} />
              <div>
                <strong>Upload skill.md</strong>
                <p>Drop or select a SKILL.md / skill.md file with YAML frontmatter</p>
                <input type="file" accept=".md,.txt" onChange={(e) => e.target.files?.[0] && handleUploadFile(e.target.files[0])} />
              </div>
            </div>
          )}
          <div className="skills-custom-form">
            <h4>Create custom skill</h4>
            <input placeholder="Skill name" value={customName} onChange={(e) => setCustomName(e.target.value)} />
            <textarea rows={8} placeholder="---&#10;name: my-skill&#10;description: ...&#10;---&#10;&#10;# Instructions..." value={customContent} onChange={(e) => setCustomContent(e.target.value)} />
            <button type="button" className="skills-add-btn" onClick={handleCustomAdd}><Plus size={14} /> Add skill</button>
          </div>
          {showAgentCreate && (
            <div className="skills-agent-create">
              <Bot size={18} />
              <h4><Sparkles size={14} /> Agent-created skill</h4>
              <textarea rows={4} placeholder="Describe what the skill should teach the agent..." value={agentPrompt} onChange={(e) => setAgentPrompt(e.target.value)} />
              <button type="button" className="skills-add-btn" onClick={handleAgentCreate}><Bot size={14} /> Generate & install</button>
            </div>
          )}
        </div>
      )}

      {message && <p className="skills-message">{message}</p>}
    </div>
  );
}
