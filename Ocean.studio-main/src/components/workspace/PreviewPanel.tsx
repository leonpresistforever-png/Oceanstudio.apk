import { useState, useEffect } from 'react';
import { Maximize2, RefreshCw, ExternalLink, Cloud } from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';
import type { PreviewPortSource } from '../../lib/cloudShellPreview';
import { isCloudShellPreviewUrl } from '../../lib/cloudShellPreview';
import PreviewFrame from './PreviewFrame';
import PreviewViewportBar from './PreviewViewportBar';
import './PreviewPanel.css';

interface PortInfo {
  port: number;
  url: string;
  label?: string;
  source?: PreviewPortSource | string;
  openUrl?: string;
}

function portLabel(p: PortInfo): string {
  if (p.source === 'cloudshell' || isCloudShellPreviewUrl(p.url)) return `:${p.port} Cloud Shell`;
  return `:${p.port}`;
}

export default function PreviewPanel() {
  const [ports, setPorts] = useState<PortInfo[]>([]);
  const previewPort = useAppStore((s) => s.previewPort);
  const setPreviewPort = useAppStore((s) => s.setPreviewPort);
  const setPreviewUrl = useAppStore((s) => s.setPreviewUrl);
  const setPreviewFullscreen = useAppStore((s) => s.setPreviewFullscreen);
  const previewViewportMode = useAppStore((s) => s.previewViewportMode);
  const setPreviewViewportMode = useAppStore((s) => s.setPreviewViewportMode);

  function selectPort(p: PortInfo) {
    setPreviewPort(p.port);
    setPreviewUrl(p.url);
  }

  async function refreshPorts() {
    const api = getOceanAPI();
    const active = await api.preview.getPorts();
    setPorts(active);
    if (active.length > 0 && !previewPort) {
      selectPort(active[0]);
    }
  }

  useEffect(() => {
    refreshPorts();
    const api = getOceanAPI();
    const unsub = api.preview.onPortDetected((port: number, url: string) => {
      setPreviewPort(port);
      setPreviewUrl(url);
      refreshPorts();
    });
    return unsub;
  }, []);

  const activePort = previewPort || ports[0]?.port;
  const activePortInfo = ports.find((p) => p.port === activePort);
  const iframeSrc = activePortInfo?.url ?? (activePort ? `http://localhost:${activePort}` : null);
  const isCloudShell = activePortInfo?.source === 'cloudshell' || (iframeSrc ? isCloudShellPreviewUrl(iframeSrc) : false);
  const openUrl = activePortInfo?.openUrl ?? iframeSrc;

  async function handleOpenExternal() {
    if (!openUrl) return;
    await getOceanAPI().shell.openExternal(openUrl);
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '8px 16px', borderBottom: '1px solid var(--border-subtle)',
        background: 'var(--bg-tertiary)', flexWrap: 'wrap', gap: '8px',
      }}>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
          {ports.length > 0 ? (
            ports.map((p) => {
              const cloud = p.source === 'cloudshell' || isCloudShellPreviewUrl(p.url);
              return (
                <button
                  key={`${p.source ?? 'local'}-${p.port}`}
                  onClick={() => selectPort(p)}
                  style={{
                    padding: '4px 10px', fontSize: '0.75rem', borderRadius: '4px',
                    background: activePort === p.port ? 'var(--bg-secondary)' : 'transparent',
                    color: activePort === p.port ? 'var(--text-primary)' : 'var(--text-secondary)',
                    border: `1px solid ${cloud ? '#4285F4' : 'var(--border)'}`,
                    display: 'inline-flex', alignItems: 'center', gap: '4px',
                  }}
                >
                  {cloud && <Cloud size={11} color="#4285F4" />}
                  {portLabel(p)}
                </button>
              );
            })
          ) : (
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-tertiary)' }}>
              No active ports detected
            </span>
          )}
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {iframeSrc && (
            <PreviewViewportBar mode={previewViewportMode} onChange={setPreviewViewportMode} />
          )}
          <button onClick={refreshPorts} title="Refresh ports" style={{ padding: '4px' }}>
            <RefreshCw size={14} color="var(--text-secondary)" />
          </button>
          {openUrl && isCloudShell && (
            <button onClick={handleOpenExternal} title="Open in browser (Google Cloud Shell proxy)" style={{ padding: '4px' }}>
              <ExternalLink size={14} color="#4285F4" />
            </button>
          )}
          {activePort && (
            <button onClick={() => setPreviewFullscreen(true)} title="Fullscreen" style={{ padding: '4px' }}>
              <Maximize2 size={14} color="var(--text-secondary)" />
            </button>
          )}
        </div>
      </div>

      {isCloudShell && (
        <div style={{
          padding: '6px 16px', fontSize: '0.75rem', background: '#1a3a5c',
          color: '#93c5fd', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px',
        }}>
          <span>Cloud Shell sandbox — preview via Google HTTPS proxy</span>
          {openUrl && (
            <button
              onClick={handleOpenExternal}
              style={{ fontSize: '0.75rem', color: '#93c5fd', textDecoration: 'underline', whiteSpace: 'nowrap' }}
            >
              Open in browser
            </button>
          )}
        </div>
      )}

      {iframeSrc ? (
        <PreviewFrame
          src={iframeSrc}
          title={`Preview port ${activePort}`}
          mode={previewViewportMode}
        />
      ) : (
        <div style={{
          flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center',
          flexDirection: 'column', gap: '8px', color: 'var(--text-tertiary)',
        }}>
          <p style={{ fontSize: '0.875rem' }}>Start a dev server in the terminal to see a live preview</p>
          <p style={{ fontSize: '0.75rem' }}>Switch viewport: Website · Mobile · Desktop virtual screen</p>
        </div>
      )}
    </div>
  );
}
