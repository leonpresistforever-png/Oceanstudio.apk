export type VirtualAppSourceType = 'exe' | 'apk' | 'url' | 'port' | 'workspace' | 'pwa';
export type VirtualAppStatus = 'pending' | 'cloning' | 'ready' | 'running' | 'error';

export interface VirtualAppManifest {
  /** Original binary or package path on host */
  sourcePath?: string;
  /** URL for web/PWA clones */
  sourceUrl?: string;
  /** Port binding for dev server clones */
  port?: number;
  /** Workspace subfolder for isolated clone */
  workspaceSubpath?: string;
  /** Environment variables for clone runtime */
  env?: Record<string, string>;
  /** Agent combo to use when operating this clone */
  agentComboId?: string;
  /** Fusion preset id if using fused agent */
  fusionPresetId?: string;
}

export interface VirtualAppInstance {
  id: string;
  name: string;
  icon: string;
  sourceType: VirtualAppSourceType;
  status: VirtualAppStatus;
  manifest: VirtualAppManifest;
  previewUrl?: string;
  viewport: 'website' | 'mobile' | 'desktop';
  createdAt: number;
  lastUsedAt?: number;
  scrapeCapabilities: string[];
  errorMessage?: string;
}

export const DEFAULT_SCRAPE_CAPABILITIES = [
  'window-title',
  'ui-tree',
  'screenshot',
  'clipboard',
  'file-system-sandbox',
  'agent-terminal-bridge',
];

export function createVirtualAppInstance(partial: Partial<VirtualAppInstance> & Pick<VirtualAppInstance, 'name' | 'sourceType'>): VirtualAppInstance {
  return {
    id: partial.id ?? `vapp-${crypto.randomUUID()}`,
    name: partial.name,
    icon: partial.icon ?? '📦',
    sourceType: partial.sourceType,
    status: partial.status ?? 'pending',
    manifest: partial.manifest ?? {},
    viewport: partial.viewport ?? 'desktop',
    createdAt: partial.createdAt ?? Date.now(),
    scrapeCapabilities: partial.scrapeCapabilities ?? [...DEFAULT_SCRAPE_CAPABILITIES],
    previewUrl: partial.previewUrl,
    lastUsedAt: partial.lastUsedAt,
    errorMessage: partial.errorMessage,
  };
}
