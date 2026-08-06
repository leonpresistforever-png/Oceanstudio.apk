import type { ChatCompletionRequest, GatewayMessage } from './types.js';

/** Normalize roles and message arrays across OpenAI, Anthropic, Gemini, Cursor dialects */
export function normalizeRoles(messages: GatewayMessage[]): GatewayMessage[] {
  const mapped = messages.map((m) => {
    let role = m.role;
    if (role === 'developer') role = 'system';
    if (role === 'model') role = 'assistant';
    return { ...m, role };
  });

  const merged: GatewayMessage[] = [];
  for (const msg of mapped) {
    const prev = merged[merged.length - 1];
    const content = typeof msg.content === 'string' ? msg.content : JSON.stringify(msg.content);
    if (prev && prev.role === msg.role && typeof prev.content === 'string') {
      prev.content = `${prev.content}\n\n${content}`;
    } else {
      merged.push({ ...msg, content });
    }
  }
  return merged;
}

/** Detect request dialect from payload shape */
export function detectDialect(body: ChatCompletionRequest): 'openai' | 'anthropic' | 'cursor' | 'google' {
  if (body.input && !body.messages) return 'cursor';
  if (body.previous_response_id || body.store !== undefined) return 'cursor';
  const msgs = body.messages ?? [];
  if (msgs.some((m) => m.role === 'developer')) return 'cursor';
  if (body.tools && JSON.stringify(body.tools).includes('input_schema')) return 'anthropic';
  return 'openai';
}

/** Strip unsupported top-level params that break strict clients */
export function stripUnsupportedParams(body: ChatCompletionRequest): ChatCompletionRequest {
  const cleaned = { ...body };
  const strip = ['previous_response_id', 'store', 'metadata', 'user', 'service_tier'];
  for (const key of strip) delete cleaned[key];
  return cleaned;
}

export function cursorToOpenAI(body: ChatCompletionRequest): ChatCompletionRequest {
  const out: ChatCompletionRequest = { ...stripUnsupportedParams(body) };

  if (body.input && !body.messages) {
    const input = body.input as { role?: string; content?: unknown }[];
    out.messages = input.map((item) => ({
      role: item.role === 'assistant' ? 'assistant' : item.role === 'system' ? 'system' : 'user',
      content: typeof item.content === 'string' ? item.content : JSON.stringify(item.content),
    }));
    delete out.input;
  }

  if (out.messages) {
    out.messages = normalizeRoles(out.messages);
  }

  return out;
}

export function toAnthropic(body: ChatCompletionRequest): Record<string, unknown> {
  const messages = normalizeRoles(body.messages ?? []);
  const systemParts = messages.filter((m) => m.role === 'system');
  const chatMessages = messages.filter((m) => m.role !== 'system');

  return {
    model: body.model,
    max_tokens: body.max_tokens ?? 8192,
    temperature: body.temperature,
    system: systemParts.map((m) => String(m.content)).join('\n') || undefined,
    messages: chatMessages.map((m) => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: String(m.content),
    })),
    stream: body.stream ?? false,
    tools: body.tools,
  };
}

export function toOpenAI(body: ChatCompletionRequest): Record<string, unknown> {
  const normalized = cursorToOpenAI(body);
  return {
    model: normalized.model,
    messages: normalizeRoles(normalized.messages ?? []),
    temperature: normalized.temperature,
    max_tokens: normalized.max_tokens,
    stream: normalized.stream ?? false,
    tools: normalized.tools,
    tool_choice: normalized.tool_choice,
    response_format: normalized.response_format,
  };
}

export function toGoogle(body: ChatCompletionRequest): Record<string, unknown> {
  const messages = normalizeRoles(body.messages ?? []);
  return {
    contents: messages.filter((m) => m.role !== 'system').map((m) => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: String(m.content) }],
    })),
    systemInstruction: messages.find((m) => m.role === 'system')
      ? { parts: [{ text: String(messages.find((m) => m.role === 'system')!.content) }] }
      : undefined,
    generationConfig: {
      temperature: body.temperature ?? 0.7,
      maxOutputTokens: body.max_tokens ?? 8192,
    },
  };
}

export function resolveModelAlias(model: string, aliases?: Record<string, string>): string {
  return aliases?.[model] ?? model;
}
