/** Built-in OAuth client credentials — user never enters these manually for supported providers */
export const BUILTIN_OAUTH_CLIENTS: Record<string, {
  clientIdEnv: string;
  clientSecretEnv?: string;
  /** Use Ocean proxy OAuth callback — works on mobile when proxy running */
  useOceanProxy?: boolean;
}> = {
  'google.antigravity': { clientIdEnv: 'GOOGLE_ANTIGRAVITY_CLIENT_ID', clientSecretEnv: 'GOOGLE_ANTIGRAVITY_CLIENT_SECRET', useOceanProxy: true },
  'openai.codex': { clientIdEnv: 'OPENAI_CODEX_CLIENT_ID', clientSecretEnv: 'OPENAI_CODEX_CLIENT_SECRET', useOceanProxy: true },
  'anthropic.claude-code': { clientIdEnv: 'ANTHROPIC_CLIENT_ID', clientSecretEnv: 'ANTHROPIC_CLIENT_SECRET', useOceanProxy: true },
  'github.copilot': { clientIdEnv: 'GITHUB_COPILOT_CLIENT_ID', clientSecretEnv: 'GITHUB_COPILOT_CLIENT_SECRET', useOceanProxy: true },
  'azure.openai': { clientIdEnv: 'AZURE_CLIENT_ID', clientSecretEnv: 'AZURE_CLIENT_SECRET', useOceanProxy: true },
  'google.vertex': { clientIdEnv: 'GOOGLE_CLOUD_CLIENT_ID', clientSecretEnv: 'GOOGLE_CLOUD_CLIENT_SECRET', useOceanProxy: true },
  'notion': { clientIdEnv: 'NOTION_CLIENT_ID', clientSecretEnv: 'NOTION_CLIENT_SECRET', useOceanProxy: true },
  'slack': { clientIdEnv: 'SLACK_CLIENT_ID', clientSecretEnv: 'SLACK_CLIENT_SECRET', useOceanProxy: true },
};

export function resolveOAuthCredentials(providerId: string, config: Record<string, string> = {}): {
  clientId: string;
  clientSecret: string;
  builtin: boolean;
} {
  const builtin = BUILTIN_OAUTH_CLIENTS[providerId];
  const clientId = config.clientId
    || (builtin ? process.env[builtin.clientIdEnv] : '')
    || process.env.OCEAN_OAUTH_CLIENT_ID
    || '';
  const clientSecret = config.clientSecret
    || (builtin?.clientSecretEnv ? process.env[builtin.clientSecretEnv] : '')
    || process.env.OCEAN_OAUTH_CLIENT_SECRET
    || '';
  return { clientId, clientSecret, builtin: Boolean(builtin) };
}

export function shouldUseOceanProxy(providerId: string): boolean {
  return BUILTIN_OAUTH_CLIENTS[providerId]?.useOceanProxy ?? true;
}
