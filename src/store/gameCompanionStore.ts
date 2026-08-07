import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { GameCompanionConfig } from '../playground/gameCompanionTypes';
import { DEFAULT_GAME_COMPANION_CONFIG } from '../playground/gameCompanionTypes';

interface GameCompanionState {
  config: GameCompanionConfig;
  setGameConfig: (patch: Partial<GameCompanionConfig>) => void;
  resetGameConfig: () => void;
}

export const useGameCompanionStore = create<GameCompanionState>()(
  persist(
    (set) => ({
      config: { ...DEFAULT_GAME_COMPANION_CONFIG },

      setGameConfig: (patch) =>
        set((s) => ({ config: { ...s.config, ...patch } })),

      resetGameConfig: () => set({ config: { ...DEFAULT_GAME_COMPANION_CONFIG } }),
    }),
    {
      name: 'ocean-game-companion',
      partialize: (s) => ({ config: s.config }),
      merge: (persisted, current) => {
        const p = persisted as Partial<GameCompanionState> | undefined;
        return {
          ...current,
          ...p,
          config: { ...DEFAULT_GAME_COMPANION_CONFIG, ...p?.config },
        };
      },
    }
  )
);
