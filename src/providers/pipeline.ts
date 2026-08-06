import type { PipelineFormat, ProviderDefinition } from './types';

/** Two-step pipeline: Ocean client → external provider → build/check nudge */
export interface PipelineRequest {
  providerId: string;
  format: PipelineFormat;
  messages: { role: string; content: string }[];
  model?: string;
  tools?: unknown[];
  metadata?: Record<string, unknown>;
}

export interface PipelineResponse {
  providerId: string;
  format: PipelineFormat;
  content: string;
  model?: string;
  toolCalls?: unknown[];
  usage?: { promptTokens: number; completionTokens: number };
  raw?: unknown;
}

export interface PipelineNudge {
  type: 'build' | 'check' | 'status';
  providerId: string;
  payload: Record<string, unknown>;
}

export function buildPipelineRequest(
  def: ProviderDefinition,
  message: string,
  model?: string
): PipelineRequest {
  return {
    providerId: def.id,
    format: def.pipeline.requestFormat,
    messages: [{ role: 'user', content: message }],
    model: model ?? def.defaultModels?.[0],
    metadata: { pipeline: 'step1-request' },
  };
}

export function formatForProvider(def: ProviderDefinition, body: PipelineRequest): unknown {
  switch (def.pipeline.requestFormat) {
    case 'openai':
      return {
        model: body.model,
        messages: body.messages,
        tools: body.tools,
        stream: false,
      };
    case 'anthropic':
      return {
        model: body.model,
        max_tokens: 8192,
        messages: body.messages.map((m) => ({
          role: m.role === 'assistant' ? 'assistant' : 'user',
          content: m.content,
        })),
      };
    case 'google':
      return {
        contents: body.messages.map((m) => ({
          role: m.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: m.content }],
        })),
        generationConfig: { temperature: 0.7 },
      };
    default:
      return body;
  }
}

export function getNudgeEndpoints(def: ProviderDefinition): { build?: string; check?: string } {
  return {
    build: def.pipeline.nudgeBuildEndpoint,
    check: def.pipeline.nudgeCheckEndpoint,
  };
}
