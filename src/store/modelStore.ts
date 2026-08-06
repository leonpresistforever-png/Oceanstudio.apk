import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type {
  ModelConfig,
  ModelDefinition,
  SkillFile,
  WebhookDef,
  CustomFunctionDef,
} from '../models/types';
import { DEFAULT_MODEL_CONFIG } from '../models/types';

interface ModelState {
  config: ModelConfig;
  customModels: ModelDefinition[];
  showConfigPanel: boolean;
  setConfig: (partial: Partial<ModelConfig>) => void;
  setSelectedModel: (id: string) => void;
  addCustomModel: (model: ModelDefinition) => void;
  removeCustomModel: (id: string) => void;
  addSkill: (skill: SkillFile) => void;
  removeSkill: (id: string) => void;
  addWebhook: (webhook: WebhookDef) => void;
  removeWebhook: (id: string) => void;
  addCustomFunction: (fn: CustomFunctionDef) => void;
  removeCustomFunction: (id: string) => void;
  setShowConfigPanel: (show: boolean) => void;
  getAgentPayload: () => Record<string, unknown>;
}

export const useModelStore = create<ModelState>()(
  persist(
    (set, get) => ({
      config: { ...DEFAULT_MODEL_CONFIG },
      customModels: [],
      showConfigPanel: false,

      setConfig: (partial) =>
        set((s) => ({ config: { ...s.config, ...partial } })),

      setSelectedModel: (id) =>
        set((s) => ({ config: { ...s.config, selectedModelId: id } })),

      addCustomModel: (model) =>
        set((s) => ({
          customModels: [...s.customModels.filter((m) => m.id !== model.id), model],
          config: { ...s.config, selectedModelId: model.id },
        })),

      removeCustomModel: (id) =>
        set((s) => ({
          customModels: s.customModels.filter((m) => m.id !== id),
          config:
            s.config.selectedModelId === id
              ? { ...s.config, selectedModelId: DEFAULT_MODEL_CONFIG.selectedModelId }
              : s.config,
        })),

      addSkill: (skill) =>
        set((s) => ({
          config: {
            ...s.config,
            skills: [...s.config.skills.filter((sk) => sk.id !== skill.id), skill],
          },
        })),

      removeSkill: (id) =>
        set((s) => ({
          config: { ...s.config, skills: s.config.skills.filter((sk) => sk.id !== id) },
        })),

      addWebhook: (webhook) =>
        set((s) => ({
          config: {
            ...s.config,
            webhooks: [...s.config.webhooks.filter((w) => w.id !== webhook.id), webhook],
          },
        })),

      removeWebhook: (id) =>
        set((s) => ({
          config: { ...s.config, webhooks: s.config.webhooks.filter((w) => w.id !== id) },
        })),

      addCustomFunction: (fn) =>
        set((s) => ({
          config: {
            ...s.config,
            customFunctions: [...s.config.customFunctions.filter((f) => f.id !== fn.id), fn],
          },
        })),

      removeCustomFunction: (id) =>
        set((s) => ({
          config: { ...s.config, customFunctions: s.config.customFunctions.filter((f) => f.id !== id) },
        })),

      setShowConfigPanel: (show) => set({ showConfigPanel: show }),

      getAgentPayload: () => {
        const { config, customModels } = get();
        const model = [...customModels].find((m) => m.id === config.selectedModelId);
        return {
          modelId: config.selectedModelId,
          modelName: model?.name,
          provider: model?.provider ?? config.selectedModelId.split('.')[0],
          temperature: config.temperature,
          topP: config.topP,
          maxTokens: config.maxTokens,
          thinkingLevel: config.thinkingLevel,
          strictness: config.strictness,
          bypassDefaultInstructions: config.bypassDefaultInstructions,
          customInstructions: config.customInstructions,
          customEndpoint: config.customEndpoint,
          postgresConnectionString: config.postgresConnectionString ? '[configured]' : undefined,
          scrapingProvider: config.scrapingProvider,
          customScrapingEndpoint: config.customScrapingEndpoint,
          skills: config.skills.map((s) => ({ name: s.name, content: s.content })),
          webhooks: config.webhooks.map((w) => ({ id: w.id, name: w.name, url: w.url, events: w.events })),
          customFunctions: config.customFunctions,
          toolsEnabled: config.toolsEnabled,
          agentTimeoutSec: typeof config.agentTimeoutSec === 'number' ? config.agentTimeoutSec : 120,
          antiTimeout: config.antiTimeout === true,
          customModels: customModels.map((m) => ({
            id: m.id,
            name: m.name,
            provider: m.provider,
            supportsVision: m.supportsVision,
            supportsThinking: m.supportsThinking,
            contextWindow: m.contextWindow,
          })),
        };
      },
    }),
    {
      name: 'ocean-model-config',
      partialize: (s) => ({
        config: {
          ...s.config,
          customApiKey: undefined,
          postgresConnectionString: undefined,
          customScrapingApiKey: undefined,
          webhooks: s.config.webhooks.map((w) => ({ ...w, secret: undefined })),
        },
        customModels: s.customModels,
      }),
      merge: (persisted, current) => {
        const p = persisted as Partial<typeof current> | undefined;
        return {
          ...current,
          ...p,
          config: { ...DEFAULT_MODEL_CONFIG, ...p?.config },
        };
      },
    }
  )
);
