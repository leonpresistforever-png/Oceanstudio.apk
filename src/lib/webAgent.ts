/** Web/mobile agent — real provider inference + workspace-aware fallback */

import { runAgentInference } from './providerInference';
import { recordAgentResponse } from './agentSession';

export type WebAgentEvent = {
  type: string;
  id: string;
  timestamp: number;
  summary: string;
  detail?: string;
  duration?: number;
  status?: string;
  metadata?: Record<string, unknown>;
};

type Listener = (event: WebAgentEvent) => void;

const listeners = new Set<Listener>();
let processing = false;
const queue: { message: string; context: Record<string, unknown>; resolve: (text: string) => void }[] = [];

export function onWebAgentEvent(cb: Listener): () => void {
  listeners.add(cb);
  return () => { listeners.delete(cb); };
}

function emit(event: WebAgentEvent) {
  listeners.forEach((cb) => cb(event));
}

async function processWebAgentQueue(): Promise<void> {
  if (processing) return;
  processing = true;

  while (queue.length > 0) {
    const item = queue.shift()!;
    const text = await executeWebAgent(item.message, item.context);
    item.resolve(text);
  }

  processing = false;
}

export async function runWebAgent(message: string, context: Record<string, unknown>): Promise<string> {
  return new Promise((resolve) => {
    queue.push({ message, context, resolve });
    void processWebAgentQueue();
  });
}

async function executeWebAgent(message: string, context: Record<string, unknown>): Promise<string> {
  const phase = context._multiAgentPhase as string | undefined;
  const isSubAgent = phase === 'parallel-worker' || phase === 'collaborative' || phase === 'parallel-plan' || phase === 'parallel-synthesize';
  const member = context.multiAgentMember as { memberName?: string; providerId?: string; modelId?: string } | undefined;

  const thoughtId = crypto.randomUUID();
  const start = Date.now();

  emit({
    type: 'thought',
    id: thoughtId,
    timestamp: start,
    summary: isSubAgent ? `${member?.memberName ?? 'Sub-agent'} working` : 'Analyzing request',
    status: 'running',
  });

  const inference = await runAgentInference(message, context);

  emit({
    type: 'thought',
    id: thoughtId,
    timestamp: Date.now(),
    summary: isSubAgent ? `${member?.memberName ?? 'Agent'} complete` : `Response ready (${inference.source})`,
    detail: inference.content.slice(0, 800),
    duration: Date.now() - start,
    status: 'completed',
    metadata: { providerId: inference.providerId, model: inference.model, source: inference.source },
  });

  const actionId = crypto.randomUUID();
  emit({
    type: 'action',
    id: actionId,
    timestamp: Date.now(),
    summary: isSubAgent
      ? `${member?.memberName ?? 'Agent'}: ${inference.model ?? inference.providerId}`
      : `Inference via ${inference.source}`,
    status: 'running',
    metadata: { phase, providerId: inference.providerId },
  });

  emit({
    type: 'result',
    id: actionId,
    timestamp: Date.now(),
    summary: inference.content.slice(0, 120) + (inference.content.length > 120 ? '...' : ''),
    detail: inference.content,
    status: 'completed',
    metadata: { responseText: inference.content },
  });

  recordAgentResponse(inference.content, {
    providerId: inference.providerId,
    source: inference.source,
  });

  return inference.content;
}

export function pauseWebAgent() {
  queue.length = 0;
  processing = false;
  emit({
    type: 'status',
    id: crypto.randomUUID(),
    timestamp: Date.now(),
    summary: 'Agent session paused',
    status: 'paused',
  });
}

export function getWebAgentStatus(): string {
  return processing ? 'running' : 'idle';
}
