export type ModelProvider = 'google' | 'anthropic' | 'openai' | 'custom';

export type ThinkingLevel = 'off' | 'low' | 'medium' | 'high' | 'max';

export type StrictnessLevel = 'relaxed' | 'normal' | 'strict' | 'maximum';

export type ScrapingProvider = 'firecrawl' | 'puppeteer' | 'playwright' | 'fetch' | 'custom';

export interface ModelDefinition {
  id: string;
  name: string;
  provider: ModelProvider;
  description?: string;
  contextWindow?: number;
  supportsThinking?: boolean;
  supportsVision?: boolean;
  isCustom?: boolean;
}

export interface CustomFunctionDef {
  id: string;
  name: string;
  description: string;
  parameters?: Record<string, unknown>;
  webhookUrl?: string;
}

export interface WebhookDef {
  id: string;
  name: string;
  url: string;
  events: string[];
  secret?: string;
}

export interface SkillFile {
  id: string;
  name: string;
  content: string;
  source: 'upload' | 'agent' | 'builtin';
  createdAt: number;
}

export interface ModelConfig {
  selectedModelId: string;
  temperature: number;
  topP: number;
  maxTokens: number;
  thinkingLevel: ThinkingLevel;
  strictness: StrictnessLevel;
  bypassDefaultInstructions: boolean;
  customInstructions: string;
  /** Custom endpoint override (OpenAI-compatible) */
  customEndpoint?: string;
  customApiKey?: string;
  /** PostgreSQL for agent memory / RAG */
  postgresConnectionString?: string;
  /** Web scraping provider */
  scrapingProvider: ScrapingProvider;
  customScrapingEndpoint?: string;
  customScrapingApiKey?: string;
  skills: SkillFile[];
  webhooks: WebhookDef[];
  customFunctions: CustomFunctionDef[];
  toolsEnabled: boolean;
  /** Agent session timeout in seconds (30–900). Ignored when antiTimeout is true */
  agentTimeoutSec: number;
  /** When true, agent runs until workflow completes — extends in 10 min loops */
  antiTimeout: boolean;
}

export const DEFAULT_MODEL_CONFIG: ModelConfig = {
  selectedModelId: 'google.gemini-2.5-pro',
  temperature: 0.7,
  topP: 0.95,
  maxTokens: 8192,
  thinkingLevel: 'medium',
  strictness: 'normal',
  bypassDefaultInstructions: false,
  customInstructions: '',
  scrapingProvider: 'firecrawl',
  skills: [],
  webhooks: [],
  customFunctions: [],
  toolsEnabled: true,
  agentTimeoutSec: 120,
  antiTimeout: false,
};
