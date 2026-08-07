import { ChevronRight, FolderOpen, Settings, Home, Puzzle, Plug, Webhook, Terminal, Monitor, Layers, Share2, Users, Sparkles, BookOpen, Box, Shield, Workflow, GitMerge, Cpu } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAppStore } from '../../store/appStore';
import FileTree from './FileTree';
import SidebarUserFooter from './SidebarUserFooter';
import './LeftSidebar.css';

export default function LeftSidebar() {
  const navigate = useNavigate();
  const sidebarExpanded = useAppStore((s) => s.sidebarExpanded);
  const toggleSidebarSection = useAppStore((s) => s.toggleSidebarSection);
  const workspacePath = useAppStore((s) => s.workspacePath);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);
  const setCenterView = useAppStore((s) => s.setCenterView);
  const requestTerminal = useAppStore((s) => s.requestTerminal);

  return (
    <aside className="workspace-left sidebar">
      <div className="sidebar-header">
        <img src="/ocean-icon.svg" alt="" />
        <span>Ocean.studio</span>
      </div>

      <div className="sidebar-scroll">
        <div className="sidebar-section">
          <div className="sidebar-section-header" onClick={() => toggleSidebarSection('explorer')}>
            <span>Explorer</span>
            <ChevronRight
              size={14}
              style={{ transform: sidebarExpanded.explorer ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }}
            />
          </div>
          {sidebarExpanded.explorer && (
            <div className="sidebar-section-content">
              {workspacePath ? (
                <FileTree rootPath={workspacePath} />
              ) : (
                <div className="sidebar-item">
                  <FolderOpen size={14} />
                  No workspace open
                </div>
              )}
            </div>
          )}
        </div>

        <div className="sidebar-section">
          <div className="sidebar-section-header" onClick={() => toggleSidebarSection('tools')}>
            <span>Tools</span>
            <ChevronRight
              size={14}
              style={{ transform: sidebarExpanded.tools ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }}
            />
          </div>
          {sidebarExpanded.tools && (
            <div className="sidebar-section-content">
              <div className="sidebar-item" onClick={() => setCenterView('providers')}>
                <Layers size={14} />
                Providers
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('fallback')}>
                <Shield size={14} />
                Fallback Models
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('integrations')}>
                <Plug size={14} />
                Integrations Hub
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('fusion')}>
                <GitMerge size={14} />
                Agent Fusion
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('studio')}>
                <Cpu size={14} />
                Agent Studio
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('virtual-apps')}>
                <Box size={14} />
                Virtual App Space
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('workflows')}>
                <Workflow size={14} />
                Agent Workflows
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('prompt-enhancer')}>
                <Sparkles size={14} />
                Prompt Enhancer
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('connections')}>
                <Share2 size={14} />
                Connections
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('multiagent')}>
                <Users size={14} />
                Multi Agent
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('playground')}>
                <Sparkles size={14} />
                Playground
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('plugins')}>
                <Puzzle size={14} />
                Plugins
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('extensions')}>
                <Box size={14} />
                Extensions
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('mcp')}>
                <Plug size={14} />
                MCP Connectors
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('skills')}>
                <BookOpen size={14} />
                Skills Store
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('editor')}>
                <Webhook size={14} />
                Webhooks
              </div>
              <div className="sidebar-item" onClick={() => requestTerminal()}>
                <Terminal size={14} />
                Terminal
              </div>
              <div className="sidebar-item" onClick={() => setCenterView('preview')}>
                <Monitor size={14} />
                Port Preview
              </div>
            </div>
          )}
        </div>

        <div className="sidebar-section">
          <div className="sidebar-section-header" onClick={() => toggleSidebarSection('settings')}>
            <span>Settings</span>
            <ChevronRight
              size={14}
              style={{ transform: sidebarExpanded.settings ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s' }}
            />
          </div>
          {sidebarExpanded.settings && (
            <div className="sidebar-section-content">
              <div className="sidebar-item" onClick={() => navigate('/')}>
                <Home size={14} />
                Back to home
              </div>
              <div className="sidebar-item">
                <Settings size={14} />
                Agent: {workspaceConfig?.agentMode || 'review'}
              </div>
            </div>
          )}
        </div>
      </div>

      <SidebarUserFooter />
    </aside>
  );
}
