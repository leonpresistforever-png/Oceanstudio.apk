import type { ProviderDefinition, ProviderPlatform } from './types';

const ALL: ProviderPlatform[] = ['electron', 'web', 'mobile'];
const DESKTOP: ProviderPlatform[] = ['electron'];
const CLOUD: ProviderPlatform[] = ['electron', 'web', 'mobile'];

function llm(
  id: string, name: string, desc: string, authType: ProviderDefinition['authType'],
  opts: Omit<Partial<ProviderDefinition>, 'pipeline'> & { pipeline?: Partial<ProviderDefinition['pipeline']> } = {}
): ProviderDefinition {
  return {
    id, name, description: desc, category: 'llm', authType, platforms: CLOUD,
    brandColor: opts.brandColor ?? '#6366f1', logoLetter: name[0],
    envFields: opts.envFields,
    oauth: opts.oauth,
    pipeline: {
      requestFormat: 'openai', responseFormat: 'openai',
      chatEndpoint: opts.pipeline?.chatEndpoint,
      modelsEndpoint: opts.pipeline?.modelsEndpoint ?? '/v1/models',
      ...opts.pipeline,
    },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true, ...opts.features },
    verified: opts.verified ?? true,
    freeTier: opts.freeTier,
    docsUrl: opts.docsUrl,
    websiteUrl: opts.websiteUrl,
    defaultModels: opts.defaultModels,
  };
}

/** 50 production providers — auth workflows defined per OmniRoute patterns */
export const PROVIDER_CATALOG: ProviderDefinition[] = [
  // ── Coding agents (OAuth PKCE / redirect) ─────────────────────────────
  {
    id: 'google.antigravity', name: 'Google Antigravity', category: 'coding',
    description: 'Google Antigravity coding agent — models, skills, tools, system files via OAuth',
    authType: 'oauth_pkce', platforms: ALL, brandColor: '#4285f4', logoLetter: 'G',
    oauth: {
      authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
      tokenUrl: 'https://oauth2.googleapis.com/token',
      scopes: ['openid', 'email', 'profile', 'https://www.googleapis.com/auth/cloud-platform'],
      usePkce: true, clientIdEnv: 'GOOGLE_ANTIGRAVITY_CLIENT_ID', redirectPort: 8768,
      redirectPath: '/oauth/google-antigravity/callback',
    },
    pipeline: {
      requestFormat: 'google', responseFormat: 'google',
      chatEndpoint: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions',
      modelsEndpoint: 'https://generativelanguage.googleapis.com/v1beta/models',
      nudgeBuildEndpoint: '/v1/build', nudgeCheckEndpoint: '/v1/check',
    },
    features: { models: true, skills: true, tools: true, systemFiles: true, proxy: true },
    defaultModels: ['gemini-2.5-pro', 'gemini-2.5-flash', 'gemini-2.0-flash'],
    verified: true, docsUrl: 'https://antigravity.google/',
  },
  {
    id: 'openai.codex', name: 'OpenAI Codex', category: 'coding',
    description: 'Codex coding agent with tool calls and repository context',
    authType: 'oauth_pkce', platforms: CLOUD, brandColor: '#10a37f', logoLetter: 'C',
    oauth: {
      authorizeUrl: 'https://auth.openai.com/authorize', tokenUrl: 'https://auth.openai.com/oauth/token',
      scopes: ['openid', 'profile', 'email', 'offline_access'],
      usePkce: true, clientIdEnv: 'OPENAI_CODEX_CLIENT_ID', redirectPort: 8768,
      redirectPath: '/oauth/openai-codex/callback',
    },
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', modelsEndpoint: 'https://api.openai.com/v1/models' },
    features: { models: true, skills: true, tools: true, systemFiles: true, proxy: true },
    defaultModels: ['gpt-5', 'o3', 'codex'],
    verified: true,
  },
  {
    id: 'anthropic.claude-code', name: 'Claude Code', category: 'coding',
    description: 'Anthropic Claude Code agent — terminal-native coding assistant',
    authType: 'oauth_pkce', platforms: CLOUD, brandColor: '#d97706', logoLetter: 'A',
    oauth: {
      authorizeUrl: 'https://claude.ai/oauth/authorize', tokenUrl: 'https://claude.ai/oauth/token',
      scopes: ['org:create_api_key', 'user:profile', 'user:inference'],
      usePkce: true, clientIdEnv: 'ANTHROPIC_CLIENT_ID', redirectPort: 8768,
      redirectPath: '/oauth/claude-code/callback',
    },
    pipeline: { requestFormat: 'anthropic', responseFormat: 'anthropic', modelsEndpoint: 'https://api.anthropic.com/v1/models' },
    features: { models: true, skills: true, tools: true, systemFiles: true, proxy: true },
    defaultModels: ['claude-opus-4', 'claude-sonnet-4', 'claude-3.7-sonnet'],
    verified: true,
  },
  {
    id: 'cursor.cloud', name: 'Cursor Cloud Agent', category: 'coding',
    description: 'Cursor cloud agent API — remote coding sessions',
    authType: 'api_key', platforms: CLOUD, brandColor: '#000000', logoLetter: 'C',
    envFields: [{ key: 'CURSOR_API_KEY', label: 'Cursor API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', chatEndpoint: 'https://api.cursor.com/v1/chat/completions' },
    features: { models: true, skills: true, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'models-api', name: 'Models API Service', category: 'gateway',
    description: 'OpenAI-compatible model API only — base URL + API key, no OAuth. Communicates directly via /v1/chat/completions.',
    authType: 'api_key', platforms: CLOUD, brandColor: '#0ea5e9', logoLetter: 'M',
    envFields: [
      { key: 'MODELS_API_BASE_URL', label: 'API Base URL', required: true, placeholder: 'https://api.example.com/v1' },
      { key: 'MODELS_API_KEY', label: 'API Key', secret: true, required: true },
      { key: 'MODELS_API_MODEL', label: 'Default Model', placeholder: 'gpt-4o' },
    ],
    pipeline: {
      requestFormat: 'openai', responseFormat: 'openai',
      chatEndpoint: '/chat/completions',
      modelsEndpoint: '/models',
    },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
    docsUrl: 'https://platform.openai.com/docs/api-reference',
    websiteUrl: 'https://openrouter.ai/docs',
  },

  // ── Gateways (proxy URL — OmniRoute pattern) ─────────────────────────
  {
    id: 'omniroute', name: 'OmniRoute', category: 'gateway',
    description: '290 AI providers through one OpenAI-compatible gateway — OAuth + free tiers',
    authType: 'proxy_gateway', platforms: ALL, brandColor: '#8b5cf6', logoLetter: 'O',
    envFields: [
      { key: 'OMNIROUTE_URL', label: 'Gateway URL', placeholder: 'http://localhost:20128', required: true },
      { key: 'OMNIROUTE_API_KEY', label: 'API Key (optional)', secret: true },
    ],
    pipeline: {
      requestFormat: 'openai', responseFormat: 'openai',
      chatEndpoint: '/v1/chat/completions', modelsEndpoint: '/v1/models',
    },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true, freeTier: true, websiteUrl: 'https://omniroute.online',
    docsUrl: 'https://github.com/diegosouzapw/OmniRoute/wiki',
  },
  {
    id: 'openrouter', name: 'OpenRouter', category: 'gateway',
    description: 'Unified API for 200+ models — single API key',
    authType: 'api_key', platforms: CLOUD, brandColor: '#6366f1', logoLetter: 'R',
    envFields: [{ key: 'OPENROUTER_API_KEY', label: 'OpenRouter API Key', secret: true, required: true }],
    pipeline: {
      requestFormat: 'openai', responseFormat: 'openai',
      chatEndpoint: 'https://openrouter.ai/api/v1/chat/completions',
      modelsEndpoint: 'https://openrouter.ai/api/v1/models',
    },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true, websiteUrl: 'https://openrouter.ai',
  },
  {
    id: 'litellm', name: 'LiteLLM Proxy', category: 'gateway',
    description: 'Self-hosted LLM proxy — route to any provider',
    authType: 'proxy_gateway', platforms: DESKTOP, brandColor: '#f59e0b', logoLetter: 'L',
    envFields: [
      { key: 'LITELLM_URL', label: 'LiteLLM URL', placeholder: 'http://localhost:4000', required: true },
      { key: 'LITELLM_API_KEY', label: 'Master Key', secret: true },
    ],
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', modelsEndpoint: '/v1/models' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },

  // ── LLM providers (API key) ───────────────────────────────────────────
  llm('openai', 'OpenAI', 'GPT-5, o3, GPT-4o — API key auth', 'api_key', {
    brandColor: '#10a37f', envFields: [{ key: 'OPENAI_API_KEY', label: 'API Key', secret: true, required: true }],
    defaultModels: ['gpt-5', 'o4-mini', 'gpt-4.1', 'gpt-4o'],
    pipeline: { chatEndpoint: 'https://api.openai.com/v1/chat/completions', modelsEndpoint: 'https://api.openai.com/v1/models' },
  }),
  llm('anthropic', 'Anthropic', 'Claude Opus, Sonnet, Haiku', 'api_key', {
    brandColor: '#d97706',
    envFields: [{ key: 'ANTHROPIC_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: {
      requestFormat: 'anthropic', responseFormat: 'anthropic',
      chatEndpoint: 'https://api.anthropic.com/v1/messages',
      modelsEndpoint: 'https://api.anthropic.com/v1/models',
    },
    defaultModels: ['claude-opus-4', 'claude-sonnet-4', 'claude-3.5-haiku'],
  }),
  llm('google', 'Google AI', 'Gemini 2.5 Pro/Flash via API key', 'api_key', {
    brandColor: '#4285f4',
    envFields: [{ key: 'GOOGLE_API_KEY', label: 'Google AI API Key', secret: true, required: true }],
    pipeline: {
      requestFormat: 'google', responseFormat: 'google',
      chatEndpoint: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions',
      modelsEndpoint: 'https://generativelanguage.googleapis.com/v1beta/models',
    },
    defaultModels: ['gemini-2.5-pro', 'gemini-2.5-flash'],
  }),
  llm('groq', 'Groq', 'Ultra-fast inference — Llama, Mixtral', 'api_key', {
    brandColor: '#f55036', freeTier: true,
    envFields: [{ key: 'GROQ_API_KEY', label: 'Groq API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.groq.com/openai/v1/chat/completions', modelsEndpoint: 'https://api.groq.com/openai/v1/models' },
    defaultModels: ['llama-3.3-70b-versatile', 'mixtral-8x7b-32768'],
  }),
  llm('deepseek', 'DeepSeek', 'DeepSeek V3, R1 reasoning models', 'api_key', {
    brandColor: '#0066ff',
    envFields: [{ key: 'DEEPSEEK_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.deepseek.com/v1/chat/completions' },
    defaultModels: ['deepseek-chat', 'deepseek-reasoner'],
  }),
  llm('mistral', 'Mistral AI', 'Mistral Large, Codestral', 'api_key', {
    brandColor: '#ff7000',
    envFields: [{ key: 'MISTRAL_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.mistral.ai/v1/chat/completions' },
    defaultModels: ['mistral-large-latest', 'codestral-latest'],
  }),
  llm('xai', 'xAI Grok', 'Grok models via xAI API', 'api_key', {
    brandColor: '#1d9bf0',
    envFields: [{ key: 'XAI_API_KEY', label: 'xAI API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.x.ai/v1/chat/completions' },
    defaultModels: ['grok-3', 'grok-3-mini'],
  }),
  llm('cohere', 'Cohere', 'Command R+ models', 'api_key', {
    brandColor: '#39594d',
    envFields: [{ key: 'COHERE_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    defaultModels: ['command-r-plus', 'command-r'],
  }),
  llm('together', 'Together AI', 'Open-source model hosting', 'api_key', {
    brandColor: '#0ea5e9',
    envFields: [{ key: 'TOGETHER_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.together.xyz/v1/chat/completions' },
  }),
  llm('fireworks', 'Fireworks AI', 'Fast open model inference', 'api_key', {
    brandColor: '#7c3aed',
    envFields: [{ key: 'FIREWORKS_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.fireworks.ai/inference/v1/chat/completions' },
  }),
  llm('perplexity', 'Perplexity', 'Search-augmented AI answers', 'api_key', {
    brandColor: '#20b8cd',
    envFields: [{ key: 'PERPLEXITY_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.perplexity.ai/chat/completions' },
    defaultModels: ['sonar-pro', 'sonar'],
  }),
  llm('cerebras', 'Cerebras', 'Ultra-fast Llama inference', 'api_key', {
    brandColor: '#ff4d00', freeTier: true,
    envFields: [{ key: 'CEREBRAS_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.cerebras.ai/v1/chat/completions' },
  }),

  // ── OAuth redirect LLM providers ────────────────────────────────────
  {
    id: 'github.copilot', name: 'GitHub Copilot', category: 'coding',
    description: 'GitHub Copilot API via OAuth',
    authType: 'oauth_redirect', platforms: CLOUD, brandColor: '#24292f', logoLetter: 'G',
    oauth: {
      authorizeUrl: 'https://github.com/login/oauth/authorize',
      tokenUrl: 'https://github.com/login/oauth/access_token',
      scopes: ['read:user', 'copilot'],
      clientIdEnv: 'GITHUB_COPILOT_CLIENT_ID', redirectPort: 8768, redirectPath: '/oauth/github-copilot/callback',
    },
    pipeline: { requestFormat: 'openai', responseFormat: 'openai' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'azure.openai', name: 'Azure OpenAI', category: 'llm',
    description: 'Azure-hosted OpenAI models',
    authType: 'oauth_redirect', platforms: CLOUD, brandColor: '#0078d4', logoLetter: 'A',
    oauth: {
      authorizeUrl: 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
      tokenUrl: 'https://login.microsoftonline.com/common/oauth2/v2.0/token',
      scopes: ['https://cognitiveservices.azure.com/.default'],
      clientIdEnv: 'AZURE_CLIENT_ID', redirectPort: 8768, redirectPath: '/oauth/azure/callback',
    },
    envFields: [{ key: 'AZURE_OPENAI_ENDPOINT', label: 'Azure Endpoint URL', required: true }],
    pipeline: { requestFormat: 'openai', responseFormat: 'openai' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'google.vertex', name: 'Google Vertex AI', category: 'cloud',
    description: 'Vertex AI models via Google OAuth',
    authType: 'oauth_redirect', platforms: CLOUD, brandColor: '#4285f4', logoLetter: 'V',
    oauth: {
      authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
      tokenUrl: 'https://oauth2.googleapis.com/token',
      scopes: ['https://www.googleapis.com/auth/cloud-platform'],
      clientIdEnv: 'GOOGLE_CLOUD_CLIENT_ID', redirectPort: 8765, redirectPath: '/oauth/callback',
    },
    pipeline: { requestFormat: 'google', responseFormat: 'google' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },

  // ── Web cookie providers (OmniRoute claude-web pattern) ───────────────
  {
    id: 'claude.web', name: 'Claude Web', category: 'llm',
    description: 'Claude.ai session cookie — no API key needed',
    authType: 'web_cookie', platforms: DESKTOP, brandColor: '#d97706', logoLetter: 'W',
    envFields: [
      { key: 'CLAUDE_SESSION_KEY', label: 'sessionKey cookie', secret: true, required: true },
      { key: 'CLAUDE_CF_CLEARANCE', label: 'cf_clearance (optional)', secret: true },
    ],
    pipeline: { requestFormat: 'anthropic', responseFormat: 'anthropic' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    defaultModels: ['claude-sonnet-4-6', 'claude-opus-4'],
    verified: true,
  },

  // ── Device flow ───────────────────────────────────────────────────────
  {
    id: 'aws.bedrock', name: 'AWS Bedrock', category: 'cloud',
    description: 'AWS Bedrock models via device/IAM auth',
    authType: 'device_flow', platforms: CLOUD, brandColor: '#ff9900', logoLetter: 'B',
    envFields: [
      { key: 'AWS_ACCESS_KEY_ID', label: 'Access Key ID', required: true },
      { key: 'AWS_SECRET_ACCESS_KEY', label: 'Secret Access Key', secret: true, required: true },
      { key: 'AWS_REGION', label: 'Region', placeholder: 'us-east-1', required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: true, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },

  // ── Embedded Ocean services ───────────────────────────────────────────
  {
    id: 'ocean.github', name: 'GitHub', category: 'dev',
    description: 'Repos, import/export, commits — built-in OAuth',
    authType: 'embedded', platforms: DESKTOP, brandColor: '#24292f', logoLetter: 'G',
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true, embeddedService: 'github',
  },
  {
    id: 'ocean.cloudshell', name: 'Google Cloud Shell', category: 'cloud',
    description: 'Cloud terminal — built-in Google OAuth',
    authType: 'embedded', platforms: CLOUD, brandColor: '#4285f4', logoLetter: 'C',
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true, embeddedService: 'cloudshell',
  },

  // ── More API key LLMs ─────────────────────────────────────────────────
  llm('huggingface', 'Hugging Face', 'Inference API for open models — free tier fallback', 'api_key', {
    brandColor: '#ffd21e',
    envFields: [{ key: 'HF_TOKEN', label: 'HF Access Token', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://router.huggingface.co/v1/chat/completions' },
    defaultModels: [
      'meta-llama/Meta-Llama-3-8B-Instruct',
      'microsoft/Phi-3-mini-4k-instruct',
      'google/gemma-2-2b-it',
    ],
    freeTier: true,
  }),
  llm('replicate', 'Replicate', 'Run ML models via API', 'api_key', {
    brandColor: '#000000',
    envFields: [{ key: 'REPLICATE_API_TOKEN', label: 'API Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
  }),
  llm('ai21', 'AI21 Labs', 'Jamba models', 'api_key', {
    brandColor: '#6366f1',
    envFields: [{ key: 'AI21_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('nvidia', 'NVIDIA NIM', 'NVIDIA inference microservices — backup models', 'api_key', {
    brandColor: '#76b900',
    envFields: [{ key: 'NVIDIA_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://integrate.api.nvidia.com/v1/chat/completions' },
    defaultModels: [
      'meta/llama3-8b-instruct',
      'meta/llama3-70b-instruct',
      'nvidia/nemotron-4-340b-instruct',
      'google/gemma-2-9b-it',
    ],
    freeTier: true,
  }),
  llm('moonshot', 'Moonshot / Kimi', 'Kimi K2 models', 'api_key', {
    brandColor: '#1a1a2e',
    envFields: [{ key: 'MOONSHOT_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { chatEndpoint: 'https://api.moonshot.cn/v1/chat/completions' },
    defaultModels: ['kimi-k2', 'moonshot-v1-128k'],
  }),
  llm('zhipu', 'Zhipu GLM', 'GLM-4 models', 'api_key', {
    brandColor: '#3b82f6',
    envFields: [{ key: 'ZHIPU_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('minimax', 'MiniMax', 'MiniMax text models', 'api_key', {
    brandColor: '#ff6b35',
    envFields: [{ key: 'MINIMAX_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('qwen', 'Qwen / Alibaba', 'Qwen models via DashScope', 'api_key', {
    brandColor: '#ff6a00',
    envFields: [{ key: 'DASHSCOPE_API_KEY', label: 'DashScope API Key', secret: true, required: true }],
  }),

  // ── Local / manual ────────────────────────────────────────────────────
  {
    id: 'ollama', name: 'Ollama', category: 'llm',
    description: 'Local models — no auth, runs on your machine',
    authType: 'manual', platforms: DESKTOP, brandColor: '#000000', logoLetter: 'O', freeTier: true,
    envFields: [{ key: 'OLLAMA_URL', label: 'Ollama URL', placeholder: 'http://localhost:11434', required: true }],
    pipeline: {
      requestFormat: 'openai', responseFormat: 'openai',
      chatEndpoint: '/api/chat', modelsEndpoint: '/api/tags',
    },
    features: { models: true, skills: false, tools: false, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'lmstudio', name: 'LM Studio', category: 'llm',
    description: 'Local OpenAI-compatible server',
    authType: 'manual', platforms: DESKTOP, brandColor: '#1e1e1e', logoLetter: 'L', freeTier: true,
    envFields: [{ key: 'LMSTUDIO_URL', label: 'Server URL', placeholder: 'http://localhost:1234/v1', required: true }],
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', modelsEndpoint: '/v1/models' },
    features: { models: true, skills: false, tools: false, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'custom.openai', name: 'Custom OpenAI Endpoint', category: 'gateway',
    description: 'Any OpenAI-compatible API — manual configuration',
    authType: 'manual', platforms: ALL, brandColor: '#64748b', logoLetter: '+',
    envFields: [
      { key: 'CUSTOM_ENDPOINT', label: 'Base URL', required: true, placeholder: 'https://api.example.com/v1' },
      { key: 'CUSTOM_API_KEY', label: 'API Key', secret: true },
      { key: 'CUSTOM_MODEL', label: 'Default Model' },
    ],
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', modelsEndpoint: '/v1/models' },
    features: { models: true, skills: true, tools: true, systemFiles: true, proxy: true },
    verified: true,
  },

  // ── Dev / productivity / scraping ─────────────────────────────────────
  {
    id: 'notion', name: 'Notion', category: 'productivity',
    description: 'Notion pages and databases', authType: 'oauth_redirect', platforms: CLOUD,
    brandColor: '#000000', logoLetter: 'N',
    oauth: {
      authorizeUrl: 'https://api.notion.com/v1/oauth/authorize', tokenUrl: 'https://api.notion.com/v1/oauth/token',
      scopes: [], clientIdEnv: 'NOTION_CLIENT_ID', redirectPort: 8768, redirectPath: '/oauth/notion/callback',
    },
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'linear', name: 'Linear', category: 'productivity',
    description: 'Issue tracking API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#5e6ad2', logoLetter: 'L',
    envFields: [{ key: 'LINEAR_API_KEY', label: 'Linear API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'slack', name: 'Slack', category: 'productivity',
    description: 'Slack workspace messaging', authType: 'oauth_redirect', platforms: CLOUD,
    brandColor: '#4a154b', logoLetter: 'S',
    oauth: {
      authorizeUrl: 'https://slack.com/oauth/v2/authorize', tokenUrl: 'https://slack.com/api/oauth.v2.access',
      scopes: ['chat:write', 'channels:read'],
      clientIdEnv: 'SLACK_CLIENT_ID', redirectPort: 8768, redirectPath: '/oauth/slack/callback',
    },
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'firecrawl', name: 'Firecrawl', category: 'scraping',
    description: 'Web scraping and crawling API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#ff6b35', logoLetter: 'F',
    envFields: [{ key: 'FIRECRAWL_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom', chatEndpoint: 'https://api.firecrawl.dev/v1/scrape' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'supabase', name: 'Supabase', category: 'database',
    description: 'Supabase database and auth', authType: 'api_key', platforms: CLOUD,
    brandColor: '#3ecf8e', logoLetter: 'S',
    envFields: [
      { key: 'SUPABASE_URL', label: 'Project URL', required: true },
      { key: 'SUPABASE_SERVICE_KEY', label: 'Service Role Key', secret: true, required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'vercel', name: 'Vercel', category: 'dev',
    description: 'Deployments and projects', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#000000', logoLetter: 'V',
    envFields: [{ key: 'VERCEL_TOKEN', label: 'Vercel Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'stripe', name: 'Stripe', category: 'dev',
    description: 'Payments API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#635bff', logoLetter: 'S',
    envFields: [{ key: 'STRIPE_SECRET_KEY', label: 'Secret Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'gitlab', name: 'GitLab', category: 'dev',
    description: 'GitLab repos and CI', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#fc6d26', logoLetter: 'G',
    envFields: [{ key: 'GITLAB_TOKEN', label: 'Personal Access Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'bitbucket', name: 'Bitbucket', category: 'dev',
    description: 'Atlassian Bitbucket API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#0052cc', logoLetter: 'B',
    envFields: [
      { key: 'BITBUCKET_USERNAME', label: 'Username', required: true },
      { key: 'BITBUCKET_APP_PASSWORD', label: 'App Password', secret: true, required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'cloudflare', name: 'Cloudflare', category: 'cloud',
    description: 'Workers, DNS, R2 storage', authType: 'api_key', platforms: CLOUD,
    brandColor: '#f38020', logoLetter: 'C',
    envFields: [{ key: 'CLOUDFLARE_API_TOKEN', label: 'API Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'digitalocean', name: 'DigitalOcean', category: 'cloud',
    description: 'Droplets and App Platform', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#0080ff', logoLetter: 'D',
    envFields: [{ key: 'DO_API_TOKEN', label: 'API Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'sentry', name: 'Sentry', category: 'dev',
    description: 'Error tracking and monitoring', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#362d59', logoLetter: 'S',
    envFields: [{ key: 'SENTRY_AUTH_TOKEN', label: 'Auth Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'datadog', name: 'Datadog', category: 'dev',
    description: 'Observability and APM', authType: 'api_key', platforms: CLOUD,
    brandColor: '#632ca6', logoLetter: 'D',
    envFields: [
      { key: 'DD_API_KEY', label: 'API Key', secret: true, required: true },
      { key: 'DD_APP_KEY', label: 'Application Key', secret: true, required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'jira', name: 'Jira', category: 'productivity',
    description: 'Atlassian Jira issues', authType: 'api_key', platforms: CLOUD,
    brandColor: '#0052cc', logoLetter: 'J',
    envFields: [
      { key: 'JIRA_URL', label: 'Jira URL', required: true },
      { key: 'JIRA_EMAIL', label: 'Email', required: true },
      { key: 'JIRA_API_TOKEN', label: 'API Token', secret: true, required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'asana', name: 'Asana', category: 'productivity',
    description: 'Task and project management', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#f06a6a', logoLetter: 'A',
    envFields: [{ key: 'ASANA_TOKEN', label: 'Personal Access Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'twilio', name: 'Twilio', category: 'dev',
    description: 'SMS, voice, messaging', authType: 'api_key', platforms: CLOUD,
    brandColor: '#f22f46', logoLetter: 'T',
    envFields: [
      { key: 'TWILIO_ACCOUNT_SID', label: 'Account SID', required: true },
      { key: 'TWILIO_AUTH_TOKEN', label: 'Auth Token', secret: true, required: true },
    ],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  // ── Additional providers (50 total) ─────────────────────────────────
  llm('openai.azure', 'Azure OpenAI (Key)', 'Azure OpenAI via API key', 'api_key', {
    brandColor: '#0078d4',
    envFields: [
      { key: 'AZURE_OPENAI_ENDPOINT', label: 'Endpoint', required: true },
      { key: 'AZURE_OPENAI_KEY', label: 'API Key', secret: true, required: true },
    ],
  }),
  llm('anyscale', 'Anyscale', 'Ray-based model serving', 'api_key', {
    brandColor: '#5b4fff',
    envFields: [{ key: 'ANYSCALE_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('baseten', 'Baseten', 'ML model deployment', 'api_key', {
    brandColor: '#000000',
    envFields: [{ key: 'BASETEN_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('lepton', 'Lepton AI', 'Fast AI cloud', 'api_key', {
    brandColor: '#6366f1',
    envFields: [{ key: 'LEPTON_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('sambanova', 'SambaNova', 'Enterprise AI chips', 'api_key', {
    brandColor: '#00a651',
    envFields: [{ key: 'SAMBANOVA_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('writer', 'Writer', 'Enterprise LLM', 'api_key', {
    brandColor: '#000000',
    envFields: [{ key: 'WRITER_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('aleph-alpha', 'Aleph Alpha', 'European LLM', 'api_key', {
    brandColor: '#1a1a1a',
    envFields: [{ key: 'ALEPH_ALPHA_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('stability', 'Stability AI', 'Image and text models', 'api_key', {
    brandColor: '#7c3aed',
    envFields: [{ key: 'STABILITY_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  {
    id: 'netlify', name: 'Netlify', category: 'dev',
    description: 'Sites and deploys', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#00c7b7', logoLetter: 'N',
    envFields: [{ key: 'NETLIFY_TOKEN', label: 'Personal Access Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'railway', name: 'Railway', category: 'dev',
    description: 'Deploy apps and databases', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#0b0d0e', logoLetter: 'R',
    envFields: [{ key: 'RAILWAY_TOKEN', label: 'API Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'flyio', name: 'Fly.io', category: 'cloud',
    description: 'Global app deployment', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#8b5cf6', logoLetter: 'F',
    envFields: [{ key: 'FLY_API_TOKEN', label: 'API Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'mongodb', name: 'MongoDB Atlas', category: 'database',
    description: 'MongoDB cloud database', authType: 'api_key', platforms: CLOUD,
    brandColor: '#00ed64', logoLetter: 'M',
    envFields: [{ key: 'MONGODB_URI', label: 'Connection URI', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'planetscale', name: 'PlanetScale', category: 'database',
    description: 'MySQL-compatible serverless DB', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#000000', logoLetter: 'P',
    envFields: [{ key: 'PLANETSCALE_TOKEN', label: 'Service Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'neon', name: 'Neon', category: 'database',
    description: 'Serverless PostgreSQL', authType: 'api_key', platforms: CLOUD,
    brandColor: '#00e599', logoLetter: 'N',
    envFields: [{ key: 'NEON_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'airtable', name: 'Airtable', category: 'productivity',
    description: 'Spreadsheet-database hybrid', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#fcb400', logoLetter: 'A',
    envFields: [{ key: 'AIRTABLE_TOKEN', label: 'Personal Access Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'discord', name: 'Discord', category: 'productivity',
    description: 'Discord bot API', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#5865f2', logoLetter: 'D',
    envFields: [{ key: 'DISCORD_BOT_TOKEN', label: 'Bot Token', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'tavily', name: 'Tavily', category: 'scraping',
    description: 'AI-optimized search API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#6366f1', logoLetter: 'T',
    envFields: [{ key: 'TAVILY_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  llm('novita', 'Novita AI', 'Image and LLM API', 'api_key', {
    brandColor: '#ff4081',
    envFields: [{ key: 'NOVITA_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('siliconflow', 'SiliconFlow', 'Chinese model hosting', 'api_key', {
    brandColor: '#6366f1',
    envFields: [{ key: 'SILICONFLOW_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  llm('hyperbolic', 'Hyperbolic', 'GPU cloud inference', 'api_key', {
    brandColor: '#000000',
    envFields: [{ key: 'HYPERBOLIC_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
  {
    id: 'heroku', name: 'Heroku', category: 'cloud',
    description: 'PaaS deployments', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#6762a6', logoLetter: 'H',
    envFields: [{ key: 'HEROKU_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'render', name: 'Render', category: 'cloud',
    description: 'Cloud app hosting', authType: 'bearer_token', platforms: CLOUD,
    brandColor: '#000000', logoLetter: 'R',
    envFields: [{ key: 'RENDER_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: false },
    verified: true,
  },
  {
    id: 'exa', name: 'Exa Search', category: 'scraping',
    description: 'Neural web search API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#000000', logoLetter: 'E',
    envFields: [{ key: 'EXA_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'serper', name: 'Serper', category: 'scraping',
    description: 'Google search API', authType: 'api_key', platforms: CLOUD,
    brandColor: '#4285f4', logoLetter: 'S',
    envFields: [{ key: 'SERPER_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  {
    id: 'brave.api', name: 'Brave Search API', category: 'scraping',
    description: 'Brave web search', authType: 'api_key', platforms: CLOUD,
    brandColor: '#fb542b', logoLetter: 'B',
    envFields: [{ key: 'BRAVE_API_KEY', label: 'API Key', secret: true, required: true }],
    pipeline: { requestFormat: 'custom', responseFormat: 'custom' },
    features: { models: false, skills: false, tools: true, systemFiles: false, proxy: true },
    verified: true,
  },
  llm('deepinfra', 'DeepInfra', 'Fast open-source model hosting', 'api_key', {
    brandColor: '#6366f1',
    envFields: [{ key: 'DEEPINFRA_API_KEY', label: 'API Key', secret: true, required: true }],
  }),
];

export function getProviderById(id: string): ProviderDefinition | undefined {
  return PROVIDER_CATALOG.find((p) => p.id === id);
}

export function getProvidersForPlatform(platform: ProviderPlatform): ProviderDefinition[] {
  return PROVIDER_CATALOG.filter((p) => p.platforms.includes(platform));
}

export function getProvidersByAuthType(authType: ProviderDefinition['authType']): ProviderDefinition[] {
  return PROVIDER_CATALOG.filter((p) => p.authType === authType);
}

export function getOAuthProviders(): ProviderDefinition[] {
  return PROVIDER_CATALOG.filter((p) =>
    p.authType === 'oauth_redirect' || p.authType === 'oauth_pkce'
  );
}
