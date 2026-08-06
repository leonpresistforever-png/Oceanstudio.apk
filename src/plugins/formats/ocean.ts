import type { OceanPluginManifest } from '../types';

export function parseOceanPluginJson(raw: unknown): OceanPluginManifest | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  if (!o.id || !o.name || !o.version) return null;

  return {
    id: String(o.id),
    name: String(o.name),
    version: String(o.version),
    description: String(o.description ?? ''),
    author: o.author ? String(o.author) : undefined,
    homepage: o.homepage ? String(o.homepage) : undefined,
    repository: o.repository ? String(o.repository) : undefined,
    icon: o.icon ? String(o.icon) : undefined,
    platforms: Array.isArray(o.platforms) ? o.platforms as OceanPluginManifest['platforms'] : ['electron'],
    type: (o.type as OceanPluginManifest['type']) ?? 'tool',
    tags: Array.isArray(o.tags) ? o.tags.map(String) : [],
    mcp: o.mcp as OceanPluginManifest['mcp'],
    mcpServers: o.mcpServers as OceanPluginManifest['mcpServers'],
    tools: o.tools as OceanPluginManifest['tools'],
    configSchema: o.configSchema as OceanPluginManifest['configSchema'],
    config: o.config as OceanPluginManifest['config'],
    mobile: o.mobile as OceanPluginManifest['mobile'],
    web: o.web as OceanPluginManifest['web'],
    electron: o.electron as OceanPluginManifest['electron'],
    source: (o.source as OceanPluginManifest['source']) ?? 'local',
    verified: Boolean(o.verified),
  };
}

export function createPluginTemplate(platform: 'electron' | 'web' | 'mobile'): OceanPluginManifest {
  const base = {
    version: '1.0.0',
    description: 'Custom Ocean.studio plugin',
    author: 'You',
    type: 'tool' as const,
    source: 'local' as const,
    tags: ['custom'],
    tools: [{ name: 'example_tool', description: 'Example tool for the agent' }],
    configSchema: {
      apiKey: { type: 'string', description: 'API key (optional)', default: '' },
    },
    config: { apiKey: '' },
  };

  if (platform === 'electron') {
    return {
      ...base,
      id: 'local.my-desktop-plugin',
      name: 'My Desktop Plugin',
      platforms: ['electron'],
      electron: { python: 'plugin.py', entry: 'main' },
      mcp: { command: 'npx', args: ['-y', '@modelcontextprotocol/server-filesystem', '.'] },
    };
  }
  if (platform === 'web') {
    return {
      ...base,
      id: 'local.my-web-plugin',
      name: 'My Web Plugin',
      platforms: ['web'],
      web: { entry: 'https://example.com/plugin', permissions: ['storage'] },
      mcp: { transport: 'sse', url: 'https://example.com/mcp/sse' },
    };
  }
  return {
    ...base,
    id: 'local.my-mobile-plugin',
    name: 'My Mobile Plugin',
    platforms: ['mobile'],
    mobile: { pkg: 'my-tool', bootstrap: true },
    mcp: { command: 'pkg', args: ['install', 'my-tool'] },
  };
}
