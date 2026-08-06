export type FallbackTrigger = '429' | 'quota' | 'timeout' | '5xx';

export type FallbackDistribution = 'sequential' | 'round_robin' | 'least_used';

export type FallbackProviderId = 'huggingface' | 'nvidia' | 'custom';

export interface FallbackModelEntry {
  id: string;
  providerId: FallbackProviderId;
  modelId: string;
  displayName: string;
  enabled: boolean;
  /** Free tier / backup role */
  tier: 'free' | 'backup' | 'custom';
  source: 'builtin' | 'user';
}

export interface FallbackAuthConfig {
  huggingfaceToken?: string;
  nvidiaApiKey?: string;
}

export interface FallbackConfig {
  /** Master toggle — fallback only runs when true */
  enabled: boolean;
  /** Apply to multi-agent subagents */
  applyToSubagents: boolean;
  triggers: FallbackTrigger[];
  distribution: FallbackDistribution;
  /** Ordered fallback chain — tried on retriable errors */
  chain: FallbackModelEntry[];
  auth: FallbackAuthConfig;
  /** Stats for round-robin / least-used */
  usageCounts: Record<string, number>;
  lastFallbackAt?: number;
  lastFallbackModelId?: string;
}

export const DEFAULT_FALLBACK_TRIGGERS: FallbackTrigger[] = ['429', 'quota', '5xx', 'timeout'];

export const DEFAULT_FALLBACK_CONFIG: FallbackConfig = {
  enabled: false,
  applyToSubagents: true,
  triggers: [...DEFAULT_FALLBACK_TRIGGERS],
  distribution: 'round_robin',
  chain: [],
  auth: {},
  usageCounts: {},
};

export interface FallbackAttemptResult {
  success: boolean;
  content?: string;
  providerId?: string;
  model?: string;
  status?: number;
  reason?: 'rate_limit' | 'quota' | 'timeout' | 'server_error' | 'auth' | 'unknown';
  retriable?: boolean;
}
