import { useState } from 'react';
import { Plus, Trash2, Key, BookOpen, Filter, Plug, Puzzle } from 'lucide-react';
import { usePlaygroundStore } from '../../store/playgroundStore';
import {
  getAllPlaygroundTools,
  getMcpCount,
  getPluginCount,
} from '../../playground/catalog';
import {
  filterMarketplaceTools,
  MARKETPLACE_FEATURE_FILTERS,
  countByFeature,
} from '../../playground/marketplaceCatalog';
import {
  MCP_ENV_TEMPLATE,
  PLUGIN_ENV_TEMPLATE,
  CUSTOM_INSTALL_STEPS,
  FEATURE_ENV_HINTS,
  buildCustomToolId,
  validateCustomEntry,
  type CustomMarketplaceEntry,
} from '../../playground/marketplaceMeta';
import type { PlaygroundToolCategory } from '../../playground/types';

interface Props {
  kind: 'mcp' | 'plugin';
}

export default function PlaygroundMarketplacePanel({ kind }: Props) {
  const customTools = usePlaygroundStore((s) => s.customMarketplaceTools);
  const featureFilter = usePlaygroundStore((s) => s.marketplaceFeatureFilter);
  const searchQuery = usePlaygroundStore((s) => s.searchQuery);
  const activeToolId = usePlaygroundStore((s) => s.activeToolId);
  const setFeatureFilter = usePlaygroundStore((s) => s.setMarketplaceFeatureFilter);
  const addCustomTool = usePlaygroundStore((s) => s.addCustomMarketplaceTool);
  const removeCustomTool = usePlaygroundStore((s) => s.removeCustomMarketplaceTool);
  const setActiveTool = usePlaygroundStore((s) => s.setActiveTool);
  const setPrompt = usePlaygroundStore((s) => s.setPrompt);

  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState('');
  const [form, setForm] = useState<Partial<CustomMarketplaceEntry>>({
    kind,
    icon: kind === 'mcp' ? '🔌' : '🧩',
    featureTags: [],
    platforms: ['electron', 'cloud'],
    envRequirements: kind === 'mcp' ? MCP_ENV_TEMPLATE : PLUGIN_ENV_TEMPLATE,
    installGuide: CUSTOM_INSTALL_STEPS[kind].join('\n'),
  });

  const allTools = getAllPlaygroundTools(customTools);
  const filtered = filterMarketplaceTools(allTools, kind, featureFilter, searchQuery);
  const featureCounts = countByFeature(allTools.filter((t) => t.kind === kind || t.category === kind));
  const totalCount = kind === 'mcp' ? getMcpCount() + customTools.filter((t) => t.kind === 'mcp').length : getPluginCount() + customTools.filter((t) => t.kind === 'plugin').length;

  const toggleFeatureTag = (tag: PlaygroundToolCategory) => {
    const current = form.featureTags ?? [];
    setForm({
      ...form,
      featureTags: current.includes(tag) ? current.filter((t) => t !== tag) : [...current, tag],
    });
  };

  const handleAddCustom = () => {
    const err = validateCustomEntry(form);
    if (err) {
      setFormError(err);
      return;
    }
    const id = buildCustomToolId(kind, form.name!);
    addCustomTool({
      id,
      name: form.name!,
      description: form.description!,
      category: kind,
      kind,
      icon: form.icon ?? (kind === 'mcp' ? '🔌' : '🧩'),
      tags: ['custom', kind],
      enabled: true,
      envRequirements: form.envRequirements,
      installGuide: form.installGuide,
      featureTags: form.featureTags as PlaygroundToolCategory[],
      platforms: form.platforms,
      customInstall: true,
      freeTier: true,
    });
    setShowForm(false);
    setFormError('');
    setForm({
      kind,
      icon: kind === 'mcp' ? '🔌' : '🧩',
      featureTags: [],
      platforms: ['electron', 'cloud'],
      envRequirements: kind === 'mcp' ? MCP_ENV_TEMPLATE : PLUGIN_ENV_TEMPLATE,
      installGuide: CUSTOM_INSTALL_STEPS[kind].join('\n'),
    });
    setActiveTool(id);
    setPrompt(`Use custom ${kind} "${form.name}": `);
  };

  const featureTagsForFilter = MARKETPLACE_FEATURE_FILTERS.filter((f) => f.id !== 'all');

  return (
    <section className="pg-marketplace">
      <div className="pg-marketplace-header">
        <div>
          <h4>
            {kind === 'mcp' ? <Plug size={16} /> : <Puzzle size={16} />}
            {kind === 'mcp' ? 'MCP Store' : 'Plugin Store'}
          </h4>
          <p>{totalCount}+ {kind === 'mcp' ? 'MCP connectors' : 'plugins'} — tagged per playground feature. Add your own below.</p>
        </div>
        <button className="pg-marketplace-add-btn" onClick={() => setShowForm(!showForm)}>
          <Plus size={14} /> Custom {kind === 'mcp' ? 'MCP' : 'Plugin'}
        </button>
      </div>

      <div className="pg-marketplace-filters">
        <Filter size={14} />
        {MARKETPLACE_FEATURE_FILTERS.map((f) => (
          <button
            key={f.id}
            className={`pg-filter-chip ${featureFilter === f.id ? 'active' : ''}`}
            onClick={() => setFeatureFilter(f.id)}
          >
            {f.label}
            {f.id !== 'all' && featureCounts[f.id] ? ` (${featureCounts[f.id]})` : ''}
          </button>
        ))}
      </div>

      {showForm && (
        <div className="pg-custom-form">
          <h5>Add Custom {kind === 'mcp' ? 'MCP Connector' : 'Plugin'}</h5>
          {formError && <p className="pg-form-error">{formError}</p>}
          <div className="pg-custom-grid">
            <label>
              <span>Name</span>
              <input value={form.name ?? ''} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="My Custom MCP" />
            </label>
            <label>
              <span>Icon (emoji)</span>
              <input value={form.icon ?? ''} onChange={(e) => setForm({ ...form, icon: e.target.value })} placeholder="🔌" />
            </label>
            <label className="pg-full">
              <span>Description</span>
              <input value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="What does this connector do?" />
            </label>
            <div className="pg-full">
              <span>Playground features (select all that apply)</span>
              <div className="pg-feature-tags">
                {featureTagsForFilter.map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    className={`pg-tag-btn ${(form.featureTags ?? []).includes(f.id as PlaygroundToolCategory) ? 'active' : ''}`}
                    onClick={() => toggleFeatureTag(f.id as PlaygroundToolCategory)}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            </div>
            <label className="pg-full">
              <span><Key size={12} /> Environment &amp; Requirements</span>
              <textarea
                rows={6}
                value={form.envRequirements ?? ''}
                onChange={(e) => setForm({ ...form, envRequirements: e.target.value })}
              />
            </label>
            <label className="pg-full">
              <span><BookOpen size={12} /> Install Guide</span>
              <textarea
                rows={4}
                value={form.installGuide ?? ''}
                onChange={(e) => setForm({ ...form, installGuide: e.target.value })}
              />
            </label>
          </div>
          <div className="pg-custom-actions">
            <button className="pg-run-btn" onClick={handleAddCustom}>Add to Marketplace</button>
            <button className="pg-cancel-btn" onClick={() => setShowForm(false)}>Cancel</button>
          </div>
          <div className="pg-env-hints">
            <strong>Suggested env vars by feature:</strong>
            <ul>
              {Object.entries(FEATURE_ENV_HINTS).map(([feat, hint]) => (
                <li key={feat}><code>{feat}</code>: {hint}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className="pg-marketplace-list">
        <p className="pg-marketplace-count">Showing {filtered.length} of {totalCount}+</p>
        {filtered.slice(0, 150).map((t) => (
          <button
            key={t.id}
            className={`pg-marketplace-item ${activeToolId === t.id ? 'active' : ''}`}
            onClick={() => {
              setActiveTool(t.id);
              setPrompt(`Use ${t.kind} "${t.name}": `);
            }}
          >
            <span className="pg-tool-icon">{t.icon}</span>
            <div className="pg-marketplace-item-body">
              <strong>{t.name}</strong>
              <small>{t.description}</small>
              <div className="pg-marketplace-tags">
                {(t.featureTags ?? []).slice(0, 4).map((tag) => (
                  <span key={tag} className="pg-feature-tag">{tag}</span>
                ))}
                {t.freeTier && <span className="pg-badge free">Free</span>}
                {t.customInstall && <span className="pg-badge custom">Custom OK</span>}
              </div>
              {t.envRequirements && (
                <code className="pg-marketplace-env">{t.envRequirements.slice(0, 80)}{t.envRequirements.length > 80 ? '…' : ''}</code>
              )}
            </div>
            {t.id.startsWith('custom-') && (
              <button
                className="pg-remove-btn"
                onClick={(e) => { e.stopPropagation(); removeCustomTool(t.id); }}
                title="Remove custom entry"
              >
                <Trash2 size={14} />
              </button>
            )}
          </button>
        ))}
        {filtered.length > 150 && (
          <p className="pg-tool-more">+{filtered.length - 150} more — refine search or filter</p>
        )}
      </div>
    </section>
  );
}
