import type { ChatCompletionRequest } from './types.js';

const THINKING_RE = /<think(?:ing)?>([\s\S]*?)<\/think(?:ing)?>/gi;
const REDACTED_THINKING_RE = /<think>([\s\S]*?)<\/redacted_thinking>/gi;

export interface StreamChunk {
  content?: string;
  reasoning?: string;
  done?: boolean;
  toolCalls?: unknown[];
}

/** Parse provider-specific SSE line into normalized chunk */
export function parseProviderSseLine(line: string, dialect: string): StreamChunk | null {
  const trimmed = line.trim();
  if (!trimmed || trimmed === 'data: [DONE]') return trimmed === 'data: [DONE]' ? { done: true } : null;

  const dataPrefix = 'data: ';
  if (!trimmed.startsWith(dataPrefix)) return null;

  try {
    const json = JSON.parse(trimmed.slice(dataPrefix.length)) as Record<string, unknown>;

    if (dialect === 'anthropic') {
      const type = json.type as string;
      if (type === 'content_block_delta') {
        const delta = json.delta as { type?: string; text?: string; thinking?: string };
        if (delta?.type === 'thinking_delta') return { reasoning: delta.thinking };
        return { content: delta?.text };
      }
      if (type === 'message_stop') return { done: true };
      return null;
    }

    const choices = json.choices as { delta?: { content?: string; reasoning_content?: string }; finish_reason?: string }[] | undefined;
    if (choices?.[0]?.finish_reason) return { done: true };
    const delta = choices?.[0]?.delta;
    if (delta?.reasoning_content) return { reasoning: delta.reasoning_content };
    if (delta?.content) return { content: delta.content };

    const candidates = json.candidates as { content?: { parts?: { text?: string }[] } }[] | undefined;
    const text = candidates?.[0]?.content?.parts?.map((p) => p.text ?? '').join('');
    if (text) return { content: text };

    return null;
  } catch {
    return null;
  }
}

/** Emit OpenAI-compatible SSE chunk */
export function toOpenAiSseChunk(chunk: StreamChunk): string {
  if (chunk.done) return 'data: [DONE]\n\n';
  const delta: Record<string, string> = {};
  if (chunk.content) delta.content = sanitizeChunk(chunk.content);
  if (chunk.reasoning) delta.reasoning_content = sanitizeChunk(chunk.reasoning);
  return `data: ${JSON.stringify({ choices: [{ delta }] })}\n\n`;
}

/** Strip control chars and broken tool JSON from stream tokens */
export function sanitizeChunk(text: string): string {
  return text
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '')
    .replace(THINKING_RE, '')
    .replace(REDACTED_THINKING_RE, '');
}

/** Extract thinking blocks from complete response for telemetry */
export function extractThinkingBlocks(text: string): { content: string; reasoning: string } {
  const reasoning: string[] = [];
  let content = text;
  for (const re of [THINKING_RE, REDACTED_THINKING_RE]) {
    content = content.replace(re, (_, inner: string) => {
      reasoning.push(inner.trim());
      return '';
    });
  }
  return { content: content.trim(), reasoning: reasoning.join('\n') };
}

/** Transform readable stream with line buffering for SSE */
export function createSseTransform(
  dialect: string,
  onChunk?: (chunk: StreamChunk) => void
): TransformStream<Uint8Array, Uint8Array> {
  const decoder = new TextDecoder();
  const encoder = new TextEncoder();
  let buffer = '';

  return new TransformStream({
    transform(chunk, controller) {
      buffer += decoder.decode(chunk, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() ?? '';

      for (const line of lines) {
        const parsed = parseProviderSseLine(line, dialect);
        if (!parsed) continue;
        onChunk?.(parsed);
        controller.enqueue(encoder.encode(toOpenAiSseChunk(parsed)));
      }
    },
    flush(controller) {
      if (buffer.trim()) {
        const parsed = parseProviderSseLine(buffer, dialect);
        if (parsed) {
          onChunk?.(parsed);
          controller.enqueue(encoder.encode(toOpenAiSseChunk(parsed)));
        }
      }
      controller.enqueue(encoder.encode('data: [DONE]\n\n'));
    },
  });
}

export function buildSseHeaders(): Record<string, string> {
  return {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  };
}
