import type { McpServerConfig, OceanPluginManifest, PluginPlatform } from '../types';

/** Parse Cursor / Claude Code / Codex style mcp.json */
export function parseMcpJson(
  json: Record<string, unknown>,
  platforms: PluginPlatform[] = ['electron', 'web']
): OceanPluginManifest[] {
  const servers = json.mcpServers as Record<string, McpServerConfig> | undefined;
  if (!servers) return [];

  return Object.entries(servers).map(([key, config]) => ({
    id: `mcp.${key}`,
    name: key.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
    version: '1.0.0',
    description: `MCP server: ${key}`,
    platforms,
    type: 'mcp' as const,
    mcp: config,
    mcpServers: { [key]: config },
    source: 'local' as const,
    tags: ['mcp', 'imported'],
  }));
}

/** Convert Ocean plugin to mcp.json format for export */
export function toMcpJson(plugins: OceanPluginManifest[]): Record<string, unknown> {
  const mcpServers: Record<string, McpServerConfig> = {};
  for (const p of plugins) {
    if (p.mcpServers) Object.assign(mcpServers, p.mcpServers);
    else if (p.mcp) mcpServers[p.id.replace(/^mcp\./, '')] = p.mcp;
  }
  return { mcpServers };
}

/** Validate MCP server config */
export function validateMcpConfig(config: McpServerConfig): string | null {
  if (config.transport === 'sse' || config.transport === 'http') {
    if (!config.url) return 'HTTP/SSE transport requires url';
    return null;
  }
  if (!config.command) return 'stdio transport requires command';
  return null;
}
