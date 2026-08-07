import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from 'firebase/auth';
import type { PreviewViewportMode } from '../lib/previewViewport';

export type AgentMode = 'review' | 'auto' | 'bypass';
export type UITemplate = 'minimal' | 'classic' | 'contrast' | 'warm';
export type Language = 'en' | 'es' | 'fr' | 'de' | 'ja' | 'zh';

export interface WorkspaceConfig {
  template: UITemplate;
  language: Language;
  agentMode: AgentMode;
  workspacePath: string;
}

export interface AgentLogEntry {
  id: string;
  type: 'thought' | 'action' | 'command' | 'todo' | 'result' | 'error' | 'status' | 'user';
  timestamp: number;
  summary: string;
  detail?: string;
  duration?: number;
  status?: 'running' | 'completed' | 'failed' | 'paused';
  expanded?: boolean;
  metadata?: Record<string, unknown>;
  feedback?: 'up' | 'down';
  taggedForQuestion?: boolean;
}

interface AppState {
  user: User | null;
  setUser: (user: User | null) => void;
  workspaceConfig: WorkspaceConfig | null;
  setWorkspaceConfig: (config: WorkspaceConfig) => void;
  terminalRequestId: number;
  requestTerminal: () => void;
  workspacePath: string;
  setWorkspacePath: (path: string) => void;
  activeFile: string | null;
  setActiveFile: (path: string | null) => void;
  centerView: 'editor' | 'codebase' | 'preview' | 'plugins' | 'mcp' | 'providers' | 'connections' | 'multiagent' | 'playground' | 'profile' | 'skills' | 'extensions' | 'fallback' | 'prompt-enhancer' | 'workflows' | 'integrations' | 'fusion' | 'studio' | 'virtual-apps';
  setCenterView: (view: 'editor' | 'codebase' | 'preview' | 'plugins' | 'mcp' | 'providers' | 'connections' | 'multiagent' | 'playground' | 'profile' | 'skills' | 'extensions' | 'fallback' | 'prompt-enhancer' | 'workflows' | 'integrations' | 'fusion' | 'studio' | 'virtual-apps') => void;
  previewPort: number | null;
  setPreviewPort: (port: number | null) => void;
  previewUrl: string | null;
  setPreviewUrl: (url: string | null) => void;
  previewFullscreen: boolean;
  setPreviewFullscreen: (fs: boolean) => void;
  previewViewportMode: PreviewViewportMode;
  setPreviewViewportMode: (mode: PreviewViewportMode) => void;
  agentLogs: AgentLogEntry[];
  addAgentLog: (entry: AgentLogEntry) => void;
  updateAgentLog: (id: string, updates: Partial<AgentLogEntry>) => void;
  clearAgentLogs: () => void;
  agentRunning: boolean;
  setAgentRunning: (running: boolean) => void;
  sidebarExpanded: Record<string, boolean>;
  toggleSidebarSection: (section: string) => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      user: null,
      setUser: (user) => set({ user }),
      workspaceConfig: null,
      setWorkspaceConfig: (config) => set({ workspaceConfig: config, workspacePath: config.workspacePath }),
      workspacePath: '',
      setWorkspacePath: (path) => set((s) => {
        if (s.workspacePath === path) return {};
        return {
          workspacePath: path,
          workspaceConfig: s.workspaceConfig ? { ...s.workspaceConfig, workspacePath: path } : s.workspaceConfig,
        };
      }),
      terminalRequestId: 0,
      requestTerminal: () => set((s) => ({ terminalRequestId: s.terminalRequestId + 1 })),
      activeFile: null,
      setActiveFile: (path) => set({ activeFile: path }),
      centerView: 'editor',
      setCenterView: (view) => set({ centerView: view }),
      previewPort: null,
      setPreviewPort: (port) => set({ previewPort: port }),
      previewUrl: null,
      setPreviewUrl: (url) => set({ previewUrl: url }),
      previewFullscreen: false,
      setPreviewFullscreen: (fs) => set({ previewFullscreen: fs }),
      previewViewportMode: 'website' as PreviewViewportMode,
      setPreviewViewportMode: (mode) => set({ previewViewportMode: mode }),
      agentLogs: [],
      addAgentLog: (entry) => set((s) => ({ agentLogs: [...s.agentLogs, entry] })),
      updateAgentLog: (id, updates) =>
        set((s) => ({
          agentLogs: s.agentLogs.map((l) => (l.id === id ? { ...l, ...updates } : l)),
        })),
      clearAgentLogs: () => set({ agentLogs: [] }),
      agentRunning: false,
      setAgentRunning: (running) => set({ agentRunning: running }),
      sidebarExpanded: { explorer: true, tools: false, settings: false },
      toggleSidebarSection: (section) =>
        set((s) => ({
          sidebarExpanded: { ...s.sidebarExpanded, [section]: !s.sidebarExpanded[section] },
        })),
    }),
    {
      name: 'ocean-studio-storage',
      partialize: (state) => ({
        workspaceConfig: state.workspaceConfig,
        workspacePath: state.workspacePath,
        sidebarExpanded: state.sidebarExpanded,
        previewViewportMode: state.previewViewportMode,
      }),
    }
  )
);
