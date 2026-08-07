import type { FallbackConfig, FallbackModelEntry, FallbackTrigger } from '../fallback/types';
import { getFallbackAuthKey, getFallbackEndpoint } from '../fallback/catalog';
import type { InferenceOptions, InferenceResult } from './providerInference';
import { formatForProvider } from '../providers/pipeline';
import type { ProviderDefinition } from '../providers/types';

export interface RetriableError {
  status?: number;
  reason: 'rate_limit' | 'quota' | 'timeout' | 'server_error' | 'auth' | 'unknown';
  retriable: boolean;
  message: string;
}

export function classifyInferenceError(status: number | undefined, body: string, isTimeout: boolean): RetriableError {
  if (isTimeout) {
    return { status, reason: 'timeout', retriable: true, message: 'Request timed out' };
  }
  const lower = body.toLowerCase();
  if (status === 429 || lower.includes('rate limit') || lower.includes('too many requests')) {
    return { status, reason: 'rate_limit', retriable: true, message: body.slice(0, 200) };
  }
  if (
    status === 402 || status === 403
    || lower.includes('quota') || lower.includes('billing') || lower.includes('exceeded')
    || lower.includes('insufficient') || lower.includes('credit')
  ) {
    return { status, reason: 'quota', retriable: true, message: body.slice(0, 200) };
  }
  if (status && status >= 500) {
    return { status, reason: 'server_error', retriable: true, message: body.slice(0, 200) };
  }
  if (status === 401 || status === 403) {
    return { status, reason: 'auth', retriable: false, message: body.slice(0, 200) };
  }
  return { status, reason: 'unknown', retriable: false, message: body.slice(0, 200) };
}

export function shouldTriggerFallback(error: RetriableError, triggers: FallbackTrigger[]): boolean {
  if (!error.retriable) return false;
  if (error.reason === 'rate_limit' && triggers.includes('429')) return true;
  if (error.reason === 'quota' && triggers.includes('quota')) return true;
  if (error.reason === 'timeout' && triggers.includes('timeout')) return true;
  if (error.reason === 'server_error' && triggers.includes('5xx')) return true;
  return false;
}

function orderChain(
  chain: FallbackModelEntry[],
  distribution: FallbackConfig['distribution'],
  usageCounts: Record<string, number>,
  memberIndex = 0
): FallbackModelEntry[] {
  if (distribution === 'sequential') return chain;

  if (distribution === 'round_robin') {
    const offset = memberIndex % chain.length;
    return [...chain.slice(offset), ...chain.slice(0, offset)];
  }

  // least_used — sort by usage count ascending
  return [...chain].sort((a, b) => (usageCounts[a.modelId] ?? 0) - (usageCounts[b.modelId] ?? 0));
}

function parseOpenAiResponse(data: unknown): string {
  const d = data as Record<string, unknown>;
  const choices = (d.choices as { message?: { content?: string } }[]) ?? [];
  return choices[0]?.message?.content ?? (d.content as string) ?? '';
}

/** Direct fallback inference — bypasses primary provider connection lookup */
export async function runFallbackInference(
  message: string,
  entry: FallbackModelEntry,
  auth: FallbackConfig['auth'],
  opts: InferenceOptions = {}
): Promise<{ result: InferenceResult | null; error?: RetriableError }> {
  const apiKey = getFallbackAuthKey(entry.providerId, auth);
  if (!apiKey) {
    return { result: null, error: { reason: 'auth', retriable: false, message: `No API key for ${entry.providerId}` } };
  }

  const endpoint = getFallbackEndpoint(entry.providerId);
  const def: ProviderDefinition = {
    id: entry.providerId,
    name: entry.displayName,
    description: '',
    category: 'llm',
    authType: 'api_key',
    platforms: ['electron', 'web', 'mobile'],
    brandColor: '#6366f1',
    logoLetter: entry.providerId[0].toUpperCase(),
    pipeline: { requestFormat: 'openai', responseFormat: 'openai', chatEndpoint: endpoint },
    features: { models: true, skills: false, tools: false, systemFiles: false, proxy: false },
    verified: true,
  };

  const messages: { role: string; content: string }[] = [];
  if (opts.systemPrompt) messages.push({ role: 'system', content: opts.systemPrompt });
  messages.push({ role: 'user', content: message });

  const body = formatForProvider(def, {
    providerId: entry.providerId,
    format: 'openai',
    messages,
    model: entry.modelId,
    tools: opts.tools,
  }) as Record<string, unknown>;

  if (opts.temperature !== undefined) body.temperature = opts.temperature;
  if (opts.maxTokens !== undefined) body.max_tokens = opts.maxTokens;

  const timeoutMs = opts.antiTimeout ? 600_000 : (opts.timeoutMs ?? 120_000);
  let isTimeout = false;

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });

    if (!res.ok) {
      const errText = await res.text().catch(() => '');
      const err = classifyInferenceError(res.status, errText, false);
      return { result: null, error: err };
    }

    const data = await res.json();
    const content = parseOpenAiResponse(data);
    if (!content) return { result: null };

    return {
      result: {
        content,
        providerId: entry.providerId,
        model: entry.modelId,
        source: 'fallback',
      },
    };
  } catch (e) {
    isTimeout = e instanceof DOMException && e.name === 'TimeoutError';
    const err = classifyInferenceError(undefined, String(e), isTimeout);
    return { result: null, error: err };
  }
}

export async function runFallbackChain(
  message: string,
  config: FallbackConfig,
  opts: InferenceOptions = {},
  memberIndex = 0,
  onModelUsed?: (modelId: string) => void
): Promise<InferenceResult | null> {
  const chain = config.chain.filter((m) => m.enabled);
  if (!chain.length || !config.enabled) return null;

  const ordered = orderChain(chain, config.distribution, config.usageCounts, memberIndex);

  for (const entry of ordered) {
    const apiKey = getFallbackAuthKey(entry.providerId, config.auth);
    if (!apiKey) continue;

    const { result, error } = await runFallbackInference(message, entry, config.auth, opts);
    if (result) {
      onModelUsed?.(entry.modelId);
      return result;
    }
    // Stop chain on non-retriable auth errors for this provider
    if (error?.reason === 'auth') continue;
  }

  return null;
}
