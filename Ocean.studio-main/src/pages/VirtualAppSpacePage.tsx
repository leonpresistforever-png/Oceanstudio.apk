import { useState } from 'react';
import { Box, Plus, Trash2 } from 'lucide-react';
import { useVirtualAppStore } from '../store/virtualAppStore';
import PreviewFrame from '../components/workspace/PreviewFrame';
import type { PreviewViewportMode } from '../lib/previewViewport';
import './VirtualAppSpacePage.css';

export default function VirtualAppSpacePage() {
  const clones = useVirtualAppStore((s) => s.clones);
  const activeCloneId = useVirtualAppStore((s) => s.activeCloneId);
  const setActiveClone = useVirtualAppStore((s) => s.setActiveClone);
  const cloneFromUrl = useVirtualAppStore((s) => s.cloneFromUrl);
  const cloneFromPort = useVirtualAppStore((s) => s.cloneFromPort);
  const cloneFromBinary = useVirtualAppStore((s) => s.cloneFromBinary);
  const removeClone = useVirtualAppStore((s) => s.removeClone);
  const updateClone = useVirtualAppStore((s) => s.updateClone);

  const [name, setName] = useState('');
  const [source, setSource] = useState('');
  const [cloneType, setCloneType] = useState<'url' | 'port' | 'exe' | 'apk'>('url');

  const active = clones.find((c) => c.id === activeCloneId);

  async function handleClone() {
    if (!name.trim() || !source.trim()) return;
    if (cloneType === 'url') cloneFromUrl(name, source);
    else if (cloneType === 'port') cloneFromPort(name, parseInt(source, 10));
    else await cloneFromBinary(name, source, cloneType);
    setName('');
    setSource('');
  }

  return (
    <div className="va-page">
      <header className="va-header">
        <h1><Box size={22} style={{ display: 'inline', verticalAlign: 'middle', marginRight: 8 }} />Virtual App Space</h1>
        <p>Clone EXE, APK, URLs, or ports into isolated virtual spaces — agents scrape and operate apps as part of the system</p>
      </header>

      <div className="va-toolbar">
        <input className="va-input" placeholder="App name" value={name} onChange={(e) => setName(e.target.value)} />
        <select className="va-input" value={cloneType} onChange={(e) => setCloneType(e.target.value as typeof cloneType)}>
          <option value="url">URL / PWA</option>
          <option value="port">Local port</option>
          <option value="exe">Windows EXE</option>
          <option value="apk">Android APK</option>
        </select>
        <input
          className="va-input"
          placeholder={cloneType === 'port' ? '3000' : cloneType === 'url' ? 'https://…' : 'C:\\path\\app.exe'}
          value={source}
          onChange={(e) => setSource(e.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <button type="button" className="va-btn" onClick={handleClone}>
          <Plus size={14} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
          Clone into space
        </button>
      </div>

      <div className="va-layout">
        <aside className="va-sidebar">
          <h3 style={{ fontSize: '0.75rem', color: '#78716c', margin: '0 0 12px', textTransform: 'uppercase' }}>Virtual clones ({clones.length})</h3>
          {clones.length === 0 && <p style={{ fontSize: '0.8rem', color: '#57534e' }}>No clones yet</p>}
          {clones.map((c) => (
            <div
              key={c.id}
              className={`va-clone-item ${c.id === activeCloneId ? 'va-clone-item--active' : ''}`}
              onClick={() => setActiveClone(c.id)}
            >
              <h4>{c.icon} {c.name}</h4>
              <span>{c.sourceType} · {c.status}</span>
              <button
                type="button"
                style={{ float: 'right', background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', padding: 0 }}
                onClick={(e) => { e.stopPropagation(); removeClone(c.id); }}
              >
                <Trash2 size={12} />
              </button>
            </div>
          ))}
        </aside>

        <div className="va-preview">
          {active?.previewUrl ? (
            <>
              <div style={{ marginBottom: 12, display: 'flex', gap: 8 }}>
                {(['website', 'mobile', 'desktop'] as PreviewViewportMode[]).map((v) => (
                  <button
                    key={v}
                    type="button"
                    className="va-btn va-btn--ghost"
                    onClick={() => updateClone(active.id, { viewport: v })}
                  >
                    {v}
                  </button>
                ))}
                <button type="button" className="va-btn va-btn--ghost" onClick={() => updateClone(active.id, { status: 'running' })}>
                  Start agent bridge
                </button>
              </div>
              <div style={{ height: 480, borderRadius: 8, overflow: 'hidden' }}>
                <PreviewFrame src={active.previewUrl} mode={active.viewport} title={active.name} />
              </div>
              <p style={{ fontSize: '0.75rem', color: '#78716c', marginTop: 8 }}>
                Scrape: {active.scrapeCapabilities.join(', ')}
              </p>
            </>
          ) : (
            <div className="va-empty">
              <p>Select or create a virtual app clone</p>
              <p style={{ fontSize: '0.8rem' }}>Agents can scrape UI tree, screenshots, and operate via terminal bridge</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
