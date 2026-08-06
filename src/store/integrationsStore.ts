import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { IntegrationDefinition } from '../integrations/catalog';

export interface ConnectedIntegration {
  id: string;
  connectedAt: number;
  accountLabel?: string;
  config?: Record<string, unknown>;
}

interface IntegrationsState {
  connected: ConnectedIntegration[];
  pinned: string[];
  connect: (id: string, label?: string, config?: Record<string, unknown>) => void;
  disconnect: (id: string) => void;
  isConnected: (id: string) => boolean;
  togglePin: (id: string) => void;
  getConnectedDefs: (catalog: IntegrationDefinition[]) => IntegrationDefinition[];
}

export const useIntegrationsStore = create<IntegrationsState>()(
  persist(
    (set, get) => ({
      connected: [
        { id: 'github', connectedAt: Date.now(), accountLabel: 'GitHub' },
        { id: 'gcp', connectedAt: Date.now(), accountLabel: 'Google Cloud' },
        { id: 'cursor', connectedAt: Date.now(), accountLabel: 'Cursor' },
        { id: 'antigravity-ide', connectedAt: Date.now(), accountLabel: 'Antigravity' },
        { id: 'openai', connectedAt: Date.now(), accountLabel: 'OpenAI' },
      ],
      pinned: ['github', 'slack', 'cursor', 'gitlab', 'figma'],

      connect: (id, accountLabel, config) =>
        set((s) => ({
          connected: [
            { id, connectedAt: Date.now(), accountLabel, config },
            ...s.connected.filter((c) => c.id !== id),
          ],
        })),

      disconnect: (id) =>
        set((s) => ({ connected: s.connected.filter((c) => c.id !== id) })),

      isConnected: (id) => get().connected.some((c) => c.id === id),

      togglePin: (id) =>
        set((s) => ({
          pinned: s.pinned.includes(id)
            ? s.pinned.filter((p) => p !== id)
            : [...s.pinned, id],
        })),

      getConnectedDefs: (catalog) => {
        const ids = new Set(get().connected.map((c) => c.id));
        return catalog.filter((d) => ids.has(d.id));
      },
    }),
    { name: 'ocean-integrations-store' }
  )
);
