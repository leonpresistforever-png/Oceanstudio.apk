import { getOceanAPI } from '../lib/platform';
import type { ProviderConnection } from './types';
import { getProviderConnections, upsertProviderConnection, removeProviderConnection } from './registry';

let syncInFlight: Promise<ProviderConnection[]> | null = null;

/** Sync Electron/backend provider state into localStorage (source of truth on desktop). */
export async function syncProviderConnectionsFromBackend(): Promise<ProviderConnection[]> {
  if (syncInFlight) return syncInFlight;

  syncInFlight = (async () => {
    const api = getOceanAPI();
    if (!api.providers?.list) {
      return getProviderConnections();
    }

    try {
      const remote = (await api.providers.list()) as ProviderConnection[];
      if (!Array.isArray(remote)) return getProviderConnections();

      const local = getProviderConnections();
      const remoteIds = new Set(remote.map((c) => c.providerId));

      for (const conn of remote) {
        upsertProviderConnection(conn);
      }

      for (const localConn of local) {
        if (!remoteIds.has(localConn.providerId)) {
          removeProviderConnection(localConn.providerId);
        }
      }

      return remote.length > 0 ? remote : getProviderConnections();
    } catch {
      return getProviderConnections();
    }
  })();

  try {
    return await syncInFlight;
  } finally {
    syncInFlight = null;
  }
}
