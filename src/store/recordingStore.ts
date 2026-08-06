import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { RecordingConfig, RecordingHardwareInfo, RecordingSource, RecordingStatus } from '../playground/recordingTypes';
import { DEFAULT_RECORDING_CONFIG } from '../playground/recordingTypes';

interface RecordingState {
  config: RecordingConfig;
  hardware: RecordingHardwareInfo | null;
  sources: RecordingSource[];
  status: RecordingStatus;
  elapsedMs: number;
  outputPath: string;
  message: string;
  selectedSourceId: string | null;

  setConfig: (patch: Partial<RecordingConfig>) => void;
  setHardware: (hw: RecordingHardwareInfo | null) => void;
  setSources: (sources: RecordingSource[]) => void;
  setStatus: (status: RecordingStatus, patch?: { elapsedMs?: number; outputPath?: string; message?: string }) => void;
  setSelectedSource: (id: string | null) => void;
}

export const useRecordingStore = create<RecordingState>()(
  persist(
    (set) => ({
      config: { ...DEFAULT_RECORDING_CONFIG },
      hardware: null,
      sources: [],
      status: 'idle',
      elapsedMs: 0,
      outputPath: '',
      message: '',
      selectedSourceId: null,

      setConfig: (patch) => set((s) => ({ config: { ...s.config, ...patch } })),
      setHardware: (hw) => set({ hardware: hw }),
      setSources: (sources) => set({ sources }),
      setStatus: (status, patch) => set({ status, ...patch }),
      setSelectedSource: (id) => set({ selectedSourceId: id }),
    }),
    {
      name: 'ocean-recording',
      partialize: (s) => ({ config: s.config }),
      merge: (persisted, current) => {
        const p = persisted as Partial<RecordingState> | undefined;
        return {
          ...current,
          ...p,
          config: { ...DEFAULT_RECORDING_CONFIG, ...p?.config },
        };
      },
    }
  )
);
