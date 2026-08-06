import type { PlaygroundToolCategory, PlaygroundToolKind } from './types';

export interface CustomMarketplaceEntry {
  name: string;
  description: string;
  kind: PlaygroundToolKind;
  icon: string;
  envRequirements: string;
  installGuide: string;
  featureTags: PlaygroundToolCategory[];
  platforms: ('electron' | 'android' | 'web' | 'cloud')[];
}

/** Default environment template for user-added MCP connectors */
export const MCP_ENV_TEMPLATE = `# MCP connector environment (.env or Cursor MCP config)
MCP_SERVER_NAME=my-custom-mcp
MCP_TRANSPORT=stdio          # stdio | sse | http
MCP_COMMAND=npx              # or node, python, docker
MCP_ARGS=-y @org/mcp-server
# API keys (if remote MCP)
MCP_API_KEY=
MCP_BASE_URL=https://api.example.com/mcp
# Optional OAuth
MCP_CLIENT_ID=
MCP_CLIENT_SECRET=`;

/** Default environment template for user-added plugins */
export const PLUGIN_ENV_TEMPLATE = `# Ocean plugin environment
PLUGIN_ID=my-custom-plugin
PLUGIN_ENTRY=./index.js      # or main.py for Python plugins
NODE_VERSION=20+
PYTHON_VERSION=3.10+
# Required API keys
API_KEY=
# Optional GPU (Electron local plugins)
CUDA_VISIBLE_DEVICES=0
HF_TOKEN=`;

export const CUSTOM_INSTALL_STEPS: Record<'mcp' | 'plugin', string[]> = {
  mcp: [
    'Open Cursor Settings → MCP → Add new server',
    'Paste your MCP command or SSE/HTTP URL',
    'Set environment variables from the template below',
    'Restart Cursor or reload the Ocean workspace',
    'Test connection from Playground → MCP Store → your connector',
  ],
  plugin: [
    'Place plugin files in workspace/.ocean/plugins/<plugin-id>/',
    'Copy env vars to .env or plugin manifest config',
    'Run: ocean plugin install <path-or-npm-package>',
    'Enable the plugin in Playground → Plugin Store',
    'Assign feature tags so it appears in Image, 3D, Music, etc.',
  ],
};

export const FEATURE_ENV_HINTS: Partial<Record<PlaygroundToolCategory, string>> = {
  'media-3d': 'MESHY_API_KEY, TRIPO_API_KEY, REPLICATE_API_TOKEN, HF_TOKEN, FAL_KEY',
  'media-image': 'OPENAI_API_KEY, REPLICATE_API_TOKEN, STABILITY_API_KEY, HF_TOKEN',
  'media-video': 'GOOGLE_API_KEY, RUNWAY_API_KEY, REPLICATE_API_TOKEN',
  'audio-tts': 'ELEVENLABS_API_KEY, GOOGLE_APPLICATION_CREDENTIALS, OPENAI_API_KEY',
  'audio-music': 'SUNO_API_KEY, ELEVENLABS_API_KEY, GOOGLE_API_KEY',
  upscale: 'REPLICATE_API_TOKEN, TOPAZ_API_KEY',
  research: 'FIRECRAWL_API_KEY, SERPAPI_KEY, BRAVE_SEARCH_API_KEY',
  agents: 'ANTHROPIC_API_KEY, OPENAI_API_KEY — agent runtime keys',
  recording: 'No API keys — Electron/APK native capture only',
  'active-bot': 'Accessibility permissions on APK; full OS access on Electron',
  skills: 'No API keys — upload skill.md or install from marketplace',
};

export function buildCustomToolId(kind: 'mcp' | 'plugin', name: string): string {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return `custom-${kind}-${slug}-${Date.now().toString(36)}`;
}

export function validateCustomEntry(entry: Partial<CustomMarketplaceEntry>): string | null {
  if (!entry.name?.trim()) return 'Name is required';
  if (!entry.description?.trim()) return 'Description is required';
  if (!entry.envRequirements?.trim()) return 'Environment requirements are required';
  if (!entry.featureTags?.length) return 'Select at least one playground feature';
  return null;
}
