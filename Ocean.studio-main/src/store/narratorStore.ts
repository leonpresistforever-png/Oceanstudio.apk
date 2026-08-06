import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type NarratorMode = 'idle' | 'listening' | 'narrating' | 'processing' | 'speaking';

interface NarratorState {
  mode: NarratorMode;
  enabled: boolean;
  lastDescription: string;
  lastCommand: string;
  commandHistory: string[];
  setMode: (mode: NarratorMode) => void;
  setEnabled: (enabled: boolean) => void;
  setLastDescription: (text: string) => void;
  addCommand: (cmd: string) => void;
}

export const useNarratorStore = create<NarratorState>()(
  persist(
    (set) => ({
      mode: 'idle',
      enabled: false,
      lastDescription: '',
      lastCommand: '',
      commandHistory: [],
      setMode: (mode) => set({ mode }),
      setEnabled: (enabled) => set({ enabled, mode: enabled ? 'listening' : 'idle' }),
      setLastDescription: (lastDescription) => set({ lastDescription }),
      addCommand: (cmd) =>
        set((s) => ({
          lastCommand: cmd,
          commandHistory: [cmd, ...s.commandHistory].slice(0, 50),
        })),
    }),
    { name: 'ocean-narrator-store', partialize: (s) => ({ enabled: s.enabled, commandHistory: s.commandHistory }) }
  )
);
