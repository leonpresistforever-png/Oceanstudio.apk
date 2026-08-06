import { PROVIDER_CATALOG } from '../providers/catalog';
import { getProviderConnection } from '../providers/registry';
import { syncProviderConnectionsFromBackend } from '../providers/sync';
import { formatForProvider } from '../providers/pipeline';
import type { ProviderDefinition } from '../providers/types';
import type { CompatibilityMode } from '../agents/types';
import { applyParamFilters } from '../agents/memberContext';
import { useFallbackStore } from '../store/fallbackStore';
import {
  classifyInferenceError,
  runFallbackChain,
  shouldTriggerFallback,
  type RetriableError,
} from './fallbackInference';

export interface InferenceOptions {
  providerId?: string;
  modelId?: string;
  temperature?: number;
  maxTokens?: number;
  systemPrompt?: string;
  compatibility?: CompatibilityMode;
  paramFilters?: { blockedParams: string[]; allowedParams: string[] };
  tools?: unknown[];
  timeoutMs?: number;
  antiTimeout?: boolean;
}

export interface InferenceResult {
  content: string;
  providerId: string;
  model?: string;
  source: 'provider' | 'fallback' | 'local';
  fallbackFrom?: string;
}

const CATALOG_IDS = new Set(PROVIDER_CATALOG.map((p) => p.id));

const DEFAULT_CHAT_ENDPOINTS: Record<string, string> = {
  openai: 'https://api.openai.com/v1/chat/completions',
  anthropic: 'https://api.anthropic.com/v1/messages',
  google: 'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions',
  mistral: 'https://api.mistral.ai/v1/chat/completions',
  cohere: 'https://api.cohere.com/v2/chat',
  together: 'https://api.together.xyz/v1/chat/completions',
  fireworks: 'https://api.fireworks.ai/inference/v1/chat/completions',
  perplexity: 'https://api.perplexity.ai/chat/completions',
  cerebras: 'https://api.cerebras.ai/v1/chat/completions',
  groq: 'https://api.groq.com/openai/v1/chat/completions',
  deepseek: 'https://api.deepseek.com/v1/chat/completions',
  xai: 'https://api.x.ai/v1/chat/completions',
  'models-api': 'https://api.models.dev/v1/chat/completions',
};

function compatibilityToFormat(mode: CompatibilityMode): ProviderDefinition['pipeline']['requestFormat'] {
  switch (mode) {
    case 'anthropic-messages': return 'anthropic';
    case 'gemini': return 'google';
    case 'openai-chat':
    case 'openai-responses':
    case 'antigravity':
      return 'openai';
    default: return 'openai';
  }
}

function findApiKey(config: Record<string, string>): string | undefined {
  for (const [key, val] of Object.entries(config)) {
    if (!val) continue;
    if (/key|token|secret/i.test(key) && val.length > 8) return val;
  }
  return undefined;
}

function resolveEndpoint(def: ProviderDefinition, config: Record<string, string>): string | undefined {
  const endpoint = def.pipeline.chatEndpoint
    ?? DEFAULT_CHAT_ENDPOINTS[def.id]
    ?? config.CUSTOM_ENDPOINT
    ?? config.API_ENDPOINT
    ?? config.MODELS_API_BASE_URL;
  if (!endpoint) return undefined;
  if (endpoint.startsWith('http')) {
    if (
      endpoint.endsWith('/chat/completions')
      || endpoint.endsWith('/completions')
      || endpoint.endsWith('/messages')
      || /\/v\d+\/chat\/?$/.test(endpoint)
      || endpoint.endsWith('/chat')
    ) {
      return endpoint;
    }
    return `${endpoint.replace(/\/$/, '')}/chat/completions`;
  }
  const base = config.MODELS_API_BASE_URL ?? config.OMNIROUTE_URL ?? config.LITELLM_URL ?? config.GATEWAY_URL ?? 'http://localhost:20128';
  return `${base.replace(/\/$/, '')}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
}

function parseResponseBody(format: ProviderDefinition['pipeline']['responseFormat'], data: unknown): string {
  const d = data as Record<string, unknown>;
  if (format === 'anthropic') {
    const blocks = (d.content as { type: string; text?: string }[]) ?? [];
    return blocks.filter((b) => b.type === 'text').map((b) => b.text ?? '').join('\n');
  }
  if (format === 'google') {
    const candidates = (d.candidates as { content?: { parts?: { text?: string }[] } }[]) ?? [];
    return candidates[0]?.content?.parts?.map((p) => p.text ?? '').join('') ?? '';
  }
  const choices = (d.choices as { message?: { content?: string } }[]) ?? [];
  return choices[0]?.message?.content ?? (d.output_text as string) ?? (d.content as string) ?? '';
}

function resolveProviderId(
  modelConfig: Record<string, unknown> | undefined,
  memberProviderId?: string,
): string {
  if (memberProviderId && CATALOG_IDS.has(memberProviderId)) return memberProviderId;
  const explicit = modelConfig?.provider as string | undefined;
  if (explicit && CATALOG_IDS.has(explicit)) return explicit;

  const modelId = modelConfig?.modelId as string | undefined;
  if (modelId) {
    const sorted = [...PROVIDER_CATALOG].sort((a, b) => b.id.length - a.id.length);
    for (const p of sorted) {
      if (modelId === p.id || modelId.startsWith(`${p.id}.`)) return p.id;
    }
  }
  return explicit ?? memberProviderId ?? 'openai';
}

function resolveModelId(
  modelConfig: Record<string, unknown> | undefined,
  memberModelId: string | undefined,
  providerId: string,
): string | undefined {
  if (memberModelId) return memberModelId;
  const modelId = modelConfig?.modelId as string | undefined;
  if (!modelId) return undefined;
  if (modelId.startsWith(`${providerId}.`)) return modelId.slice(providerId.length + 1);
  if (CATALOG_IDS.has(modelId)) return undefined;
  return modelId;
}

/** Run real provider inference when credentials and endpoint are available */
export async function runProviderInference(
  message: string,
  opts: InferenceOptions = {}
): Promise<InferenceResult | null> {
  const attempt = await attemptProviderInference(message, opts);
  return attempt.result;
}

interface ProviderAttempt {
  result: InferenceResult | null;
  error?: RetriableError;
  providerId: string;
}

async function attemptProviderInference(
  message: string,
  opts: InferenceOptions = {}
): Promise<ProviderAttempt> {
  await syncProviderConnectionsFromBackend();

  const providerId = opts.providerId ?? 'openai';
  const conn = getProviderConnection(providerId);
  if (!conn || conn.status !== 'connected') return { result: null, providerId };

  const def = PROVIDER_CATALOG.find((p) => p.id === providerId);
  if (!def) return { result: null, providerId };

  const apiKey = findApiKey(conn.config);
  if (!apiKey && def.authType === 'api_key') return { result: null, providerId };

  const endpoint = resolveEndpoint(def, conn.config);
  if (!endpoint) return { result: null, providerId };

  const format = opts.compatibility && opts.compatibility !== 'auto'
    ? compatibilityToFormat(opts.compatibility)
    : def.pipeline.requestFormat;

  const model = opts.modelId ?? conn.syncedModels?.[0] ?? def.defaultModels?.[0] ?? conn.config.MODELS_API_MODEL;
  const messages: { role: string; content: string }[] = [];
  if (opts.systemPrompt) messages.push({ role: 'system', content: opts.systemPrompt });
  messages.push({ role: 'user', content: message });

  let body = formatForProvider(
    { ...def, pipeline: { ...def.pipeline, requestFormat: format } },
    { providerId, format, messages, model, tools: opts.tools }
  ) as Record<string, unknown>;

  if (opts.temperature !== undefined) body.temperature = opts.temperature;
  if (opts.maxTokens !== undefined) body.max_tokens = opts.maxTokens;

  if (opts.paramFilters) {
    body = applyParamFilters(body, { ...opts.paramFilters, autoLearnFrom400: false });
  }

  let requestUrl = endpoint;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (apiKey) {
    if (providerId.includes('anthropic')) {
      headers['x-api-key'] = apiKey;
      headers['anthropic-version'] = '2023-06-01';
    } else if (providerId === 'google' || providerId === 'google.vertex') {
      const sep = requestUrl.includes('?') ? '&' : '?';
      requestUrl = `${requestUrl}${sep}key=${encodeURIComponent(apiKey)}`;
    } else {
      headers.Authorization = `Bearer ${apiKey}`;
    }
  }

  let isTimeout = false;
  try {
    const timeoutMs = opts.antiTimeout ? 600_000 : (opts.timeoutMs ?? 120_000);
    const res = await fetch(requestUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      const error = classifyInferenceError(res.status, errText, false);
      return { result: null, error, providerId };
    }

    const data = await res.json();
    const content = parseResponseBody(format, data);
    if (!content) return { result: null, providerId };

    return {
      result: { content, providerId, model, source: 'provider' },
      providerId,
    };
  } catch (e) {
    isTimeout = e instanceof DOMException && e.name === 'TimeoutError';
    const error = classifyInferenceError(undefined, String(e), isTimeout);
    return { result: null, error, providerId };
  }
}

/** Build a substantive local response when no provider API is reachable */
export function buildLocalAgentResponse(
  message: string,
  context: Record<string, unknown>
): string {
  const member = context.multiAgentMember as { memberName?: string; role?: string; task?: string } | undefined;
  const workspace = context.workspacePath as string | undefined;
  const terminal = context.terminalType as string | undefined;
  const mcpCount = Object.keys((context.mcpServers as object) ?? {}).length;
  const pluginCount = ((context.pluginTools as unknown[]) ?? []).length;
  const phase = context._multiAgentPhase as string | undefined;

  const lines: string[] = [];
  const agentName = member?.memberName ?? 'Agent';

  if (phase === 'parallel-plan') {
    lines.push(`${agentName} plan for: ${message.slice(0, 300)}`);
    lines.push('', 'Delegated tasks will be executed by team workers with terminal, MCP, and plugin access.');
  } else if (phase === 'parallel-synthesize' || phase === 'collaborative') {
    lines.push(`${agentName} synthesis:`, message.slice(0, 500));
  } else if (member) {
    lines.push(`${agentName} (${member.role}): ${member.task ?? 'task'}`);
    lines.push('', `Processed request: ${message.slice(0, 400)}`);
  } else {
    lines.push(`Processed: ${message.slice(0, 400)}`);
  }

  lines.push('', '_(Offline mode — connect a provider for live model responses.)_');

  if (workspace) lines.push(`Workspace: ${workspace}`);
  if (terminal) lines.push(`Terminal: ${terminal} — commands execute on real shell`);
  if (mcpCount) lines.push(`MCP tools available: ${mcpCount}`);
  if (pluginCount) lines.push(`Plugin tools: ${pluginCount}`);

  return lines.join('\n');
}

/** Try provider API first, fall back to local contextual response */
export async function runAgentInference(
  message: string,
  context: Record<string, unknown>
): Promise<InferenceResult> {
  const member = context.multiAgentMember as {
    providerId?: string;
    modelId?: string;
    systemPrompt?: string;
    params?: { temperature?: number; maxTokens?: number };
    compatibility?: CompatibilityMode;
    paramFilters?: { blockedParams: string[]; allowedParams: string[] };
  } | undefined;

  const modelConfig = context.modelConfig as Record<string, unknown> | undefined;
  const providerId = resolveProviderId(modelConfig, member?.providerId);
  const modelId = resolveModelId(modelConfig, member?.modelId, providerId);

  const antiTimeout = modelConfig?.antiTimeout as boolean | undefined;
  const timeoutMs = antiTimeout ? 600_000 : ((modelConfig?.agentTimeoutSec as number) ?? 120) * 1000;

  const inferenceOpts = {
    providerId,
    modelId,
    temperature: member?.params?.temperature ?? (modelConfig?.temperature as number),
    maxTokens: member?.params?.maxTokens ?? (modelConfig?.maxTokens as number),
    systemPrompt: member?.systemPrompt ?? (modelConfig?.customInstructions as string),
    compatibility: member?.compatibility,
    paramFilters: member?.paramFilters,
    tools: modelConfig?.tools as unknown[],
    timeoutMs,
    antiTimeout,
  };

  const attempt = await attemptProviderInference(message, inferenceOpts);

  if (attempt.result) return attempt.result;

  // Fallback — only when enabled and primary failed with retriable error
  const fallbackConfig = useFallbackStore.getState().config;
  const isSubagent = !!member;
  const fallbackAllowed = fallbackConfig.enabled
    && (!isSubagent || fallbackConfig.applyToSubagents);

  const shouldFallback = fallbackAllowed && (
    !attempt.error
    || shouldTriggerFallback(attempt.error, fallbackConfig.triggers)
  );

  if (shouldFallback) {
    const memberIndex = (context._multiAgentMemberIndex as number) ?? 0;
    const fallbackResult = await runFallbackChain(
      message,
      fallbackConfig,
      inferenceOpts,
      memberIndex,
      (modelId) => useFallbackStore.getState().recordUsage(modelId)
    );
    if (fallbackResult) {
      return {
        ...fallbackResult,
        fallbackFrom: providerId,
      };
    }
  }

  return {
    content: buildLocalAgentResponse(message, context),
    providerId: providerId ?? 'local',
    model: modelId,
    source: 'local',
  };
}
