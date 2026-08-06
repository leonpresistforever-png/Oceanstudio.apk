import type { ProviderDefinition } from './types';
import { BUILTIN_OAUTH_CLIENTS } from './oauth-clients';

export type ProviderAuthBadge =
  | 'OAuth Redirect'
  | 'OAuth PKCE'
  | 'API Key'
  | 'Bearer Token'
  | 'Device Flow'
  | 'Web Cookie'
  | 'Gateway URL'
  | 'Manual Config'
  | 'Built-in';

export const AUTH_BADGE_COLORS: Record<ProviderAuthBadge, string> = {
  'OAuth Redirect': '#8b5cf6',
  'OAuth PKCE': '#7c3aed',
  'API Key': '#f59e0b',
  'Bearer Token': '#eab308',
  'Device Flow': '#06b6d4',
  'Web Cookie': '#ec4899',
  'Gateway URL': '#6366f1',
  'Manual Config': '#64748b',
  'Built-in': '#22c55e',
};

export function getProviderAuthBadge(def: ProviderDefinition): ProviderAuthBadge {
  switch (def.authType) {
    case 'oauth_redirect': return 'OAuth Redirect';
    case 'oauth_pkce': return 'OAuth PKCE';
    case 'api_key': return 'API Key';
    case 'bearer_token': return 'Bearer Token';
    case 'device_flow': return 'Device Flow';
    case 'web_cookie': return 'Web Cookie';
    case 'proxy_gateway': return 'Gateway URL';
    case 'embedded': return 'Built-in';
    default: return 'Manual Config';
  }
}

export function canInstantConnect(def: ProviderDefinition): boolean {
  return def.authType === 'embedded' ||
    (def.authType === 'manual' && def.id === 'ollama');
}

export function hasRequiredConfig(def: ProviderDefinition, config: Record<string, string>): boolean {
  if (canInstantConnect(def)) return true;
  if (def.authType === 'oauth_redirect' || def.authType === 'oauth_pkce') return true;
  for (const field of def.envFields ?? []) {
    if (field.required !== false && !config[field.key]?.trim()) return false;
  }
  return true;
}

export function supportsOAuthOnPlatform(
  def: ProviderDefinition,
  platform: 'electron' | 'web' | 'mobile',
  proxyRunning = false
): boolean {
  if (def.authType !== 'oauth_redirect' && def.authType !== 'oauth_pkce') return true;
  // OAuth via Ocean proxy works on all platforms when proxy is running (LAN callback for mobile)
  if (proxyRunning) return true;
  return platform === 'electron';
}

export function isBuiltinOAuthProvider(providerId: string): boolean {
  return providerId in BUILTIN_OAUTH_CLIENTS;
}
