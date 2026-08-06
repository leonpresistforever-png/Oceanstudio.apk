import type { ModelDefinition } from './types';

export const GOOGLE_MODELS: ModelDefinition[] = [
  { id: 'google.gemini-2.5-pro', name: 'Gemini 2.5 Pro', provider: 'google', contextWindow: 1_000_000, supportsThinking: true, supportsVision: true },
  { id: 'google.gemini-2.5-flash', name: 'Gemini 2.5 Flash', provider: 'google', contextWindow: 1_000_000, supportsThinking: true, supportsVision: true },
  { id: 'google.gemini-2.0-flash', name: 'Gemini 2.0 Flash', provider: 'google', contextWindow: 1_000_000, supportsVision: true },
  { id: 'google.gemini-2.0-flash-lite', name: 'Gemini 2.0 Flash Lite', provider: 'google', contextWindow: 1_000_000 },
  { id: 'google.gemini-1.5-pro', name: 'Gemini 1.5 Pro', provider: 'google', contextWindow: 2_000_000, supportsVision: true },
  { id: 'google.gemini-1.5-flash', name: 'Gemini 1.5 Flash', provider: 'google', contextWindow: 1_000_000, supportsVision: true },
];

export const ANTHROPIC_MODELS: ModelDefinition[] = [
  { id: 'anthropic.claude-opus-4', name: 'Claude Opus 4', provider: 'anthropic', contextWindow: 200_000, supportsThinking: true, supportsVision: true },
  { id: 'anthropic.claude-sonnet-4', name: 'Claude Sonnet 4', provider: 'anthropic', contextWindow: 200_000, supportsThinking: true, supportsVision: true },
  { id: 'anthropic.claude-3.7-sonnet', name: 'Claude 3.7 Sonnet', provider: 'anthropic', contextWindow: 200_000, supportsThinking: true, supportsVision: true },
  { id: 'anthropic.claude-3.5-sonnet', name: 'Claude 3.5 Sonnet', provider: 'anthropic', contextWindow: 200_000, supportsVision: true },
  { id: 'anthropic.claude-3.5-haiku', name: 'Claude 3.5 Haiku', provider: 'anthropic', contextWindow: 200_000, supportsVision: true },
  { id: 'anthropic.claude-3-opus', name: 'Claude 3 Opus', provider: 'anthropic', contextWindow: 200_000, supportsVision: true },
];

export const OPENAI_MODELS: ModelDefinition[] = [
  { id: 'openai.gpt-5', name: 'GPT-5', provider: 'openai', contextWindow: 256_000, supportsThinking: true, supportsVision: true },
  { id: 'openai.gpt-5-mini', name: 'GPT-5 Mini', provider: 'openai', contextWindow: 128_000, supportsThinking: true },
  { id: 'openai.o3', name: 'o3', provider: 'openai', contextWindow: 200_000, supportsThinking: true, supportsVision: true },
  { id: 'openai.o4-mini', name: 'o4-mini', provider: 'openai', contextWindow: 128_000, supportsThinking: true },
  { id: 'openai.gpt-4.1', name: 'GPT-4.1', provider: 'openai', contextWindow: 1_000_000, supportsVision: true },
  { id: 'openai.gpt-4o', name: 'GPT-4o', provider: 'openai', contextWindow: 128_000, supportsVision: true },
  { id: 'openai.codex', name: 'Codex', provider: 'openai', contextWindow: 64_000, description: 'Code-specialized model' },
];

export const ALL_BUILTIN_MODELS: ModelDefinition[] = [
  ...GOOGLE_MODELS,
  ...ANTHROPIC_MODELS,
  ...OPENAI_MODELS,
];

export const PROVIDER_LABELS: Record<string, string> = {
  google: 'Google',
  anthropic: 'Claude',
  openai: 'OpenAI / Codex',
  custom: 'Custom',
};

export function getModelById(id: string, customModels: ModelDefinition[] = []): ModelDefinition | undefined {
  return [...ALL_BUILTIN_MODELS, ...customModels].find((m) => m.id === id);
}
