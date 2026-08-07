import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { InstalledSkill, SkillDefinition, SkillScope } from './types';
import { BUILTIN_SKILLS } from './builtinCatalog';
import { CURSOR_WORKFLOW_SKILLS } from './cursorWorkflowSkills';

const ALL_BUILTIN_SKILLS = [...BUILTIN_SKILLS, ...CURSOR_WORKFLOW_SKILLS];

export const MAX_INSTALLED_SKILLS = 200;

export type InstallResult = { ok: true } | { ok: false; reason: 'duplicate' | 'cap' };

interface SkillsState {
  installed: InstalledSkill[];
  marketplaceLoaded: boolean;
  marketplaceSkills: SkillDefinition[];
  marketplaceLoading: boolean;
  lastMarketplaceFetch?: number;

  installSkill: (skill: SkillDefinition) => InstallResult;
  uninstallSkill: (id: string) => void;
  toggleSkill: (id: string, enabled: boolean) => void;
  addCustomSkill: (skill: SkillDefinition) => InstallResult;
  addAgentSkill: (name: string, content: string) => InstallResult;
  setMarketplaceSkills: (skills: SkillDefinition[]) => void;
  setMarketplaceLoading: (loading: boolean) => void;
  setMarketplaceLoaded: (loaded: boolean) => void;
  getInstalledForScope: (scope: SkillScope) => InstalledSkill[];
}

export const useSkillsStore = create<SkillsState>()(
  persist(
    (set, get) => ({
      installed: [],
      marketplaceLoaded: false,
      marketplaceSkills: [],
      marketplaceLoading: false,

      installSkill: (skill) => {
        const s = get();
        if (s.installed.some((x) => x.id === skill.id)) return { ok: false, reason: 'duplicate' };
        if (s.installed.length >= MAX_INSTALLED_SKILLS) return { ok: false, reason: 'cap' };
        const entry: InstalledSkill = {
          ...skill,
          enabled: true,
          installedAt: Date.now(),
          content: skill.content ?? `# ${skill.name}\n\n${skill.description}`,
        };
        set({ installed: [entry, ...s.installed] });
        return { ok: true };
      },

      uninstallSkill: (id) =>
        set((s) => ({ installed: s.installed.filter((x) => x.id !== id) })),

      toggleSkill: (id, enabled) =>
        set((s) => ({
          installed: s.installed.map((x) => (x.id === id ? { ...x, enabled } : x)),
        })),

      addCustomSkill: (skill) => get().installSkill({ ...skill, source: 'custom' }),

      addAgentSkill: (name, content) =>
        get().installSkill({
          id: `agent.${crypto.randomUUID()}`,
          name,
          description: `Agent-created skill: ${name}`,
          category: 'coding',
          scope: 'both',
          tags: ['agent-created'],
          source: 'agent',
          content,
        }),

      setMarketplaceSkills: (skills) => set({ marketplaceSkills: skills }),
      setMarketplaceLoading: (loading) => set({ marketplaceLoading: loading }),
      setMarketplaceLoaded: (loaded) => set({ marketplaceLoaded: loaded, lastMarketplaceFetch: Date.now() }),

      getInstalledForScope: (scope) => {
        const installed = get().installed.filter((s) => s.enabled);
        return installed.filter((s) => s.scope === 'both' || s.scope === scope);
      },
    }),
    {
      name: 'ocean-skills-store',
      partialize: (s) => ({ installed: s.installed }),
    }
  )
);

export function getAllCatalogSkills(): SkillDefinition[] {
  const store = useSkillsStore.getState();
  const seen = new Set<string>();
  const merged: SkillDefinition[] = [];
  for (const s of [...ALL_BUILTIN_SKILLS, ...store.marketplaceSkills]) {
    if (seen.has(s.id)) continue;
    seen.add(s.id);
    merged.push(s);
  }
  return merged;
}

export function getInstalledSkillCount(): number {
  return useSkillsStore.getState().installed.filter((s) => s.enabled).length;
}
