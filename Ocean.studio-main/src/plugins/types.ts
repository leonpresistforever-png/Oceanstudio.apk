/** Platform targets for plugin compatibility */
export type PluginPlatform = 'electron' | 'web' | 'mobile';

/** Plugin runtime types — mirrors MCP, VS Code tools, and Ocean native */
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
  /** Unique plugin id (reverse-domain style) */
  id: string;
  name: string;
  version: string;
  description: string;
  author?: string;
  homepage?: string;
  repository?: string;
  icon?: string;
  /** Which platforms this plugin supports */
  platforms: PluginPlatform[];
  type: PluginType;
  tags?: string[];
  /** MCP server config (Cursor / Claude Code / Codex compatible) */
  mcp?: McpServerConfig;
  /** Multiple MCP servers in one plugin */
  mcpServers?: Record<string, McpServerConfig>;
  /** Declared tools exposed to agent */
  tools?: PluginTool[];
  /** User-configurable settings schema */
  configSchema?: Record<string, { type: string; default?: unknown; description?: string }>;
  /** Default config values */
  config?: Record<string, unknown>;
  /** Mobile-specific: termux pkg name or asset bundle */
  mobile?: { pkg?: string; bootstrap?: boolean };
  /** Web-specific: service worker or iframe embed */
  web?: { entry?: string; permissions?: string[] };
  /** Desktop-specific: python entry, binary path */
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

export interface MarketplaceSearchResult {
  manifest: OceanPluginManifest;
  matchScore: number;
  sourceUrl: string;
}

export type MarketplaceTab = 'official' | 'opensource' | 'installed' | 'create';
