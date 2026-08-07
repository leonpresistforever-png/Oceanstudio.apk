import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Puzzle, Search, Download, Trash2, Upload, Bot, Loader2, Check, Plus,
  ExternalLink, Store, Star, ToggleLeft,
} from 'lucide-react';
import { useExtensionsStore, MAX_INSTALLED_EXTENSIONS } from '../../extensions/extensionsStore';
import { BUILTIN_EXTENSIONS, BUILTIN_EXTENSION_COUNT } from '../../extensions/builtinCatalog';
import { MARKETPLACE_EXTENSION_COUNT } from '../../extensions/marketplaceCounts';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import {
  loadExtensionsMarketplace,
  populateExtensionsOnOpen,
  preloadExtensionMarketplaceSeed,
} from '../../extensions/researcher';
import { EXTENSION_CATEGORIES, parseExtensionManifest, type ExtensionDefinition } from '../../extensions/types';

interface Props {
  title?: string;
}

export default function ExtensionsStorePanel({ title = 'Extensions' }: Props) {
  const installed = useExtensionsStore((s) => s.installed);
  const marketplace = useExtensionsStore((s) => s.marketplace);
  const marketplaceLoaded = useExtensionsStore((s) => s.marketplaceLoaded);
  const marketplaceLoading = useExtensionsStore((s) => s.marketplaceLoading);
  const installExtension = useExtensionsStore((s) => s.installExtension);
  const uninstallExtension = useExtensionsStore((s) => s.uninstallExtension);
  const toggleExtension = useExtensionsStore((s) => s.toggleExtension);
  const addCustomExtension = useExtensionsStore((s) => s.addCustomExtension);
  const addAgentExtension = useExtensionsStore((s) => s.addAgentExtension);
  const setMarketplace = useExtensionsStore((s) => s.setMarketplace);
  const setMarketplaceLoading = useExtensionsStore((s) => s.setMarketplaceLoading);
  const setMarketplaceLoaded = useExtensionsStore((s) => s.setMarketplaceLoaded);

  const [tab, setTab] = useState<'marketplace' | 'installed' | 'custom'>('marketplace');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string>('all');
  const [installing, setInstalling] = useState<string | null>(null);
  const [customJson, setCustomJson] = useState('');
  const [agentDesc, setAgentDesc] = useState('');
  const [message, setMessage] = useState('');
  const loadGen = useRef(0);
  const debouncedSearch = useDebouncedValue(search, 300);

  const loadMarketplace = useCallback(async (q = '') => {
    const gen = ++loadGen.current;
    setMarketplaceLoading(true);
    try {
      const items = q ? await loadExtensionsMarketplace(q) : await populateExtensionsOnOpen();
      if (gen !== loadGen.current) return;
      setMarketplace(items);
      setMarketplaceLoaded(true);
    } finally {
      if (gen === loadGen.current) setMarketplaceLoading(false);
    }
  }, [setMarketplace, setMarketplaceLoading, setMarketplaceLoaded]);

  useEffect(() => {
    preloadExtensionMarketplaceSeed();
  }, []);

  useEffect(() => {
    if (debouncedSearch.length >= 2) void loadMarketplace(debouncedSearch);
  }, [debouncedSearch, loadMarketplace]);

  useEffect(() => {
    if (!marketplaceLoaded && !marketplaceLoading) void loadMarketplace();
  }, [marketplaceLoaded, marketplaceLoading, loadMarketplace]);

  const installedIds = new Set(installed.map((e) => e.id));
  const catalog = marketplaceLoaded ? marketplace : [...BUILTIN_EXTENSIONS];

  const filtered = useMemo(() => catalog.filter((e) => {
    if (category !== 'all' && e.category !== category) return false;
    if (debouncedSearch) {
      const hay = `${e.displayName} ${e.description} ${e.id} ${e.publisher}`.toLowerCase();
      if (!hay.includes(debouncedSearch.toLowerCase())) return false;
    }
    return true;
  }), [catalog, category, debouncedSearch]);

  async function handleInstall(ext: ExtensionDefinition) {
    setInstalling(ext.id);
    try {
      const result = installExtension(ext);
      if (!result.ok) {
        setMessage(result.reason === 'cap'
          ? `Install limit (${MAX_INSTALLED_EXTENSIONS}) reached — uninstall an extension first`
          : 'Already installed');
        return;
      }
      setMessage(`Installed ${ext.displayName} — agent context updated with usage guide`);
    } finally {
      setInstalling(null);
    }
  }

  function handleUploadManifest(file: File) {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const json = JSON.parse(String(reader.result)) as Record<string, unknown>;
        const parsed = parseExtensionManifest(json);
        const result = addCustomExtension({
          id: parsed.id ?? `custom.${crypto.randomUUID()}`,
          name: parsed.name ?? 'custom',
          displayName: parsed.displayName ?? 'Custom Extension',
          description: parsed.description ?? 'Uploaded extension manifest',
          version: parsed.version ?? '1.0.0',
          publisher: parsed.publisher ?? 'custom',
          category: 'other',
          platforms: ['electron', 'web'],
          installType: 'custom',
          source: 'custom',
          agentGuide: `## ${parsed.displayName}\n\nCustom uploaded extension.\n\n${parsed.description ?? ''}`,
          commands: parsed.commands,
          activationEvents: parsed.activationEvents,
          tags: ['custom', 'upload'],
        });
        if (!result.ok) {
          setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_EXTENSIONS}) reached` : 'Already installed');
          return;
        }
        setMessage(`Uploaded extension: ${parsed.displayName}`);
        setTab('installed');
      } catch {
        setMessage('Invalid extension manifest JSON (package.json or extension manifest format)');
      }
    };
    reader.readAsText(file);
  }

  function handleJsonInstall() {
    if (!customJson.trim()) return;
    try {
      const json = JSON.parse(customJson) as Record<string, unknown>;
      const parsed = parseExtensionManifest(json);
      const result = addCustomExtension({
        id: parsed.id ?? `custom.${crypto.randomUUID()}`,
        name: parsed.name ?? 'custom',
        displayName: parsed.displayName ?? 'Custom',
        description: parsed.description ?? '',
        version: parsed.version ?? '1.0.0',
        publisher: parsed.publisher ?? 'custom',
        category: 'other',
        platforms: ['electron', 'web'],
        installType: 'custom',
        source: 'custom',
        agentGuide: `## ${parsed.displayName}\n\n${parsed.description ?? 'Custom extension'}`,
        commands: parsed.commands,
        tags: ['custom'],
      });
      if (!result.ok) {
        setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_EXTENSIONS}) reached` : 'Already installed');
        return;
      }
      setCustomJson('');
      setMessage('Extension installed from JSON');
      setTab('installed');
    } catch {
      setMessage('Invalid JSON manifest');
    }
  }

  function handleAgentCreate() {
    if (!agentDesc.trim()) return;
    const result = addAgentExtension('Agent Extension', `## Agent Extension\n\n${agentDesc.trim()}\n\nUse when user requests this workflow.`);
    if (!result.ok) {
      setMessage(result.reason === 'cap' ? `Install limit (${MAX_INSTALLED_EXTENSIONS}) reached` : 'Already installed');
      return;
    }
    setAgentDesc('');
    setMessage('Agent extension created — usage guide injected into agent context');
    setTab('installed');
  }

  return (
    <div className="ext-store">
      <div className="ext-store-header">
        <Puzzle size={20} />
        <div>
          <h3>{title}</h3>
          <p>
            VS Code / Antigravity-style extensions — {BUILTIN_EXTENSION_COUNT}+ curated, {MARKETPLACE_EXTENSION_COUNT}+ OSS via Open VSX.
            Agent receives install context and usage guides.
          </p>
        </div>
        {marketplaceLoading && <Loader2 size={16} className="pg-spin" />}
      </div>

      <div className="ext-store-tabs">
        {(['marketplace', 'installed', 'custom'] as const).map((t) => (
          <button key={t} type="button" className={`ext-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
            {t === 'marketplace' && <Store size={14} />}
            {t === 'installed' && <ToggleLeft size={14} />}
            {t === 'custom' && <Plus size={14} />}
            {t.charAt(0).toUpperCase() + t.slice(1)}
            {t === 'installed' && ` (${installed.length})`}
          </button>
        ))}
      </div>

      {tab === 'marketplace' && (
        <>
          <div className="ext-search-row">
            <Search size={16} />
            <input
              placeholder={`Search Open VSX + ${MARKETPLACE_EXTENSION_COUNT + BUILTIN_EXTENSION_COUNT}+ extensions (Prettier, ESLint, Python...)`}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && void loadMarketplace(search)}
            />
            <button type="button" onClick={() => void loadMarketplace(search)}>Search</button>
          </div>
          <div className="ext-filters">
            <button type="button" className={`ext-chip ${category === 'all' ? 'active' : ''}`} onClick={() => setCategory('all')}>All</button>
            {EXTENSION_CATEGORIES.map((c) => (
              <button key={c.id} type="button" className={`ext-chip ${category === c.id ? 'active' : ''}`} onClick={() => setCategory(c.id)}>{c.label}</button>
            ))}
          </div>
          <p className="ext-count">
            Showing {Math.min(filtered.length, 80).toLocaleString()} of {filtered.length.toLocaleString()} extensions · live Open VSX + OSS seed
          </p>
          <div className="ext-list">
            {filtered.slice(0, 80).map((ext) => (
              <div key={ext.id} className="ext-item">
                <div className="ext-item-body">
                  <strong>{ext.displayName}</strong>
                  {ext.verified && <span className="ext-verified">verified</span>}
                  <small>{ext.publisher} · v{ext.version} · {ext.description}</small>
                  <div className="ext-meta">
                    {ext.rating != null && <span><Star size={10} /> {ext.rating.toFixed(1)}</span>}
                    {ext.downloads && <span>{ext.downloads.toLocaleString()} installs</span>}
                    <span className="ext-cat">{ext.category}</span>
                  </div>
                </div>
                <div className="ext-item-actions">
                  {(ext.repository || ext.openvsxId) && (
                    <a
                      href={ext.repository ?? `https://open-vsx.org/extension/${ext.publisher}/${ext.name}`}
                      target="_blank"
                      rel="noreferrer"
                      className="ext-link"
                    >
                      <ExternalLink size={12} />
                    </a>
                  )}
                  {installedIds.has(ext.id) ? (
                    <span className="ext-installed-badge"><Check size={12} /> Installed</span>
                  ) : (
                    <button type="button" className="ext-install-btn" disabled={installing === ext.id} onClick={() => void handleInstall(ext)}>
                      {installing === ext.id ? <Loader2 size={12} className="pg-spin" /> : <Download size={12} />}
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
        <div className="ext-list">
          {installed.length === 0 && <p className="ext-empty">No extensions installed. Browse marketplace for Prettier, ESLint, Python, Docker, Antigravity-style tools.</p>}
          {installed.map((ext) => (
            <div key={ext.id} className="ext-item installed">
              <div className="ext-item-body">
                <strong>{ext.displayName}</strong>
                <small>{ext.publisher} · {ext.description}</small>
                <details className="ext-guide-preview">
                  <summary>Agent usage guide</summary>
                  <pre>{ext.agentGuide}</pre>
                </details>
              </div>
              <div className="ext-item-actions">
                <label className="ext-toggle">
                  <input type="checkbox" checked={ext.enabled} onChange={(e) => toggleExtension(ext.id, e.target.checked)} />
                  Active
                </label>
                <button type="button" className="ext-remove-btn" onClick={() => uninstallExtension(ext.id)}><Trash2 size={12} /></button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'custom' && (
        <div className="ext-custom">
          <div className="ext-upload">
            <Upload size={18} />
            <div>
              <strong>Upload extension manifest</strong>
              <p>package.json or VS Code extension manifest (contributes.commands, activationEvents)</p>
              <input type="file" accept=".json" onChange={(e) => e.target.files?.[0] && handleUploadManifest(e.target.files[0])} />
            </div>
          </div>
          <div className="ext-custom-form">
            <h4>Paste extension JSON</h4>
            <textarea rows={8} placeholder='{"name":"my-ext","displayName":"My Extension","publisher":"me","version":"1.0.0","description":"...","contributes":{"commands":[]}}' value={customJson} onChange={(e) => setCustomJson(e.target.value)} />
            <button type="button" className="ext-add-btn" onClick={handleJsonInstall}><Plus size={14} /> Install from JSON</button>
          </div>
          <div className="ext-agent-create">
            <Bot size={18} />
            <h4>Agent-created extension guide</h4>
            <textarea rows={4} placeholder="Describe extension workflow for the agent (commands, when to use, fallbacks)..." value={agentDesc} onChange={(e) => setAgentDesc(e.target.value)} />
            <button type="button" className="ext-add-btn" onClick={handleAgentCreate}><Bot size={14} /> Create & install</button>
          </div>
        </div>
      )}

      {message && <p className="ext-message">{message}</p>}
    </div>
  );
}
