import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import {
  type FallbackConfig,
  type FallbackModelEntry,
  type FallbackTrigger,
  type FallbackDistribution,
  DEFAULT_FALLBACK_CONFIG,
} from '../fallback/types';
import { createBuiltinFallbackChain } from '../fallback/catalog';
import { upsertProviderConnection } from '../providers/registry';

interface FallbackState {
  config: FallbackConfig;
  setEnabled: (enabled: boolean) => void;
  setApplyToSubagents: (v: boolean) => void;
  setTriggers: (triggers: FallbackTrigger[]) => void;
  setDistribution: (mode: FallbackDistribution) => void;
  setHuggingFaceToken: (token: string) => void;
  setNvidiaApiKey: (key: string) => void;
  resetBuiltinChain: () => void;
  toggleModel: (id: string, enabled: boolean) => void;
  addCustomModel: (entry: Omit<FallbackModelEntry, 'id' | 'source'>) => void;
  removeModel: (id: string) => void;
  reorderModel: (id: string, direction: 'up' | 'down') => void;
  recordUsage: (modelId: string) => void;
  getEnabledChain: () => FallbackModelEntry[];
  getAgentPayload: () => Record<string, unknown>;
  syncProviderAuth: () => void;
}

function ensureChain(chain: FallbackModelEntry[]): FallbackModelEntry[] {
  return chain.length ? chain : createBuiltinFallbackChain();
}

export const useFallbackStore = create<FallbackState>()(
  persist(
    (set, get) => ({
      config: {
        ...DEFAULT_FALLBACK_CONFIG,
        chain: createBuiltinFallbackChain(),
      },

      setEnabled: (enabled) =>
        set((s) => ({ config: { ...s.config, enabled } })),

      setApplyToSubagents: (applyToSubagents) =>
        set((s) => ({ config: { ...s.config, applyToSubagents } })),

      setTriggers: (triggers) =>
        set((s) => ({ config: { ...s.config, triggers } })),

      setDistribution: (distribution) =>
        set((s) => ({ config: { ...s.config, distribution } })),

      setHuggingFaceToken: (token) => {
        set((s) => ({
          config: { ...s.config, auth: { ...s.config.auth, huggingfaceToken: token } },
        }));
        get().syncProviderAuth();
      },

      setNvidiaApiKey: (key) => {
        set((s) => ({
          config: { ...s.config, auth: { ...s.config.auth, nvidiaApiKey: key } },
        }));
        get().syncProviderAuth();
      },

      resetBuiltinChain: () =>
        set((s) => ({ config: { ...s.config, chain: createBuiltinFallbackChain() } })),

      toggleModel: (id, enabled) =>
        set((s) => ({
          config: {
            ...s.config,
            chain: s.config.chain.map((m) => (m.id === id ? { ...m, enabled } : m)),
          },
        })),

      addCustomModel: (entry) => {
        const model: FallbackModelEntry = {
          ...entry,
          id: `custom-${crypto.randomUUID().slice(0, 8)}`,
          source: 'user',
        };
        set((s) => ({ config: { ...s.config, chain: [...s.config.chain, model] } }));
      },

      removeModel: (id) =>
        set((s) => ({
          config: { ...s.config, chain: s.config.chain.filter((m) => m.id !== id || m.source === 'builtin') },
        })),

      reorderModel: (id, direction) =>
        set((s) => {
          const chain = [...s.config.chain];
          const idx = chain.findIndex((m) => m.id === id);
          if (idx < 0) return s;
          const swap = direction === 'up' ? idx - 1 : idx + 1;
          if (swap < 0 || swap >= chain.length) return s;
          [chain[idx], chain[swap]] = [chain[swap], chain[idx]];
          return { config: { ...s.config, chain } };
        }),

      recordUsage: (modelId) =>
        set((s) => ({
          config: {
            ...s.config,
            usageCounts: {
              ...s.config.usageCounts,
              [modelId]: (s.config.usageCounts[modelId] ?? 0) + 1,
            },
            lastFallbackAt: Date.now(),
            lastFallbackModelId: modelId,
          },
        })),

      getEnabledChain: () => {
        const { config } = get();
        return ensureChain(config.chain).filter((m) => m.enabled);
      },

      getAgentPayload: () => {
        const { config } = get();
        const chain = get().getEnabledChain();
        return {
          enabled: config.enabled,
          applyToSubagents: config.applyToSubagents,
          triggers: config.triggers,
          distribution: config.distribution,
          chainCount: chain.length,
          chain: chain.map((m) => ({
            providerId: m.providerId,
            modelId: m.modelId,
            displayName: m.displayName,
            tier: m.tier,
          })),
          hasHuggingFaceAuth: !!config.auth.huggingfaceToken,
          hasNvidiaAuth: !!config.auth.nvidiaApiKey,
          lastFallbackModelId: config.lastFallbackModelId,
        };
      },

      syncProviderAuth: () => {
        const { auth } = get().config;
        if (auth.huggingfaceToken) {
          upsertProviderConnection({
            providerId: 'huggingface',
            name: 'Hugging Face',
            status: 'connected',
            config: { HF_TOKEN: auth.huggingfaceToken },
            connectedAt: Date.now(),
            accountLabel: 'HF Fallback',
            syncedModels: createBuiltinFallbackChain()
              .filter((m) => m.providerId === 'huggingface')
              .map((m) => m.modelId),
          });
        }
        if (auth.nvidiaApiKey) {
          upsertProviderConnection({
            providerId: 'nvidia',
            name: 'NVIDIA NIM',
            status: 'connected',
            config: { NVIDIA_API_KEY: auth.nvidiaApiKey },
            connectedAt: Date.now(),
            accountLabel: 'NIM Backup',
            syncedModels: createBuiltinFallbackChain()
              .filter((m) => m.providerId === 'nvidia')
              .map((m) => m.modelId),
          });
        }
      },
    }),
    {
      name: 'ocean-fallback-config',
      partialize: (s) => ({ config: s.config }),
      merge: (persisted, current) => {
        const p = persisted as Partial<typeof current> | undefined;
        const mergedConfig = { ...DEFAULT_FALLBACK_CONFIG, ...p?.config };
        if (!mergedConfig.chain?.length) mergedConfig.chain = createBuiltinFallbackChain();
        return { ...current, ...p, config: mergedConfig };
      },
      onRehydrateStorage: () => (state) => {
        if (state?.config.auth.huggingfaceToken || state?.config.auth.nvidiaApiKey) {
          state.syncProviderAuth();
        }
      },
    }
  )
);
