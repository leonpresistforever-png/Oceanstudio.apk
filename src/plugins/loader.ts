import type { InstalledPlugin, OceanPluginManifest, PluginPlatform } from './types';
import { getInstalledPlugins, installPlugin, setPluginEnabled, uninstallPlugin, updatePluginStatus } from './registry';
import { validateMcpConfig } from './formats/mcp';

export function filterByPlatform(plugins: OceanPluginManifest[], platform: PluginPlatform): OceanPluginManifest[] {
  return plugins.filter((p) => p.platforms.includes(platform));
}

export async function installAndStart(
  manifest: OceanPluginManifest,
  api: { plugins?: { start: (id: string) => Promise<void>; install: (m: OceanPluginManifest) => Promise<void> } }
): Promise<InstalledPlugin> {
  if (manifest.mcp) {
    const err = validateMcpConfig(manifest.mcp);
    if (err) throw new Error(err);
  }

  if (api.plugins) {
    await api.plugins.install(manifest);
  } else {
    installPlugin(manifest);
  }

  const installed = getInstalledPlugins().find((p) => p.manifest.id === manifest.id)!;
  updatePluginStatus(manifest.id, 'installing');

  try {
    if (api.plugins && manifest.type === 'mcp') {
      await api.plugins.start(manifest.id);
    }
    updatePluginStatus(manifest.id, 'running');
  } catch (e) {
    updatePluginStatus(manifest.id, 'error', e instanceof Error ? e.message : 'Start failed');
  }

  return installed;
}

export function getActiveTools(platform: PluginPlatform): { pluginId: string; tools: { name: string; description: string }[] }[] {
  return getInstalledPlugins()
    .filter((p) => p.enabled && p.manifest.platforms.includes(platform))
    .map((p) => ({
      pluginId: p.manifest.id,
      tools: p.manifest.tools ?? [],
    }))
    .filter((x) => x.tools.length > 0);
}

export { installPlugin, uninstallPlugin, setPluginEnabled, getInstalledPlugins };
