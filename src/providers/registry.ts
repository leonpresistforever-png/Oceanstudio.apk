import type { ProviderConnection } from './types';
import { getActiveProviderIds } from './activeProviders';

export { getActiveProviderIds, setActiveProviderIds, toggleActiveProvider, addActiveProvider, removeActiveProvider, getActiveProviderId, setActiveProviderId } from './activeProviders';

const STORAGE_KEY = 'ocean-provider-connections';

export function getProviderConnections(): ProviderConnection[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function save(list: ProviderConnection[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

export function getProviderConnection(providerId: string): ProviderConnection | undefined {
  return getProviderConnections().find((c) => c.providerId === providerId);
}

export function upsertProviderConnection(conn: ProviderConnection): void {
  const list = getProviderConnections();
  const idx = list.findIndex((c) => c.providerId === conn.providerId);
  if (idx >= 0) list[idx] = conn;
  else list.push(conn);
  save(list);
}

export function removeProviderConnection(providerId: string): void {
  save(getProviderConnections().filter((c) => c.providerId !== providerId));
}

export function exportProviderAgentConfig(): Record<string, unknown> {
  const connected = getProviderConnections().filter((c) => c.status === 'connected');
  const activeIds = getActiveProviderIds();
  const providers: Record<string, unknown> = {};
  for (const c of connected) {
    providers[c.providerId] = {
      name: c.name,
      models: c.syncedModels ?? [],
      tools: c.syncedTools ?? [],
      skills: c.syncedSkills ?? [],
      accountLabel: c.accountLabel,
      active: activeIds.includes(c.providerId),
      config: Object.fromEntries(
        Object.entries(c.config).filter(([k]) => !k.includes('secret') && !k.includes('token') && !k.includes('key'))
      ),
    };
  }
  return {
    providers,
    activeProviderIds: activeIds,
    activeProviderId: activeIds[0],
    connectedCount: connected.length,
    updatedAt: new Date().toISOString(),
  };
}
