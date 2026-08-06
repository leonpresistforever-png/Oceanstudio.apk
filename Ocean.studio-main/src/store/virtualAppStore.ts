import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { createVirtualAppInstance, type VirtualAppInstance, type VirtualAppSourceType } from '../virtual-apps/types';

interface VirtualAppState {
  clones: VirtualAppInstance[];
  activeCloneId: string | null;
  addClone: (partial: Partial<VirtualAppInstance> & Pick<VirtualAppInstance, 'name' | 'sourceType'>) => string;
  updateClone: (id: string, partial: Partial<VirtualAppInstance>) => void;
  removeClone: (id: string) => void;
  setActiveClone: (id: string | null) => void;
  getActiveClone: () => VirtualAppInstance | undefined;
  /** Scaffold: register clone from exe/apk path */
  cloneFromBinary: (name: string, sourcePath: string, type: 'exe' | 'apk') => Promise<string>;
  cloneFromUrl: (name: string, url: string) => string;
  cloneFromPort: (name: string, port: number) => string;
}

export const useVirtualAppStore = create<VirtualAppState>()(
  persist(
    (set, get) => ({
      clones: [],
      activeCloneId: null,

      addClone: (partial) => {
        const instance = createVirtualAppInstance(partial);
        set((s) => ({ clones: [instance, ...s.clones], activeCloneId: instance.id }));
        return instance.id;
      },

      updateClone: (id, partial) =>
        set((s) => ({
          clones: s.clones.map((c) => (c.id === id ? { ...c, ...partial, lastUsedAt: Date.now() } : c)),
        })),

      removeClone: (id) =>
        set((s) => ({
          clones: s.clones.filter((c) => c.id !== id),
          activeCloneId: s.activeCloneId === id ? null : s.activeCloneId,
        })),

      setActiveClone: (id) => set({ activeCloneId: id }),

      getActiveClone: () => {
        const { clones, activeCloneId } = get();
        return clones.find((c) => c.id === activeCloneId);
      },

      cloneFromBinary: async (name, sourcePath, type) => {
        const id = get().addClone({
          name,
          sourceType: type,
          status: 'cloning',
          icon: type === 'apk' ? '📱' : '🖥️',
          manifest: { sourcePath, workspaceSubpath: `virtual-apps/${name.replace(/\s+/g, '-').toLowerCase()}` },
          viewport: type === 'apk' ? 'mobile' : 'desktop',
        });
        // Scaffold: mark ready — real clone would use sandbox/container in Electron
        setTimeout(() => {
          get().updateClone(id, {
            status: 'ready',
            previewUrl: type === 'apk' ? undefined : `http://localhost:9222`,
            scrapeCapabilities: ['window-title', 'ui-tree', 'screenshot', 'agent-terminal-bridge'],
          });
        }, 800);
        return id;
      },

      cloneFromUrl: (name, url) =>
        get().addClone({
          name,
          sourceType: 'url',
          status: 'ready',
          icon: '🌐',
          manifest: { sourceUrl: url },
          previewUrl: url,
          viewport: 'website',
        }),

      cloneFromPort: (name, port) =>
        get().addClone({
          name,
          sourceType: 'port',
          status: 'ready',
          icon: '🔌',
          manifest: { port },
          previewUrl: `http://localhost:${port}`,
          viewport: 'desktop',
        }),
    }),
    {
      name: 'ocean-virtual-apps',
      partialize: (s) => ({ clones: s.clones, activeCloneId: s.activeCloneId }),
    }
  )
);
