import type { CompressLevel, EnhanceStyle, PromptEnhancerConfig } from '../prompt/types';
import { runAgentInference } from './providerInference';

/** Rough token estimate — ~4 chars per token for English */
export function estimateTokens(text: string): number {
  if (!text) return 0;
  return Math.ceil(text.length / 4);
}

const FILLER_PATTERNS = [
  /\b(please|kindly|just|really|very|actually|basically|literally)\b/gi,
  /\b(i think|i believe|i want to|i need to|can you please)\b/gi,
  /^\s*um+\s*/gi,
  /\s{2,}/g,
];

const AGGRESSIVE_PATTERNS = [
  /\b(that would be great|thank you in advance|if possible)\b/gi,
  /---+|===+|\*\*\*+/g,
];

export function compressPrompt(text: string, level: CompressLevel): string {
  if (level === 'off' || !text.trim()) return text;

  let out = text.trim();

  if (level === 'light') {
    out = out.replace(/\n{3,}/g, '\n\n').replace(/[ \t]+/g, ' ');
  }

  if (level === 'medium' || level === 'aggressive') {
    for (const re of FILLER_PATTERNS) {
      out = out.replace(re, level === 'aggressive' ? '' : ' ');
    }
    out = out.replace(/\n{2,}/g, '\n').trim();
  }

  if (level === 'aggressive') {
    for (const re of AGGRESSIVE_PATTERNS) {
      out = out.replace(re, '');
    }
    // Collapse bullet lists to single line when very long
    if (estimateTokens(out) > 800) {
      out = out.replace(/^[\s]*[-*•]\s+/gm, '• ');
    }
  }

  return out.replace(/\s+\n/g, '\n').trim();
}

function styleInstruction(style: EnhanceStyle): string {
  switch (style) {
    case 'detailed':
      return 'Expand with clear goals, constraints, and expected output format.';
    case 'concise':
      return 'Make concise and direct — remove fluff, keep intent.';
    case 'technical':
      return 'Use precise technical language, file paths, and acceptance criteria.';
    case 'creative':
      return 'Add creative framing while preserving the user intent.';
    default:
      return 'Clarify intent, add structure, preserve meaning.';
  }
}

/** Rule-based enhance — no API call */
export function enhancePromptLocally(text: string, style: EnhanceStyle): string {
  const trimmed = text.trim();
  if (!trimmed) return text;

  const hasGoal = /^(goal|task|fix|build|create|implement|debug|refactor)/i.test(trimmed);
  const lines: string[] = [];

  if (!hasGoal && style !== 'concise') {
    lines.push(`Task: ${trimmed}`);
  } else {
    lines.push(trimmed);
  }

  if (style === 'technical' && !trimmed.includes('Acceptance')) {
    lines.push('', 'Acceptance: solution works, no regressions.');
  }

  if (style === 'detailed' && trimmed.length < 120) {
    lines.push('', 'Context: workspace agent session in Ocean.studio.');
  }

  return lines.join('\n').trim();
}

/** AI enhance via provider inference */
export async function enhancePromptWithAI(
  text: string,
  config: PromptEnhancerConfig,
  context: Record<string, unknown>
): Promise<string> {
  const custom = config.customEnhancePrompt.trim();
  const system = custom || [
    'You are a prompt enhancer. Rewrite the user message to be clearer for a coding agent.',
    styleInstruction(config.enhanceStyle),
    'Output ONLY the improved prompt — no preamble.',
  ].join(' ');

  const result = await runAgentInference(
    `Enhance this prompt:\n\n${text}`,
    {
      ...context,
      modelConfig: {
        ...(context.modelConfig as Record<string, unknown>),
        customInstructions: system,
        maxTokens: 1024,
        temperature: 0.3,
      },
    }
  );

  const enhanced = result.content.trim();
  if (enhanced.length < 10 || enhanced.startsWith('_(Offline')) return text;
  return enhanced;
}

export function truncateToTokenBudget(text: string, maxTokens: number): string {
  const est = estimateTokens(text);
  if (est <= maxTokens) return text;
  const maxChars = maxTokens * 4;
  return `${text.slice(0, maxChars - 20).trim()}… [truncated]`;
}

export async function processOutgoingPrompt(
  text: string,
  config: PromptEnhancerConfig,
  context: Record<string, unknown>
): Promise<{ text: string; result: import('../prompt/types').PromptProcessResult }> {
  const original = text;
  let processed = text;
  let enhanced = false;
  let compressed = false;

  const tokensBefore = estimateTokens(original);

  if (config.autoEnhance) {
    if (config.enhanceStyle === 'concise') {
      processed = enhancePromptLocally(processed, 'concise');
    } else {
      try {
        processed = await enhancePromptWithAI(processed, config, context);
        enhanced = true;
      } catch {
        processed = enhancePromptLocally(processed, config.enhanceStyle);
        enhanced = true;
      }
    }
  }

  if (config.autoCompress && config.compressLevel !== 'off') {
    const before = processed;
    processed = compressPrompt(processed, config.compressLevel);
    compressed = processed !== before;
  }

  processed = truncateToTokenBudget(processed, config.maxInputTokens);
  const tokensAfter = estimateTokens(processed);

  return {
    text: processed,
    result: {
      original,
      processed,
      enhanced,
      compressed,
      estimatedTokensBefore: tokensBefore,
      estimatedTokensAfter: tokensAfter,
      savedTokens: Math.max(0, tokensBefore - tokensAfter),
    },
  };
}
