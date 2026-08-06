import type { GatewayMessage } from './types.js';

/** Caveman-style context compression — abbreviate common patterns to save tokens */
const CAVEMAN_RULES: [RegExp, string][] = [
  [/\bplease\b/gi, ''],
  [/\bcould you\b/gi, ''],
  [/\bI would like you to\b/gi, ''],
  [/\bmake sure to\b/gi, ''],
  [/\bin order to\b/gi, 'to'],
  [/\bat this point in time\b/gi, 'now'],
  [/\bdue to the fact that\b/gi, 'because'],
  [/\bfor the purpose of\b/gi, 'for'],
  [/\bwith regard to\b/gi, 're:'],
  [/\bin the event that\b/gi, 'if'],
  [/\butilize\b/gi, 'use'],
  [/\bimplement\b/gi, 'add'],
  [/\bfunctionality\b/gi, 'feature'],
  [/\bapproximately\b/gi, '~'],
];

/** RTK filter — remove redundant tool context and duplicate system prompts */
export function rtkFilter(messages: GatewayMessage[]): GatewayMessage[] {
  const seen = new Set<string>();
  return messages.filter((m) => {
    const key = `${m.role}:${String(m.content).slice(0, 120)}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function cavemanCompress(text: string): string {
  let out = text;
  for (const [re, replacement] of CAVEMAN_RULES) {
    out = out.replace(re, replacement);
  }
  return out.replace(/\s{2,}/g, ' ').trim();
}

export function compressMessages(
  messages: GatewayMessage[],
  options: { caveman?: boolean; rtk?: boolean; maxCharsPerMessage?: number } = {}
): { messages: GatewayMessage[]; savedChars: number } {
  let working = options.rtk !== false ? rtkFilter(messages) : [...messages];
  let savedChars = 0;

  const originalLen = JSON.stringify(messages).length;

  working = working.map((m) => {
    let content = String(m.content);
    const before = content.length;

    if (options.caveman !== false && m.role !== 'system') {
      content = cavemanCompress(content);
    }

    if (options.maxCharsPerMessage && content.length > options.maxCharsPerMessage) {
      content = content.slice(0, options.maxCharsPerMessage) + '…[truncated]';
    }

    savedChars += before - content.length;
    return { ...m, content };
  });

  savedChars = Math.max(savedChars, originalLen - JSON.stringify(working).length);
  return { messages: working, savedChars };
}

export function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4);
}
