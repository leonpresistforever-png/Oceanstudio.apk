import type { McpConnectorDefinition } from './types';

/** How a connector authenticates — shown on MCP cards */
export type McpAuthConnectMode =
  | 'instant'           // SSE/HTTP with no keys — connect immediately (e.g. Cloudflare Docs)
  | 'api_key'           // API key / bearer token fields
  | 'oauth'             // OAuth redirect flow
  | 'client_credentials' // OAuth client id + secret
  | 'env_vars';         // Generic env configuration (stdio npx)

export interface McpAuthInfo {
  mode: McpAuthConnectMode;
  label: string;
  description: string;
  requiredFields: string[];
  canInstantConnect: boolean;
}

export function getConnectorAuthInfo(def: McpConnectorDefinition): McpAuthInfo {
  const requiredEnv = (def.envFields ?? []).filter((f) => f.required !== false);
  const optionalEnv = (def.envFields ?? []).filter((f) => f.required === false);

  if (def.auth?.type === 'oauth') {
    const needsClient = def.auth.oauthAuthorizeUrl && !def.envFields?.length;
    return {
      mode: needsClient ? 'client_credentials' : 'oauth',
      label: needsClient ? 'OAuth Client' : 'OAuth',
      description: needsClient
        ? 'Requires OAuth Client ID and Secret, then browser login'
        : 'Browser OAuth login — may need Client ID',
      requiredFields: needsClient ? ['clientId', 'clientSecret'] : ['clientId'],
      canInstantConnect: false,
    };
  }

  if (requiredEnv.length > 0) {
    const isApiKey = requiredEnv.every((f) =>
      /api[_-]?key|token|secret|pat|auth/i.test(f.key) || f.secret
    );
    return {
      mode: isApiKey ? 'api_key' : 'env_vars',
      label: isApiKey ? 'API Key' : 'Config Required',
      description: isApiKey
        ? 'Enter API key or token to connect'
        : `Fill in: ${requiredEnv.map((f) => f.label).join(', ')}`,
      requiredFields: requiredEnv.map((f) => f.key),
      canInstantConnect: false,
    };
  }

  if (optionalEnv.length > 0) {
    return {
      mode: 'api_key',
      label: 'Optional Key',
      description: 'Connects instantly; API key optional for higher limits',
      requiredFields: [],
      canInstantConnect: true,
    };
  }

  // No auth, no env — instant SSE or local stdio
  if (def.transport === 'sse' || def.transport === 'http') {
    return {
      mode: 'instant',
      label: 'Instant Connect',
      description: 'Cloud SSE — no API key or client ID needed',
      requiredFields: [],
      canInstantConnect: true,
    };
  }

  // Local stdio without env — runs via npx on desktop
  return {
    mode: 'instant',
    label: 'Local npx',
    description: 'Runs locally via npx — no configuration needed',
    requiredFields: [],
    canInstantConnect: def.hosting === 'local',
  };
}

export function hasRequiredConfig(def: McpConnectorDefinition, config: Record<string, string>): boolean {
  const info = getConnectorAuthInfo(def);
  if (info.canInstantConnect && info.requiredFields.length === 0) return true;

  for (const key of info.requiredFields) {
    if (!config[key]?.trim()) return false;
  }

  for (const field of def.envFields ?? []) {
    if (field.required !== false && !config[field.key]?.trim()) return false;
  }

  return true;
}

export const AUTH_BADGE_COLORS: Record<McpAuthConnectMode, string> = {
  instant: '#22c55e',
  api_key: '#f59e0b',
  oauth: '#8b5cf6',
  client_credentials: '#ec4899',
  env_vars: '#64748b',
};
