import type { AutoComboDecision, ConnectionHealth, ModelCombo } from './types.js';
import { CircuitBreaker } from './circuit-breaker.js';

export interface RouteCandidate {
  connectionId: string;
  providerId: string;
  model: string;
  health: ConnectionHealth;
}

/** 12-factor Auto-Combo scoring — higher is better */
export function scoreCandidate(candidate: RouteCandidate, circuit: CircuitBreaker): number {
  if (!circuit.isAvailable(candidate.connectionId)) return -1;

  const h = candidate.health;
  let score = 0;

  score += h.successRate * 30;
  score += Math.max(0, 20 - h.latencyMs / 100);
  score += h.quotaHeadroom * 25;
  score += Math.max(0, 15 - h.costPer1kTokens * 100);
  score += Math.max(0, 10 - (Date.now() - h.lastUsed) / 60_000);

  return Math.round(score * 100) / 100;
}

export function selectRoute(
  candidates: RouteCandidate[],
  combo: ModelCombo | undefined,
  circuit: CircuitBreaker,
  enableAutoCombo: boolean
): { winner: RouteCandidate; decision: AutoComboDecision } | null {
  const available = candidates.filter((c) => circuit.isAvailable(c.connectionId));
  if (!available.length) return null;

  const requestId = `req_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const scores: Record<string, number> = {};

  let ordered: RouteCandidate[];

  if (combo?.strategy === 'fallback' || combo?.strategy === 'round_robin') {
    const seq = combo.fallbackSequence;
    ordered = seq
      .map((id) => available.find((c) => c.connectionId === id || c.providerId === id))
      .filter((c): c is RouteCandidate => !!c);
    if (!ordered.length) ordered = available;
  } else if (enableAutoCombo) {
    for (const c of available) {
      scores[c.connectionId] = scoreCandidate(c, circuit);
    }
    ordered = [...available].sort((a, b) => (scores[b.connectionId] ?? 0) - (scores[a.connectionId] ?? 0));
  } else {
    ordered = available;
    for (const c of available) scores[c.connectionId] = 50;
  }

  const winner = ordered[0];
  if (!winner) return null;

  if (!scores[winner.connectionId]) {
    scores[winner.connectionId] = scoreCandidate(winner, circuit);
  }

  return {
    winner,
    decision: {
      id: `dec_${Date.now()}`,
      requestId,
      winningConnectionId: winner.connectionId,
      candidateScores: scores,
      reason: combo ? `combo:${combo.strategy}` : enableAutoCombo ? 'auto_combo' : 'default',
      timestamp: Date.now(),
    },
  };
}

export const DEFAULT_COMBOS: ModelCombo[] = [
  {
    id: 'ocean-smart',
    name: 'Ocean Smart Route',
    strategy: 'auto_combo',
    fallbackSequence: ['openai', 'anthropic', 'google', 'groq', 'deepseek'],
    enabled: true,
    createdAt: Date.now(),
  },
  {
    id: 'ocean-cost',
    name: 'Cost Optimal',
    strategy: 'cost_optimal',
    fallbackSequence: ['groq', 'deepseek', 'google', 'openai'],
    enabled: true,
    createdAt: Date.now(),
  },
  {
    id: 'ocean-coding',
    name: 'Coding Agents',
    strategy: 'fallback',
    fallbackSequence: ['anthropic', 'openai', 'antigravity', 'github-copilot'],
    modelAliases: {
      'gpt-4': 'gpt-4o',
      'claude-3-5-sonnet': 'claude-sonnet-4-20250514',
    },
    enabled: true,
    createdAt: Date.now(),
  },
];
