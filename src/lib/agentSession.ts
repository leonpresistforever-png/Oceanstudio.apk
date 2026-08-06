import { estimateTokens } from './promptProcessor';

export interface AgentSessionStats {
  sessionId: string;
  startedAt: number;
  messageCount: number;
  userMessages: number;
  agentResponses: number;
  estimatedInputTokens: number;
  estimatedOutputTokens: number;
  lastActivityAt: number;
  providerId?: string;
  source?: string;
}

let currentSession: AgentSessionStats = createSession();

function createSession(): AgentSessionStats {
  return {
    sessionId: `sess_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    startedAt: Date.now(),
    messageCount: 0,
    userMessages: 0,
    agentResponses: 0,
    estimatedInputTokens: 0,
    estimatedOutputTokens: 0,
    lastActivityAt: Date.now(),
  };
}

export function getAgentSession(): AgentSessionStats {
  return { ...currentSession };
}

export function resetAgentSession(): AgentSessionStats {
  currentSession = createSession();
  return getAgentSession();
}

export function recordUserMessage(text: string): void {
  currentSession.messageCount += 1;
  currentSession.userMessages += 1;
  currentSession.estimatedInputTokens += estimateTokens(text);
  currentSession.lastActivityAt = Date.now();
}

export function recordAgentResponse(text: string, meta?: { providerId?: string; source?: string }): void {
  currentSession.messageCount += 1;
  currentSession.agentResponses += 1;
  currentSession.estimatedOutputTokens += estimateTokens(text);
  currentSession.lastActivityAt = Date.now();
  if (meta?.providerId) currentSession.providerId = meta.providerId;
  if (meta?.source) currentSession.source = meta.source;
}

export function serializeSessionForAgent(): string {
  const s = currentSession;
  return [
    '## Agent Session',
    `ID: ${s.sessionId}`,
    `Messages: ${s.messageCount} (${s.userMessages} user, ${s.agentResponses} agent)`,
    `Est. tokens — in: ${s.estimatedInputTokens}, out: ${s.estimatedOutputTokens}`,
    s.providerId ? `Last provider: ${s.providerId} (${s.source ?? 'unknown'})` : '',
  ].filter(Boolean).join('\n');
}
