import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ProviderConnection } from '../providers/types';
import {
  getActiveProviderIds, setActiveProviderIds, toggleActiveProvider,
  addActiveProvider, removeActiveProvider,
} from '../providers/activeProviders';

interface ProviderState {
  activeProviderIds: string[];
  proxyRunning: boolean;
  proxyUrl: string;
  proxyLanUrl: string;
  toggleActiveProvider: (id: string) => void;
  setActiveProviders: (ids: string[]) => void;
  addActiveProvider: (id: string) => void;
  removeActiveProvider: (id: string) => void;
  setProxyStatus: (running: boolean, url?: string, lanUrl?: string) => void;
  applyProviderModels: (conn: ProviderConnection) => void;
}

export const useProviderStore = create<ProviderState>()(
  persist(
    (set, get) => ({
      activeProviderIds: getActiveProviderIds(),
      proxyRunning: false,
      proxyUrl: 'http://localhost:20128',
      proxyLanUrl: 'http://localhost:20128',

      toggleActiveProvider: (id) => {
        const next = toggleActiveProvider(id);
        set({ activeProviderIds: next });
      },

      setActiveProviders: (ids) => {
        setActiveProviderIds(ids);
        set({ activeProviderIds: ids });
      },

      addActiveProvider: (id) => {
        const next = addActiveProvider(id);
        set({ activeProviderIds: next });
      },

      removeActiveProvider: (id) => {
        const next = removeActiveProvider(id);
        set({ activeProviderIds: next });
      },

      setProxyStatus: (running, url, lanUrl) => {
        set({
          proxyRunning: running,
          proxyUrl: url ?? get().proxyUrl,
          proxyLanUrl: lanUrl ?? url ?? get().proxyLanUrl,
        });
      },

      applyProviderModels: (conn) => {
        if (conn.status !== 'connected' || !conn.syncedModels?.length) return;
        import('../store/modelStore').then(({ useModelStore }) => {
          const store = useModelStore.getState();
          for (const modelId of conn.syncedModels ?? []) {
            store.addCustomModel({
              id: `${conn.providerId}.${modelId}`,
              name: modelId,
              provider: 'custom',
              isCustom: true,
              description: `From ${conn.name}`,
            });
          }
          if (conn.syncedModels?.[0]) {
            store.setSelectedModel(`${conn.providerId}.${conn.syncedModels[0]}`);
          }
        });
      },
    }),
    { name: 'ocean-provider-store', partialize: (s) => ({ activeProviderIds: s.activeProviderIds }) }
  )
);

/** @deprecated use activeProviderIds */
export function useActiveProviderId(): string | null {
  return useProviderStore((s) => s.activeProviderIds[0] ?? null);
}
