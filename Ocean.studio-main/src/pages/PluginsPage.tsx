import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, Plus, Download, Trash2, Play, Square, Check, Star,
  Package, Globe, Smartphone, Monitor, Sparkles, Code2, Upload,
  ExternalLink, RefreshCw,
} from 'lucide-react';
import { getOceanAPI } from '../lib/platform';
import { getOfficialPlugins } from '../plugins/marketplace/official';
import { runPluginResearcher, searchOpenSourcePlugins } from '../plugins/marketplace/researcher';
import {
  getInstalledPlugins, installAndStart, uninstallPlugin, setPluginEnabled,
} from '../plugins/loader';
import { createPluginTemplate, parseOceanPluginJson } from '../plugins/formats/ocean';
import { parseMcpJson } from '../plugins/formats/mcp';
import type { MarketplaceTab, OceanPluginManifest, PluginPlatform } from '../plugins/types';
import { useAppStore } from '../store/appStore';
import './PluginsPage.css';

function detectPlatform(): PluginPlatform {
  if (typeof window !== 'undefined' && window.ocean) return 'electron';
  const ua = navigator.userAgent;
  if (/Android|iPhone|iPad/i.test(ua) && !(window as unknown as { ocean?: unknown }).ocean) return 'mobile';
  return 'web';
}

const PLATFORM_LABELS: Record<PluginPlatform, { label: string; icon: typeof Monitor }> = {
  electron: { label: 'Desktop', icon: Monitor },
  web: { label: 'Web', icon: Globe },
  mobile: { label: 'Mobile', icon: Smartphone },
};

export default function PluginsPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  const [platform, setPlatform] = useState<PluginPlatform>('electron');
  const [tab, setTab] = useState<MarketplaceTab>('official');
  const [search, setSearch] = useState('');
  const [installed, setInstalled] = useState(getInstalledPlugins());
  const [official, setOfficial] = useState<OceanPluginManifest[]>([]);
  const [opensource, setOpensource] = useState<OceanPluginManifest[]>([]);
  const [researching, setResearching] = useState(false);
  const [researchLog, setResearchLog] = useState<string[]>([]);
  const [createPlatform, setCreatePlatform] = useState<PluginPlatform>('electron');
  const [createJson, setCreateJson] = useState('');
  const [importJson, setImportJson] = useState('');

  useEffect(() => {
    getOceanAPI().platform.get().then((info) => {
      if (info.isAndroid || info.hasNativeTerminal) setPlatform('mobile');
      else if (info.isElectron) setPlatform('electron');
      else setPlatform('web');
    });
  }, []);

  useEffect(() => {
    setOfficial(getOfficialPlugins(platform));
  }, [platform]);

  const refreshInstalled = useCallback(() => setInstalled(getInstalledPlugins()), []);

  const runResearch = useCallback(async (query?: string) => {
    setResearching(true);
    setResearchLog([]);
    addAgentLog({
      id: crypto.randomUUID(),
      type: 'action',
      timestamp: Date.now(),
      summary: query ? `Searching plugins: "${query}"` : 'Plugin researcher scanning sources...',
      status: 'running',
    });

    try {
      if (query?.trim()) {
        const results = await searchOpenSourcePlugins(query, platform);
        setOpensource(results.map((r) => r.manifest));
        setResearchLog([`Found ${results.length} plugins for "${query}"`]);
      } else {
        const found = await runPluginResearcher(platform, (msg) => {
          setResearchLog((prev) => [...prev, msg]);
        });
        setOpensource(found);
      }
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Plugin research complete — ${opensource.length || 'new'} results`,
        status: 'completed',
      });
    } catch (e) {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: e instanceof Error ? e.message : 'Research failed',
        status: 'failed',
      });
    }
    setResearching(false);
  }, [platform, addAgentLog, opensource.length]);

  useEffect(() => {
    if (tab === 'opensource' && opensource.length === 0) runResearch();
  }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleInstall(manifest: OceanPluginManifest) {
    const api = getOceanAPI();
    try {
      await installAndStart(manifest, api as Parameters<typeof installAndStart>[1]);
      refreshInstalled();
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Installed plugin: ${manifest.name}`,
        status: 'completed',
      });
    } catch (e) {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: e instanceof Error ? e.message : 'Install failed',
        status: 'failed',
      });
    }
  }

  async function handleUninstall(id: string) {
    const api = getOceanAPI() as { plugins?: { uninstall: (id: string) => Promise<void> } };
    if (api.plugins) await api.plugins.uninstall(id);
    else uninstallPlugin(id);
    refreshInstalled();
  }

  async function handleToggle(id: string, enabled: boolean) {
    const api = getOceanAPI() as { plugins?: { setEnabled: (id: string, e: boolean) => Promise<void> } };
    if (api.plugins) await api.plugins.setEnabled(id, enabled);
    else setPluginEnabled(id, enabled);
    refreshInstalled();
  }

  function handleCreateTemplate() {
    const template = createPluginTemplate(createPlatform);
    setCreateJson(JSON.stringify(template, null, 2));
  }

  function handleSaveCreated() {
    try {
      const parsed = parseOceanPluginJson(JSON.parse(createJson));
      if (parsed) handleInstall(parsed);
    } catch { /* invalid json */ }
  }

  function handleImportMcp() {
    try {
      const parsed = JSON.parse(importJson);
      const plugins = parseMcpJson(parsed, [platform]);
      plugins.forEach((p) => handleInstall(p));
      setImportJson('');
    } catch { /* invalid */ }
  }

  const filteredOfficial = official.filter((p) =>
    !search || `${p.name} ${p.description} ${p.tags?.join(' ')}`.toLowerCase().includes(search.toLowerCase())
  );
  const filteredOss = opensource.filter((p) =>
    !search || `${p.name} ${p.description} ${p.tags?.join(' ')}`.toLowerCase().includes(search.toLowerCase())
  );

  const PlatformIcon = PLATFORM_LABELS[platform].icon;

  return (
    <div className="plugins-page">
      <motion.div
        className="plugins-header"
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
      >
        <div className="plugins-header-left">
          <button className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1>Plugins</h1>
          <span className="plugins-platform-badge">
            <PlatformIcon size={14} />
            {PLATFORM_LABELS[platform].label}
          </span>
        </div>
        <div className="plugins-search">
          <Search size={16} />
          <input
            placeholder="Search plugins — agent will fetch related results..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && tab === 'opensource' && runResearch(search)}
          />
          {tab === 'opensource' && (
            <button className="plugins-search-btn" onClick={() => runResearch(search)} disabled={researching}>
              <Sparkles size={14} /> Research
            </button>
          )}
        </div>
      </motion.div>

      <div className="plugins-tabs">
        {(['official', 'opensource', 'installed', 'create'] as MarketplaceTab[]).map((t) => (
          <button
            key={t}
            className={`plugins-tab ${tab === t ? 'active' : ''}`}
            onClick={() => setTab(t)}
          >
            {t === 'official' && 'Marketplace'}
            {t === 'opensource' && 'Open Source Plugins'}
            {t === 'installed' && `Installed (${installed.length})`}
            {t === 'create' && 'Create'}
          </button>
        ))}
      </div>

      <div className="plugins-body">
        <AnimatePresence mode="wait">
          {tab === 'official' && (
            <motion.div key="official" className="plugins-grid" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <p className="plugins-section-desc">Curated by Ocean.studio — production-ready plugins for {PLATFORM_LABELS[platform].label}</p>
              {filteredOfficial.map((p) => (
                <PluginCard key={p.id} manifest={p} installed={installed.some((i) => i.manifest.id === p.id)} onInstall={() => handleInstall(p)} />
              ))}
            </motion.div>
          )}

          {tab === 'opensource' && (
            <motion.div key="oss" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <div className="plugins-researcher-bar">
                <Sparkles size={16} />
                <span>Plugin researcher — finds extensions &amp; tools with built-in compatibility (not MCP — use MCP Connectors for those)</span>
                <button onClick={() => runResearch(search)} disabled={researching}>
                  <RefreshCw size={14} className={researching ? 'spin' : ''} />
                  {researching ? 'Researching...' : 'Refresh'}
                </button>
              </div>
              {researchLog.length > 0 && (
                <div className="plugins-research-log">
                  {researchLog.map((l, i) => <div key={i}>{l}</div>)}
                </div>
              )}
              <div className="plugins-grid">
                {filteredOss.map((p) => (
                  <PluginCard key={p.id} manifest={p} installed={installed.some((i) => i.manifest.id === p.id)} onInstall={() => handleInstall(p)} />
                ))}
                {filteredOss.length === 0 && !researching && (
                  <div className="plugins-empty">Search or click Refresh to discover open source plugins</div>
                )}
              </div>
            </motion.div>
          )}

          {tab === 'installed' && (
            <motion.div key="installed" className="plugins-installed-list" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              {installed.length === 0 && <div className="plugins-empty">No plugins installed yet</div>}
              {installed.map((p) => (
                <div key={p.manifest.id} className="plugins-installed-row">
                  <div className="plugins-installed-info">
                    <strong>{p.manifest.name}</strong>
                    <span className={`plugins-status plugins-status-${p.status}`}>{p.status}</span>
                    <span className="plugins-installed-desc">{p.manifest.description}</span>
                  </div>
                  <div className="plugins-installed-actions">
                    <button onClick={() => handleToggle(p.manifest.id, !p.enabled)} title={p.enabled ? 'Disable' : 'Enable'}>
                      {p.enabled ? <Square size={14} /> : <Play size={14} />}
                    </button>
                    <button onClick={() => handleUninstall(p.manifest.id)} title="Uninstall"><Trash2 size={14} /></button>
                  </div>
                </div>
              ))}
            </motion.div>
          )}

          {tab === 'create' && (
            <motion.div key="create" className="plugins-create" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
              <div className="plugins-create-platforms">
                {(['electron', 'web', 'mobile'] as PluginPlatform[]).map((pl) => {
                  const Icon = PLATFORM_LABELS[pl].icon;
                  if (pl === 'electron' && platform !== 'electron') return null;
                  if (pl === 'mobile' && platform !== 'mobile') return null;
                  if (pl === 'web' && platform === 'mobile') return null;
                  return (
                    <button
                      key={pl}
                      className={`plugins-platform-btn ${createPlatform === pl ? 'active' : ''}`}
                      onClick={() => setCreatePlatform(pl)}
                    >
                      <Icon size={18} />
                      {PLATFORM_LABELS[pl].label} Plugin
                    </button>
                  );
                })}
              </div>

              <div className="plugins-create-actions">
                <button onClick={handleCreateTemplate}><Code2 size={14} /> Generate Template</button>
                <button onClick={handleSaveCreated}><Plus size={14} /> Install Plugin</button>
              </div>

              <textarea
                className="plugins-json-editor"
                value={createJson}
                onChange={(e) => setCreateJson(e.target.value)}
                placeholder='Paste ocean.plugin.json or click Generate Template...'
                spellCheck={false}
              />

              <div className="plugins-import-section">
                <h3><Upload size={16} /> Import MCP Config (Cursor / Claude Code / Codex)</h3>
                <textarea
                  className="plugins-json-editor plugins-import"
                  value={importJson}
                  onChange={(e) => setImportJson(e.target.value)}
                  placeholder='{"mcpServers": {"filesystem": {"command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem"]}}}'
                  spellCheck={false}
                />
                <button onClick={handleImportMcp}><Download size={14} /> Import mcp.json</button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

function PluginCard({
  manifest, installed, onInstall,
}: {
  manifest: OceanPluginManifest;
  installed: boolean;
  onInstall: () => void;
}) {
  return (
    <motion.div
      className="plugin-card"
      whileHover={{ y: -2, boxShadow: '0 8px 24px rgba(0,0,0,0.12)' }}
      transition={{ duration: 0.2 }}
    >
      <div className="plugin-card-header">
        <Package size={20} />
        <div>
          <h3>{manifest.name}</h3>
          {manifest.verified && <span className="plugin-verified"><Check size={10} /> Verified</span>}
        </div>
      </div>
      <p className="plugin-card-desc">{manifest.description}</p>
      <div className="plugin-card-meta">
        {manifest.rating && <span><Star size={12} /> {manifest.rating.toFixed(1)}</span>}
        {manifest.downloads && <span>{manifest.downloads.toLocaleString()} installs</span>}
        <span className="plugin-type">{manifest.type}</span>
      </div>
      <div className="plugin-card-tags">
        {manifest.tags?.slice(0, 4).map((t) => <span key={t} className="plugin-tag">{t}</span>)}
      </div>
      <div className="plugin-card-actions">
        {manifest.homepage && (
          <a href={manifest.homepage} target="_blank" rel="noreferrer" className="plugin-link">
            <ExternalLink size={12} />
          </a>
        )}
        <button
          className={`plugin-install-btn ${installed ? 'installed' : ''}`}
          onClick={onInstall}
          disabled={installed}
        >
          {installed ? <><Check size={14} /> Installed</> : <><Download size={14} /> Install</>}
        </button>
      </div>
    </motion.div>
  );
}
