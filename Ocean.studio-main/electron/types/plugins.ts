export type PluginPlatform = 'electron' | 'web' | 'mobile';
export type PluginType = 'mcp' | 'tool' | 'agent' | 'ui' | 'webhook' | 'connector';

export interface McpServerConfig {
  command?: string;
  args?: string[];
  env?: Record<string, string>;
  cwd?: string;
  url?: string;
  transport?: 'stdio' | 'sse' | 'http';
}

export interface PluginTool {
  name: string;
  description: string;
  inputSchema?: Record<string, unknown>;
}

export interface OceanPluginManifest {
  id: string;
  name: string;
  version: string;
  description: string;
  author?: string;
  homepage?: string;
  repository?: string;
  icon?: string;
  platforms: PluginPlatform[];
  type: PluginType;
  tags?: string[];
  mcp?: McpServerConfig;
  mcpServers?: Record<string, McpServerConfig>;
  tools?: PluginTool[];
  configSchema?: Record<string, { type: string; default?: unknown; description?: string }>;
  config?: Record<string, unknown>;
  mobile?: { pkg?: string; bootstrap?: boolean };
  web?: { entry?: string; permissions?: string[] };
  electron?: { entry?: string; python?: string; binary?: string };
  source: 'official' | 'opensource' | 'local' | 'agent';
  verified?: boolean;
  downloads?: number;
  rating?: number;
}

export interface InstalledPlugin {
  manifest: OceanPluginManifest;
  enabled: boolean;
  installedAt: number;
  config: Record<string, unknown>;
  status: 'idle' | 'running' | 'error' | 'installing';
  error?: string;
  localPath?: string;
}
