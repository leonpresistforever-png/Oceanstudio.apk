import type { FallbackModelEntry } from './types';

/** Hugging Face Inference — free-tier serverless models */
export const HUGGINGFACE_FREE_MODELS: Omit<FallbackModelEntry, 'id' | 'enabled' | 'source'>[] = [
  { providerId: 'huggingface', modelId: 'meta-llama/Meta-Llama-3-8B-Instruct', displayName: 'Llama 3 8B Instruct', tier: 'free' },
  { providerId: 'huggingface', modelId: 'microsoft/Phi-3-mini-4k-instruct', displayName: 'Phi-3 Mini 4K', tier: 'free' },
  { providerId: 'huggingface', modelId: 'google/gemma-2-2b-it', displayName: 'Gemma 2 2B IT', tier: 'free' },
  { providerId: 'huggingface', modelId: 'HuggingFaceH4/zephyr-7b-beta', displayName: 'Zephyr 7B Beta', tier: 'free' },
  { providerId: 'huggingface', modelId: 'mistralai/Mistral-7B-Instruct-v0.3', displayName: 'Mistral 7B Instruct', tier: 'free' },
  { providerId: 'huggingface', modelId: 'Qwen/Qwen2.5-7B-Instruct', displayName: 'Qwen 2.5 7B Instruct', tier: 'free' },
  { providerId: 'huggingface', modelId: 'meta-llama/Llama-3.2-3B-Instruct', displayName: 'Llama 3.2 3B Instruct', tier: 'free' },
  { providerId: 'huggingface', modelId: 'TinyLlama/TinyLlama-1.1B-Chat-v1.0', displayName: 'TinyLlama 1.1B Chat', tier: 'free' },
];

/** NVIDIA NIM — backup inference microservices */
export const NVIDIA_NIM_MODELS: Omit<FallbackModelEntry, 'id' | 'enabled' | 'source'>[] = [
  { providerId: 'nvidia', modelId: 'meta/llama3-8b-instruct', displayName: 'Llama 3 8B Instruct (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'meta/llama3-70b-instruct', displayName: 'Llama 3 70B Instruct (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'google/gemma-2-9b-it', displayName: 'Gemma 2 9B IT (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'microsoft/phi-3-mini-128k-instruct', displayName: 'Phi-3 Mini 128K (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'mistralai/mistral-7b-instruct-v0.3', displayName: 'Mistral 7B Instruct (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'nvidia/nemotron-4-340b-instruct', displayName: 'Nemotron 4 340B Instruct', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'nvidia/nemotron-mini-4b-instruct', displayName: 'Nemotron Mini 4B', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'databricks/dbrx-instruct', displayName: 'DBRX Instruct (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'ai21labs/jamba-1.5-large-instruct', displayName: 'Jamba 1.5 Large (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'snowflake/arctic', displayName: 'Snowflake Arctic (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'deepseek-ai/deepseek-r1', displayName: 'DeepSeek R1 (NIM)', tier: 'backup' },
  { providerId: 'nvidia', modelId: 'qwen/qwen2.5-7b-instruct', displayName: 'Qwen 2.5 7B (NIM)', tier: 'backup' },
];

export function createBuiltinFallbackChain(): FallbackModelEntry[] {
  const hf = HUGGINGFACE_FREE_MODELS.map((m, i) => ({
    ...m,
    id: `hf-${i}`,
    enabled: true,
    source: 'builtin' as const,
  }));
  const nim = NVIDIA_NIM_MODELS.map((m, i) => ({
    ...m,
    id: `nim-${i}`,
    enabled: true,
    source: 'builtin' as const,
  }));
  return [...hf, ...nim];
}

export function getFallbackEndpoint(providerId: string): string {
  switch (providerId) {
    case 'huggingface':
      return 'https://router.huggingface.co/v1/chat/completions';
    case 'nvidia':
      return 'https://integrate.api.nvidia.com/v1/chat/completions';
    default:
      return 'https://integrate.api.nvidia.com/v1/chat/completions';
  }
}

export function getFallbackAuthKey(providerId: string, auth: { huggingfaceToken?: string; nvidiaApiKey?: string }): string | undefined {
  if (providerId === 'huggingface') return auth.huggingfaceToken;
  if (providerId === 'nvidia') return auth.nvidiaApiKey;
  return undefined;
}
