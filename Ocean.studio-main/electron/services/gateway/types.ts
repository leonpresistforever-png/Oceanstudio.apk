export type ProviderDialect = 'openai' | 'anthropic' | 'google' | 'cursor' | 'auto';

export interface GatewayMessage {
  role: string;
  content: string | unknown;
  name?: string;
  tool_calls?: unknown[];
}

export interface ChatCompletionRequest {
  model: string;
  messages?: GatewayMessage[];
  input?: unknown[];
  stream?: boolean;
  temperature?: number;
  max_tokens?: number;
  tools?: unknown[];
  tool_choice?: unknown;
  response_format?: unknown;
  previous_response_id?: string;
  store?: boolean;
  [key: string]: unknown;
}

export interface ModelCombo {
  id: string;
  name: string;
  strategy: 'fallback' | 'round_robin' | 'auto_combo' | 'cost_optimal';
  fallbackSequence: string[];
  modelAliases?: Record<string, string>;
  enabled: boolean;
  createdAt: number;
}

export interface VirtualKey {
  id: string;
  name: string;
  keyHash: string;
  keyPrefix: string;
  allowedModels: string[];
  allowedCombos: string[];
  budgetLimitUsd?: number;
  rateLimitRpm?: number;
  isActive: boolean;
  createdAt: number;
}

export interface UsageRecord {
  id: string;
  virtualKeyId?: string;
  connectionId: string;
  model: string;
  promptTokens: number;
  completionTokens: number;
  totalCostUsd: number;
  latencyMs: number;
  success: boolean;
  timestamp: number;
}

export interface CircuitState {
  connectionId: string;
  failures: number;
  lastFailure?: number;
  cooldownUntil?: number;
  status: 'healthy' | 'degraded' | 'open';
}

export interface AutoComboDecision {
  id: string;
  requestId: string;
  winningConnectionId: string;
  candidateScores: Record<string, number>;
  reason: string;
  timestamp: number;
}

export interface StrategicWorkflow {
  id: string;
  name: string;
  description: string;
  triggers: ('chat' | 'agent' | 'playground' | 'ide')[];
  steps: WorkflowStep[];
  enabled: boolean;
  createdAt: number;
}

export interface WorkflowStep {
  id: string;
  type: 'route' | 'compress' | 'transform' | 'fallback' | 'mcp_invoke' | 'webhook';
  config: Record<string, unknown>;
}

export interface GatewayConfig {
  port: number;
  enableCursorBridge: boolean;
  enableCompression: boolean;
  enableAutoCombo: boolean;
  forceHttp11: boolean;
  defaultComboId?: string;
  corsOrigins: string[];
}

export interface ConnectionHealth {
  connectionId: string;
  providerId: string;
  latencyMs: number;
  successRate: number;
  quotaHeadroom: number;
  costPer1kTokens: number;
  lastUsed: number;
}

export const DEFAULT_GATEWAY_CONFIG: GatewayConfig = {
  port: 20128,
  enableCursorBridge: true,
  enableCompression: true,
  enableAutoCombo: true,
  forceHttp11: true,
  corsOrigins: ['*'],
};
