import type { CircuitState } from './types.js';

const FAILURE_THRESHOLD = 3;
const COOLDOWN_MS = 30_000;
const MAX_COOLDOWN_MS = 300_000;

export class CircuitBreaker {
  private states = new Map<string, CircuitState>();

  get(connectionId: string): CircuitState {
    return this.states.get(connectionId) ?? {
      connectionId,
      failures: 0,
      status: 'healthy',
    };
  }

  isAvailable(connectionId: string): boolean {
    const state = this.get(connectionId);
    if (state.status === 'open' && state.cooldownUntil && Date.now() < state.cooldownUntil) {
      return false;
    }
    if (state.cooldownUntil && Date.now() >= state.cooldownUntil) {
      state.status = 'degraded';
      state.failures = Math.floor(state.failures / 2);
    }
    return state.status !== 'open';
  }

  recordSuccess(connectionId: string): void {
    const state = this.get(connectionId);
    state.failures = 0;
    state.status = 'healthy';
    state.cooldownUntil = undefined;
    this.states.set(connectionId, state);
  }

  recordFailure(connectionId: string, retriable: boolean): void {
    const state = this.get(connectionId);
    state.failures += 1;
    state.lastFailure = Date.now();

    if (!retriable) {
      state.status = 'degraded';
    } else if (state.failures >= FAILURE_THRESHOLD) {
      state.status = 'open';
      const backoff = Math.min(COOLDOWN_MS * Math.pow(2, state.failures - FAILURE_THRESHOLD), MAX_COOLDOWN_MS);
      state.cooldownUntil = Date.now() + backoff;
    } else {
      state.status = 'degraded';
    }
    this.states.set(connectionId, state);
  }

  allStates(): CircuitState[] {
    return [...this.states.values()];
  }

  reset(connectionId?: string): void {
    if (connectionId) this.states.delete(connectionId);
    else this.states.clear();
  }
}

export function isRetriableStatus(status: number): boolean {
  return status === 429 || status === 502 || status === 503 || status === 529;
}

export async function cooldownAwareRetry<T>(
  fn: () => Promise<T>,
  maxAttempts = 3,
  baseDelayMs = 1000
): Promise<T> {
  let lastError: Error | undefined;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
      if (attempt < maxAttempts - 1) {
        await new Promise((r) => setTimeout(r, baseDelayMs * Math.pow(2, attempt)));
      }
    }
  }
  throw lastError ?? new Error('Retry exhausted');
}
