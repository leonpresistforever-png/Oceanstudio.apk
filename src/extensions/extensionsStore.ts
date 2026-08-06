import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ExtensionDefinition, InstalledExtension } from './types';
import { BUILTIN_EXTENSIONS } from './builtinCatalog';

export const MAX_INSTALLED_EXTENSIONS = 150;

export type ExtensionInstallResult = { ok: true } | { ok: false; reason: 'duplicate' | 'cap' };

interface ExtensionsState {
  installed: InstalledExtension[];
  marketplace: ExtensionDefinition[];
  marketplaceLoaded: boolean;
  marketplaceLoading: boolean;

  installExtension: (ext: ExtensionDefinition) => ExtensionInstallResult;
  uninstallExtension: (id: string) => void;
  toggleExtension: (id: string, enabled: boolean) => void;
  addCustomExtension: (ext: ExtensionDefinition) => ExtensionInstallResult;
  addAgentExtension: (name: string, guide: string) => ExtensionInstallResult;
  setMarketplace: (items: ExtensionDefinition[]) => void;
  setMarketplaceLoading: (v: boolean) => void;
  setMarketplaceLoaded: (v: boolean) => void;
  getEnabled: () => InstalledExtension[];
}

export const useExtensionsStore = create<ExtensionsState>()(
  persist(
    (set, get) => ({
      installed: [],
      marketplace: [],
      marketplaceLoaded: false,
      marketplaceLoading: false,

      installExtension: (ext) => {
        const s = get();
        if (s.installed.some((x) => x.id === ext.id)) return { ok: false, reason: 'duplicate' };
        if (s.installed.length >= MAX_INSTALLED_EXTENSIONS) return { ok: false, reason: 'cap' };
        const entry: InstalledExtension = {
          ...ext,
          enabled: true,
          installedAt: Date.now(),
        };
        set({ installed: [entry, ...s.installed] });
        return { ok: true };
      },

      uninstallExtension: (id) =>
        set((s) => ({ installed: s.installed.filter((x) => x.id !== id) })),

      toggleExtension: (id, enabled) =>
        set((s) => ({
          installed: s.installed.map((x) => (x.id === id ? { ...x, enabled } : x)),
        })),

      addCustomExtension: (ext) => get().installExtension({ ...ext, source: 'custom' }),

      addAgentExtension: (name, guide) =>
        get().installExtension({
          id: `agent.${crypto.randomUUID()}`,
          name: name.toLowerCase().replace(/\s+/g, '-'),
          displayName: name,
          description: `Agent-created extension: ${name}`,
          version: '1.0.0',
          publisher: 'agent',
          category: 'other',
          platforms: ['electron', 'web', 'mobile'],
          installType: 'custom',
          source: 'agent',
          agentGuide: guide,
          tags: ['agent-created'],
        }),

      setMarketplace: (items) => set({ marketplace: items }),
      setMarketplaceLoading: (v) => set({ marketplaceLoading: v }),
      setMarketplaceLoaded: (v) => set({ marketplaceLoaded: v }),

      getEnabled: () => get().installed.filter((x) => x.enabled),
    }),
    {
      name: 'ocean-extensions-store',
      partialize: (s) => ({ installed: s.installed }),
    }
  )
);

export function getBuiltinExtension(id: string): ExtensionDefinition | undefined {
  return BUILTIN_EXTENSIONS.find((e) => e.id === id);
}
