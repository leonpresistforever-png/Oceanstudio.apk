import { useEffect, useState } from 'react';
import { useAppStore } from '../store/appStore';
import { getOceanAPI } from '../lib/platform';
import { isCloudShellPreviewUrl } from '../lib/cloudShellPreview';
import { useIsMobileApp } from '../hooks/useIsMobileApp';
import PreviewFrame from '../components/workspace/PreviewFrame';
import PreviewViewportBar from '../components/workspace/PreviewViewportBar';
import LeftSidebar from '../components/workspace/LeftSidebar';
import CenterPanel from '../components/workspace/CenterPanel';
import RightSidebar from '../components/workspace/RightSidebar';
import IntegrationsBar from '../components/workspace/IntegrationsBar';
import MobileWorkspaceNav, { type MobilePanel } from '../components/workspace/MobileWorkspaceNav';
import TerminalPanel from '../components/workspace/TerminalPanel';
import './Workspace.css';
import '../components/workspace/PreviewPanel.css';
import '../components/workspace/MobileWorkspaceNav.css';

export default function Workspace() {
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);
  const setWorkspacePath = useAppStore((s) => s.setWorkspacePath);
  const setCenterView = useAppStore((s) => s.setCenterView);
  const previewFullscreen = useAppStore((s) => s.previewFullscreen);
  const previewPort = useAppStore((s) => s.previewPort);
  const previewUrl = useAppStore((s) => s.previewUrl);
  const previewViewportMode = useAppStore((s) => s.previewViewportMode);
  const setPreviewFullscreen = useAppStore((s) => s.setPreviewFullscreen);
  const setPreviewViewportMode = useAppStore((s) => s.setPreviewViewportMode);
  const setPreviewPort = useAppStore((s) => s.setPreviewPort);
  const setPreviewUrl = useAppStore((s) => s.setPreviewUrl);
  const addAgentLog = useAppStore((s) => s.addAgentLog);
  const isMobile = useIsMobileApp();
  const [mobilePanel, setMobilePanel] = useState<MobilePanel>('agent');

  useEffect(() => {
    if (workspaceConfig?.workspacePath) {
      setWorkspacePath(workspaceConfig.workspacePath);
    }
    if (workspaceConfig?.template) {
      document.documentElement.setAttribute('data-template', workspaceConfig.template);
    }
  }, [workspaceConfig, setWorkspacePath]);

  useEffect(() => {
    async function initWorkspacePath() {
      const api = getOceanAPI();
      const info = await api.platform.get();
      if (!info.isAndroid) return;
      const defaultPath = await api.fs.getDefaultWorkspace();
      if (defaultPath) setWorkspacePath(defaultPath);
    }
    void initWorkspacePath();
  }, [setWorkspacePath]);

  useEffect(() => {
    if (!isMobile) return;
    if (mobilePanel === 'editor') setCenterView('editor');
    if (mobilePanel === 'tools') setCenterView('integrations');
  }, [isMobile, mobilePanel, setCenterView]);

  useEffect(() => {
    const api = getOceanAPI();
    const unsub = api.preview.onPortDetected((port: number, url: string, meta) => {
      setPreviewPort(port);
      setPreviewUrl(url);
      const label = meta?.source === 'cloudshell' ? 'Cloud Shell' : 'local';
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Port ${port} detected (${label}) — preview available`,
        detail: meta?.openUrl ?? url,
        status: 'completed',
      });
    });
    return unsub;
  }, [setPreviewPort, setPreviewUrl, addAgentLog]);

  const iframeSrc = previewUrl ?? (previewPort ? `http://localhost:${previewPort}` : null);
  const isCloudShell = iframeSrc ? isCloudShellPreviewUrl(iframeSrc) : false;
  const previewLabel = isCloudShell
    ? `Cloud Shell :${previewPort}`
    : `localhost:${previewPort}`;

  const workspaceClass = [
    'workspace',
    isMobile ? 'workspace--mobile' : '',
    isMobile ? `panel-${mobilePanel}` : '',
  ].filter(Boolean).join(' ');

  return (
    <div className={workspaceClass}>
      {isMobile && mobilePanel === 'terminal' ? (
        <div className="mobile-terminal-fullscreen">
          <TerminalPanel
            type="native"
            onClose={() => setMobilePanel('agent')}
          />
        </div>
      ) : (
        <>
          <LeftSidebar />
          <CenterPanel />
          <RightSidebar />
        </>
      )}
      <IntegrationsBar />

      {isMobile && (
        <MobileWorkspaceNav active={mobilePanel} onChange={setMobilePanel} />
      )}

      {previewFullscreen && previewPort && iframeSrc && (
        <div className="preview-fullscreen">
          <div className="preview-fullscreen-bar">
            <span>Preview — {previewLabel}</span>
            <div className="preview-fullscreen-bar-actions">
              <PreviewViewportBar
                mode={previewViewportMode}
                onChange={setPreviewViewportMode}
                compact
              />
              {isCloudShell && (
                <button
                  className="preview-exit-btn"
                  onClick={() => getOceanAPI().shell.openExternal(iframeSrc)}
                >
                  Open in browser
                </button>
              )}
              <button className="preview-exit-btn" onClick={() => setPreviewFullscreen(false)}>
                Exit preview
              </button>
            </div>
          </div>
          <div className="preview-fullscreen-body">
            <PreviewFrame
              src={iframeSrc}
              title="Preview"
              mode={previewViewportMode}
              className="preview-frame--fullscreen"
            />
          </div>
        </div>
      )}
    </div>
  );
}
