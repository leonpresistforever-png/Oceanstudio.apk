import type { InstalledPlugin, OceanPluginManifest } from './types';

const STORAGE_KEY = 'ocean-installed-plugins';

function loadRaw(): InstalledPlugin[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
}

function saveRaw(plugins: InstalledPlugin[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(plugins));
}

export function getInstalledPlugins(): InstalledPlugin[] {
  return loadRaw();
}

export function getInstalledPlugin(id: string): InstalledPlugin | undefined {
  return loadRaw().find((p) => p.manifest.id === id);
}

export function installPlugin(manifest: OceanPluginManifest, config?: Record<string, unknown>): InstalledPlugin {
  const plugins = loadRaw();
  const existing = plugins.findIndex((p) => p.manifest.id === manifest.id);
  const entry: InstalledPlugin = {
    manifest,
    enabled: true,
    installedAt: Date.now(),
    config: config ?? manifest.config ?? {},
    status: 'idle',
  };
  if (existing >= 0) plugins[existing] = entry;
  else plugins.push(entry);
  saveRaw(plugins);
  return entry;
}

export function uninstallPlugin(id: string): void {
  saveRaw(loadRaw().filter((p) => p.manifest.id !== id));
}

export function setPluginEnabled(id: string, enabled: boolean): void {
  const plugins = loadRaw();
  const p = plugins.find((x) => x.manifest.id === id);
  if (p) {
    p.enabled = enabled;
    saveRaw(plugins);
  }
}

export function updatePluginConfig(id: string, config: Record<string, unknown>): void {
  const plugins = loadRaw();
  const p = plugins.find((x) => x.manifest.id === id);
  if (p) {
    p.config = config;
    saveRaw(plugins);
  }
}

export function updatePluginStatus(
  id: string,
  status: InstalledPlugin['status'],
  error?: string
): void {
  const plugins = loadRaw();
  const p = plugins.find((x) => x.manifest.id === id);
  if (p) {
    p.status = status;
    p.error = error;
    saveRaw(plugins);
  }
}
