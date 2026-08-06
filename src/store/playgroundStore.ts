import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type {
  PlaygroundToolCategory,
  PlaygroundScheduledTask,
  PlaygroundMediaJob,
  PlaygroundAgentTask,
  MusicStudioConfig,
  ActiveBotSession,
  ActiveBotLogEntry,
  ActiveBotMood,
  PlaygroundTool,
} from '../playground/types';
import { DEFAULT_MUSIC_CONFIG } from '../playground/types';

interface PlaygroundState {
  activeCategory: PlaygroundToolCategory;
  activeToolId: string | null;
  searchQuery: string;
  agentTasks: PlaygroundAgentTask[];
  scheduledTasks: PlaygroundScheduledTask[];
  mediaJobs: PlaygroundMediaJob[];
  musicConfig: MusicStudioConfig;
  prompt: string;
  activeBotSession: ActiveBotSession | null;
  customMarketplaceTools: PlaygroundTool[];
  marketplaceFeatureFilter: PlaygroundToolCategory | 'all';
  selected3DSourceId: string | null;

  setActiveCategory: (cat: PlaygroundToolCategory) => void;
  setActiveTool: (id: string | null) => void;
  setSearchQuery: (q: string) => void;
  setPrompt: (p: string) => void;
  setMusicConfig: (patch: Partial<MusicStudioConfig>) => void;

  addAgentTask: (task: Omit<PlaygroundAgentTask, 'id' | 'createdAt' | 'status'>) => string;
  updateAgentTask: (id: string, patch: Partial<PlaygroundAgentTask>) => void;

  addScheduledTask: (task: Omit<PlaygroundScheduledTask, 'id' | 'createdAt' | 'status'>) => string;
  updateScheduledTask: (id: string, patch: Partial<PlaygroundScheduledTask>) => void;
  removeScheduledTask: (id: string) => void;

  addMediaJob: (job: Omit<PlaygroundMediaJob, 'id' | 'createdAt' | 'status'>) => string;
  updateMediaJob: (id: string, patch: Partial<PlaygroundMediaJob>) => void;

  setActiveBotSession: (session: ActiveBotSession | null) => void;
  updateActiveBotSession: (patch: Partial<ActiveBotSession>) => void;
  addActiveBotLog: (entry: Omit<ActiveBotLogEntry, 'id' | 'timestamp'>) => void;
  setActiveBotMood: (mood: ActiveBotMood) => void;
  acceptActiveBotRisk: () => void;
  setAccessibilityGranted: (granted: boolean) => void;
  addCustomMarketplaceTool: (tool: PlaygroundTool) => void;
  removeCustomMarketplaceTool: (id: string) => void;
  setMarketplaceFeatureFilter: (f: PlaygroundToolCategory | 'all') => void;
  setSelected3DSource: (id: string | null) => void;
}

export const usePlaygroundStore = create<PlaygroundState>()(
  persist(
    (set, get) => ({
      activeCategory: 'active-bot',
      activeToolId: null,
      searchQuery: '',
      agentTasks: [],
      scheduledTasks: [],
      mediaJobs: [],
      musicConfig: { ...DEFAULT_MUSIC_CONFIG },
      prompt: '',
      activeBotSession: null,
      customMarketplaceTools: [],
      marketplaceFeatureFilter: 'all',
      selected3DSourceId: null,

      setActiveCategory: (cat) => set({ activeCategory: cat, activeToolId: null }),
      setActiveTool: (id) => set({ activeToolId: id }),
      setSearchQuery: (q) => set({ searchQuery: q }),
      setPrompt: (p) => set({ prompt: p }),
      setMusicConfig: (patch) => set((s) => ({ musicConfig: { ...s.musicConfig, ...patch } })),

      addAgentTask: (partial) => {
        const id = crypto.randomUUID();
        const task: PlaygroundAgentTask = {
          id,
          status: 'pending',
          createdAt: Date.now(),
          ...partial,
        };
        set((s) => ({ agentTasks: [task, ...s.agentTasks].slice(0, 50) }));
        return id;
      },

      updateAgentTask: (id, patch) =>
        set((s) => ({
          agentTasks: s.agentTasks.map((t) => (t.id === id ? { ...t, ...patch } : t)),
        })),

      addScheduledTask: (partial) => {
        const id = crypto.randomUUID();
        const task: PlaygroundScheduledTask = {
          id,
          status: 'pending',
          createdAt: Date.now(),
          ...partial,
        };
        set((s) => ({ scheduledTasks: [task, ...s.scheduledTasks].slice(0, 30) }));
        return id;
      },

      updateScheduledTask: (id, patch) =>
        set((s) => ({
          scheduledTasks: s.scheduledTasks.map((t) => (t.id === id ? { ...t, ...patch } : t)),
        })),

      removeScheduledTask: (id) =>
        set((s) => ({ scheduledTasks: s.scheduledTasks.filter((t) => t.id !== id) })),

      addMediaJob: (partial) => {
        const id = crypto.randomUUID();
        const job: PlaygroundMediaJob = {
          id,
          status: 'queued',
          createdAt: Date.now(),
          ...partial,
        };
        set((s) => ({ mediaJobs: [job, ...s.mediaJobs].slice(0, 30) }));
        return id;
      },

      updateMediaJob: (id, patch) =>
        set((s) => ({
          mediaJobs: s.mediaJobs.map((j) => (j.id === id ? { ...j, ...patch } : j)),
        })),

      setActiveBotSession: (session) => set({ activeBotSession: session }),

      updateActiveBotSession: (patch) =>
        set((s) => ({
          activeBotSession: s.activeBotSession ? { ...s.activeBotSession, ...patch } : null,
        })),

      addActiveBotLog: (entry) =>
        set((s) => {
          if (!s.activeBotSession) return s;
          const log: ActiveBotLogEntry = {
            id: crypto.randomUUID(),
            timestamp: Date.now(),
            ...entry,
          };
          return {
            activeBotSession: {
              ...s.activeBotSession,
              activityLog: [log, ...s.activeBotSession.activityLog].slice(0, 100),
            },
          };
        }),

      setActiveBotMood: (mood) =>
        set((s) => ({
          activeBotSession: s.activeBotSession ? { ...s.activeBotSession, currentMood: mood } : null,
        })),

      acceptActiveBotRisk: () =>
        set((s) => ({
          activeBotSession: s.activeBotSession
            ? { ...s.activeBotSession, riskAccepted: true }
            : null,
        })),

      setAccessibilityGranted: (granted) =>
        set((s) => ({
          activeBotSession: s.activeBotSession
            ? { ...s.activeBotSession, accessibilityGranted: granted }
            : null,
        })),

      addCustomMarketplaceTool: (tool) =>
        set((s) => ({
          customMarketplaceTools: [tool, ...s.customMarketplaceTools].slice(0, 50),
        })),

      removeCustomMarketplaceTool: (id) =>
        set((s) => ({
          customMarketplaceTools: s.customMarketplaceTools.filter((t) => t.id !== id),
          activeToolId: s.activeToolId === id ? null : s.activeToolId,
        })),

      setMarketplaceFeatureFilter: (f) => set({ marketplaceFeatureFilter: f }),

      setSelected3DSource: (id) => set({ selected3DSourceId: id }),
    }),
    {
      name: 'ocean-playground',
      partialize: (s) => ({
        scheduledTasks: s.scheduledTasks,
        agentTasks: s.agentTasks.slice(0, 20),
        mediaJobs: s.mediaJobs.slice(0, 10),
        musicConfig: s.musicConfig,
        customMarketplaceTools: s.customMarketplaceTools,
        marketplaceFeatureFilter: s.marketplaceFeatureFilter,
        selected3DSourceId: s.selected3DSourceId,
        activeBotSession: s.activeBotSession
          ? {
              ...s.activeBotSession,
              status: s.activeBotSession.status === 'running' ? 'paused' : s.activeBotSession.status,
              activityLog: s.activeBotSession.activityLog.slice(0, 30),
            }
          : null,
      }),
    }
  )
);

/** Check and run due scheduled tasks — call from PlaygroundPage interval */
export function getDueScheduledTasks(): PlaygroundScheduledTask[] {
  const now = Date.now();
  return usePlaygroundStore.getState().scheduledTasks.filter(
    (t) => t.status === 'pending' && t.runAt <= now
  );
}

/** Prevent duplicate schedule execution */
export function isScheduleRunning(): boolean {
  return usePlaygroundStore.getState().scheduledTasks.some((t) => t.status === 'running');
}
