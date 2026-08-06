import { lazy, Suspense } from 'react';
import { useAppStore } from '../../store/appStore';
import CodeEditor from './CodeEditor';
import CodebaseView from './CodebaseView';
import PreviewPanel from './PreviewPanel';

const PluginsPage = lazy(() => import('../../pages/PluginsPage'));
const McpConnectorsPage = lazy(() => import('../../pages/McpConnectorsPage'));
const ProvidersPage = lazy(() => import('../../pages/ProvidersPage'));
const ConnectionCanvasPage = lazy(() => import('../../pages/ConnectionCanvasPage'));
const MultiAgentPage = lazy(() => import('../../pages/MultiAgentPage'));
const PlaygroundPage = lazy(() => import('../../pages/PlaygroundPage'));
const ProfileSettingsPage = lazy(() => import('../../pages/ProfileSettingsPage'));
const SkillsPage = lazy(() => import('../../pages/SkillsPage'));
const ExtensionsPage = lazy(() => import('../../pages/ExtensionsPage'));
const FallbackConfigPage = lazy(() => import('../../pages/FallbackConfigPage'));
const PromptEnhancerPage = lazy(() => import('../../pages/PromptEnhancerPage'));
const AgentWorkflowsPage = lazy(() => import('../../pages/AgentWorkflowsPage'));
const IntegrationsHubPage = lazy(() => import('../../pages/IntegrationsHubPage'));
const AgentFusionPage = lazy(() => import('../../pages/AgentFusionPage'));
const AgentStudioPage = lazy(() => import('../../pages/AgentStudioPage'));
const VirtualAppSpacePage = lazy(() => import('../../pages/VirtualAppSpacePage'));

function PanelLoader() {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      height: '100%', color: 'var(--text-tertiary)', fontSize: '0.875rem',
    }}>
      Loading…
    </div>
  );
}

export default function CenterPanel() {
  const centerView = useAppStore((s) => s.centerView);
  const setCenterView = useAppStore((s) => s.setCenterView);

  const tabs = [
    { id: 'editor' as const, label: 'Editor' },
    { id: 'codebase' as const, label: 'Codebase' },
    { id: 'preview' as const, label: 'Preview' },
    { id: 'plugins' as const, label: 'Plugins' },
  ];

  return (
    <main className="workspace-center">
      <div className="center-tabs">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`center-tab ${centerView === tab.id ? 'active' : ''}`}
            onClick={() => setCenterView(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="center-content">
        {centerView === 'editor' && <CodeEditor />}
        {centerView === 'codebase' && <CodebaseView />}
        {centerView === 'preview' && <PreviewPanel />}
        {centerView === 'plugins' && (
          <Suspense fallback={<PanelLoader />}><PluginsPage /></Suspense>
        )}
        {centerView === 'mcp' && (
          <Suspense fallback={<PanelLoader />}><McpConnectorsPage /></Suspense>
        )}
        {centerView === 'providers' && (
          <Suspense fallback={<PanelLoader />}><ProvidersPage /></Suspense>
        )}
        {centerView === 'connections' && (
          <Suspense fallback={<PanelLoader />}><ConnectionCanvasPage /></Suspense>
        )}
        {centerView === 'multiagent' && (
          <Suspense fallback={<PanelLoader />}><MultiAgentPage /></Suspense>
        )}
        {centerView === 'playground' && (
          <Suspense fallback={<PanelLoader />}><PlaygroundPage /></Suspense>
        )}
        {centerView === 'profile' && (
          <Suspense fallback={<PanelLoader />}><ProfileSettingsPage /></Suspense>
        )}
        {centerView === 'skills' && (
          <Suspense fallback={<PanelLoader />}><SkillsPage /></Suspense>
        )}
        {centerView === 'extensions' && (
          <Suspense fallback={<PanelLoader />}><ExtensionsPage /></Suspense>
        )}
        {centerView === 'fallback' && (
          <Suspense fallback={<PanelLoader />}><FallbackConfigPage /></Suspense>
        )}
        {centerView === 'prompt-enhancer' && (
          <Suspense fallback={<PanelLoader />}><PromptEnhancerPage /></Suspense>
        )}
        {centerView === 'workflows' && (
          <Suspense fallback={<PanelLoader />}><AgentWorkflowsPage /></Suspense>
        )}
        {centerView === 'integrations' && (
          <Suspense fallback={<PanelLoader />}><IntegrationsHubPage /></Suspense>
        )}
        {centerView === 'fusion' && (
          <Suspense fallback={<PanelLoader />}><AgentFusionPage /></Suspense>
        )}
        {centerView === 'studio' && (
          <Suspense fallback={<PanelLoader />}><AgentStudioPage /></Suspense>
        )}
        {centerView === 'virtual-apps' && (
          <Suspense fallback={<PanelLoader />}><VirtualAppSpacePage /></Suspense>
        )}
      </div>
    </main>
  );
}
