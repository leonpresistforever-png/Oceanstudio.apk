import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import {
  type PromptEnhancerConfig,
  DEFAULT_PROMPT_ENHANCER_CONFIG,
} from '../prompt/types';

interface PromptEnhancerState {
  config: PromptEnhancerConfig;
  setConfig: (partial: Partial<PromptEnhancerConfig>) => void;
  getAgentPayload: () => Record<string, unknown>;
}

export const usePromptEnhancerStore = create<PromptEnhancerState>()(
  persist(
    (set, get) => ({
      config: { ...DEFAULT_PROMPT_ENHANCER_CONFIG },

      setConfig: (partial) =>
        set((s) => ({ config: { ...s.config, ...partial } })),

      getAgentPayload: () => {
        const { config } = get();
        return {
          autoEnhance: config.autoEnhance,
          autoCompress: config.autoCompress,
          enhanceStyle: config.enhanceStyle,
          compressLevel: config.compressLevel,
          maxInputTokens: config.maxInputTokens,
        };
      },
    }),
    { name: 'ocean-prompt-enhancer' }
  )
);
