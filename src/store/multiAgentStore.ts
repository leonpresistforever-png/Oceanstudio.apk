import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type {
  AgentCombo, AgentMember, AgentFusionConfig, MultiAgentSystemConfig, MultiAgentMode,
  WorkflowRunRecord, WorkflowPhaseRecord, AgentCategory,
} from '../agents/types';
import {
  createAgentCombo, createAgentMember, createDefaultStarterCombo,
  DEFAULT_SYSTEM_CONFIG, DEFAULT_ACCESS_CONFIG, DEFAULT_PARAM_FILTERS,
} from '../agents/types';
import { DEFAULT_CATEGORIES, createCategory } from '../agents/categories';

function migrateMember(m: AgentMember): AgentMember {
  return {
    ...m,
    archetype: m.archetype ?? 'custom',
    access: { ...DEFAULT_ACCESS_CONFIG, ...m.access },
    assignedMcpIds: m.assignedMcpIds ?? [],
    assignedPluginIds: m.assignedPluginIds ?? [],
    compatibility: m.compatibility ?? 'auto',
    paramFilters: { ...DEFAULT_PARAM_FILTERS, ...m.paramFilters },
    functionCalls: m.functionCalls ?? [],
    skillFiles: m.skillFiles ?? [],
  };
}

function migrateCombo(c: AgentCombo): AgentCombo {
  return { ...c, members: c.members.map(migrateMember) };
}

function buildDefaultFusionConfig(combo: AgentCombo): AgentFusionConfig {
  const enabled = combo.members.filter((m) => m.enabled);
  const memberIds = enabled.map((m) => m.id);
  const memberWeights: Record<string, number> = {};
  const weight = memberIds.length > 0 ? 1 / memberIds.length : 1;
  for (const m of enabled) memberWeights[m.id] = weight;
  return {
    strategy: 'weighted-vote',
    fusedName: `${combo.name} Fusion`,
    fusedSystemPrompt:
      'You are a fused agent combining multiple providers (Codex, Antigravity, Cursor, etc.). Union all capabilities and synthesize one coherent response.',
    memberIds,
    memberWeights,
    providerRouting: 'parallel-merge',
    primaryMemberId: combo.headAgentId || memberIds[0],
  };
}

interface MultiAgentState {
  combos: AgentCombo[];
  categories: AgentCategory[];
  activeCategoryId: string | null;
  activeComboId: string | null;
  multiAgentEnabled: boolean;
  systemConfig: MultiAgentSystemConfig;
  selectedMemberId: string | null;
  runHistory: WorkflowRunRecord[];
  activeRunId: string | null;

  setActiveCombo: (id: string | null) => void;
  setActiveCategory: (id: string | null) => void;
  setMultiAgentEnabled: (enabled: boolean) => void;
  setSelectedMember: (id: string | null) => void;
  setSystemConfig: (partial: Partial<MultiAgentSystemConfig>) => void;

  addCombo: (combo?: Partial<AgentCombo>) => string;
  updateCombo: (id: string, partial: Partial<AgentCombo>) => void;
  removeCombo: (id: string) => void;
  duplicateCombo: (id: string) => string;
  importCombo: (combo: AgentCombo) => string;

  addMember: (comboId: string, member: AgentMember) => void;
  updateMember: (comboId: string, memberId: string, partial: Partial<AgentMember>) => void;
  removeMember: (comboId: string, memberId: string) => void;
  setComboMode: (comboId: string, mode: MultiAgentMode) => void;
  updateFusionConfig: (comboId: string, partial: Partial<AgentFusionConfig>) => void;
  toggleFusionMember: (comboId: string, memberId: string, included: boolean) => void;
  setHeadAgent: (comboId: string, memberId: string) => void;

  addCategory: (partial?: Partial<AgentCategory>) => string;
  updateCategory: (id: string, partial: Partial<AgentCategory>) => void;
  removeCategory: (id: string) => void;

  startRun: (partial: Omit<WorkflowRunRecord, 'id' | 'startedAt' | 'status' | 'phases'> & { phases?: WorkflowPhaseRecord[] }) => string;
  updateRun: (id: string, partial: Partial<WorkflowRunRecord>) => void;
  addRunPhase: (runId: string, phase: WorkflowPhaseRecord) => void;
  updateRunPhase: (runId: string, phaseId: string, partial: Partial<WorkflowPhaseRecord>) => void;
  completeRun: (runId: string, status: 'completed' | 'failed') => void;
  clearRunHistory: () => void;

  getActiveCombo: () => AgentCombo | undefined;
  getAgentPayload: () => Record<string, unknown>;
}

export const useMultiAgentStore = create<MultiAgentState>()(
  persist(
    (set, get) => ({
      combos: [createDefaultStarterCombo()],
      categories: [...DEFAULT_CATEGORIES],
      activeCategoryId: DEFAULT_CATEGORIES[0]?.id ?? null,
      activeComboId: null,
      multiAgentEnabled: false,
      systemConfig: { ...DEFAULT_SYSTEM_CONFIG },
      selectedMemberId: null,
      runHistory: [],
      activeRunId: null,

      setActiveCombo: (id) => set({ activeComboId: id, selectedMemberId: null }),
      setActiveCategory: (id) => set({ activeCategoryId: id }),
      setMultiAgentEnabled: (enabled) => {
        const state = get();
        const activeComboId = state.activeComboId ?? state.combos[0]?.id ?? null;
        set({ multiAgentEnabled: enabled, activeComboId });
      },
      setSelectedMember: (id) => set({ selectedMemberId: id }),
      setSystemConfig: (partial) =>
        set((s) => ({ systemConfig: { ...s.systemConfig, ...partial } })),

      addCombo: (partial) => {
        const combo = createAgentCombo(partial);
        set((s) => ({ combos: [...s.combos, combo] }));
        return combo.id;
      },

      updateCombo: (id, partial) =>
        set((s) => ({
          combos: s.combos.map((c) =>
            c.id === id ? { ...c, ...partial, updatedAt: Date.now() } : c
          ),
        })),

      removeCombo: (id) =>
        set((s) => {
          if (s.combos.length <= 1) return s;
          return {
            combos: s.combos.filter((c) => c.id !== id),
            activeComboId: s.activeComboId === id ? (s.combos.find((c) => c.id !== id)?.id ?? null) : s.activeComboId,
          };
        }),

      duplicateCombo: (id) => {
        const src = get().combos.find((c) => c.id === id);
        if (!src) return '';
        const idMap = new Map<string, string>();
        const newMembers = src.members.map((m) => {
          const newId = crypto.randomUUID();
          idMap.set(m.id, newId);
          return { ...m, id: newId };
        });
        const copy = createAgentCombo({
          ...src,
          id: crypto.randomUUID(),
          name: `${src.name} (copy)`,
          members: newMembers,
          headAgentId: idMap.get(src.headAgentId) ?? newMembers[0]?.id ?? '',
        });
        set((s) => ({ combos: [...s.combos, copy] }));
        return copy.id;
      },

      importCombo: (combo) => {
        const idMap = new Map<string, string>();
        const members = combo.members.map((m) => {
          const newId = crypto.randomUUID();
          idMap.set(m.id, newId);
          return { ...m, id: newId };
        });
        const imported = createAgentCombo({
          ...combo,
          id: crypto.randomUUID(),
          name: combo.name,
          members,
          headAgentId: idMap.get(combo.headAgentId) ?? members.find((m) => m.role === 'head')?.id ?? members[0]?.id ?? '',
        });
        set((s) => ({ combos: [...s.combos, imported] }));
        return imported.id;
      },

      addMember: (comboId, member) =>
        set((s) => ({
          combos: s.combos.map((c) =>
            c.id === comboId
              ? { ...c, members: [...c.members, member], updatedAt: Date.now() }
              : c
          ),
        })),

      updateMember: (comboId, memberId, partial) =>
        set((s) => ({
          combos: s.combos.map((c) =>
            c.id === comboId
              ? {
                  ...c,
                  members: c.members.map((m) => (m.id === memberId ? { ...m, ...partial } : m)),
                  updatedAt: Date.now(),
                }
              : c
          ),
        })),

      removeMember: (comboId, memberId) =>
        set((s) => ({
          combos: s.combos.map((c) => {
            if (c.id !== comboId) return c;
            const members = c.members.filter((m) => m.id !== memberId);
            return {
              ...c,
              members,
              headAgentId: c.headAgentId === memberId ? (members[0]?.id ?? '') : c.headAgentId,
              updatedAt: Date.now(),
            };
          }),
          selectedMemberId: s.selectedMemberId === memberId ? null : s.selectedMemberId,
        })),

      setComboMode: (comboId, mode) =>
        set((s) => ({
          combos: s.combos.map((c) => {
            if (c.id !== comboId) return c;
            const next: AgentCombo = { ...c, mode, updatedAt: Date.now() };
            if (mode === 'fusion' && !c.fusionConfig) {
              next.fusionConfig = buildDefaultFusionConfig(c);
            }
            return next;
          }),
        })),

      updateFusionConfig: (comboId, partial) =>
        set((s) => ({
          combos: s.combos.map((c) => {
            if (c.id !== comboId) return c;
            const base = c.fusionConfig ?? buildDefaultFusionConfig(c);
            return {
              ...c,
              fusionConfig: { ...base, ...partial },
              updatedAt: Date.now(),
            };
          }),
        })),

      toggleFusionMember: (comboId, memberId, included) =>
        set((s) => ({
          combos: s.combos.map((c) => {
            if (c.id !== comboId) return c;
            const base = c.fusionConfig ?? buildDefaultFusionConfig(c);
            const memberIds = included
              ? Array.from(new Set([...base.memberIds, memberId]))
              : base.memberIds.filter((id) => id !== memberId);
            const memberWeights = { ...base.memberWeights };
            if (included && !(memberId in memberWeights)) {
              memberWeights[memberId] = 1 / Math.max(memberIds.length, 1);
            } else if (!included) {
              delete memberWeights[memberId];
            }
            return {
              ...c,
              fusionConfig: { ...base, memberIds, memberWeights },
              updatedAt: Date.now(),
            };
          }),
        })),

      setHeadAgent: (comboId, memberId) =>
        set((s) => ({
          combos: s.combos.map((c) =>
            c.id === comboId
              ? {
                  ...c,
                  headAgentId: memberId,
                  members: c.members.map((m) => ({
                    ...m,
                    role: m.id === memberId ? 'head' as const : m.role === 'head' ? 'worker' as const : m.role,
                  })),
                  updatedAt: Date.now(),
                }
              : c
          ),
        })),

      addCategory: (partial) => {
        const cat = createCategory(partial);
        set((s) => ({ categories: [...s.categories, cat] }));
        return cat.id;
      },

      updateCategory: (id, partial) =>
        set((s) => ({
          categories: s.categories.map((c) => (c.id === id ? { ...c, ...partial } : c)),
        })),

      removeCategory: (id) => {
        const { categories } = get();
        const cat = categories.find((c) => c.id === id);
        if (!cat || cat.builtIn) return;
        const next = categories.filter((c) => c.id !== id);
        set({
          categories: next,
          activeCategoryId: get().activeCategoryId === id ? next[0]?.id ?? null : get().activeCategoryId,
        });
      },

      startRun: (partial) => {
        const id = crypto.randomUUID();
        const record: WorkflowRunRecord = {
          id,
          startedAt: Date.now(),
          status: 'running',
          phases: partial.phases ?? [],
          ...partial,
        };
        set((s) => ({
          activeRunId: id,
          runHistory: [record, ...s.runHistory].slice(0, 50),
        }));
        return id;
      },

      updateRun: (id, partial) =>
        set((s) => ({
          runHistory: s.runHistory.map((r) => (r.id === id ? { ...r, ...partial } : r)),
        })),

      addRunPhase: (runId, phase) =>
        set((s) => ({
          runHistory: s.runHistory.map((r) =>
            r.id === runId ? { ...r, phases: [...r.phases, phase], currentPhase: phase.label } : r
          ),
        })),

      updateRunPhase: (runId, phaseId, partial) =>
        set((s) => ({
          runHistory: s.runHistory.map((r) =>
            r.id === runId
              ? {
                  ...r,
                  phases: r.phases.map((p) => (p.id === phaseId ? { ...p, ...partial } : p)),
                  currentPhase: partial.status === 'running' ? partial.label ?? r.currentPhase : r.currentPhase,
                }
              : r
          ),
        })),

      completeRun: (runId, status) =>
        set((s) => ({
          activeRunId: s.activeRunId === runId ? null : s.activeRunId,
          runHistory: s.runHistory.map((r) =>
            r.id === runId ? { ...r, status, completedAt: Date.now() } : r
          ),
        })),

      clearRunHistory: () => set({ runHistory: [], activeRunId: null }),

      getActiveCombo: () => {
        const { combos, activeComboId } = get();
        return combos.find((c) => c.id === activeComboId) ?? combos[0];
      },

      getAgentPayload: () => {
        const combo = get().getActiveCombo();
        const { multiAgentEnabled, systemConfig } = get();
        if (!multiAgentEnabled || !combo) {
          return { enabled: false };
        }
        const head = combo.members.find((m) => m.id === combo.headAgentId);
        const workers = combo.members.filter((m) => m.enabled && m.id !== combo.headAgentId);
        return {
          enabled: true,
          comboId: combo.id,
          comboName: combo.name,
          mode: combo.mode,
          fusionConfig: combo.fusionConfig,
          headAgent: head,
          workers,
          members: combo.members.filter((m) => m.enabled),
          sharedWorkspaceContext: combo.sharedWorkspaceContext,
          cloudSettings: combo.cloudSettings,
          systemConfig,
        };
      },
    }),
    {
      name: 'ocean-multi-agent-store',
      partialize: (s) => ({
        combos: s.combos,
        categories: s.categories,
        activeCategoryId: s.activeCategoryId ?? s.categories[0]?.id ?? null,
        activeComboId: s.activeComboId ?? s.combos[0]?.id ?? null,
        multiAgentEnabled: s.multiAgentEnabled,
        systemConfig: s.systemConfig,
        runHistory: s.runHistory.slice(0, 20),
      }),
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        state.combos = state.combos.map(migrateCombo);
        if (!state.categories?.length) state.categories = [...DEFAULT_CATEGORIES];
        if (!state.activeCategoryId && state.categories[0]) state.activeCategoryId = state.categories[0].id;
        if (!state.activeComboId && state.combos[0]) state.activeComboId = state.combos[0].id;
      },
    }
  )
);
