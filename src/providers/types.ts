export type ProviderPlatform = 'electron' | 'web' | 'mobile';

export type ProviderCategory =
  | 'llm'
  | 'coding'
  | 'gateway'
  | 'cloud'
  | 'dev'
  | 'scraping'
  | 'productivity'
  | 'database';

/** How the user authenticates — mirrors OmniRoute provider connection modes */
export type ProviderAuthType =
  | 'oauth_redirect'   // Browser redirect → localhost callback
  | 'oauth_pkce'       // PKCE OAuth (OpenAI Codex, Claude Code, Antigravity)
  | 'api_key'          // API key / secret
  | 'bearer_token'     // Personal access token
  | 'device_flow'      // Device code (AWS Kiro-style)
  | 'web_cookie'       // Paste session cookies
  | 'proxy_gateway'    // Connect via gateway URL (OmniRoute, LiteLLM)
  | 'manual'           // Custom endpoint + headers
  | 'embedded';        // Uses built-in Ocean service (GitHub, Cloud Shell)

export type PipelineFormat = 'openai' | 'anthropic' | 'google' | 'mcp' | 'custom';

export interface ProviderEnvField {
  key: string;
  label: string;
  secret?: boolean;
  required?: boolean;
  placeholder?: string;
}

export interface ProviderOAuthConfig {
  authorizeUrl: string;
  tokenUrl: string;
  scopes: string[];
  usePkce?: boolean;
  clientIdEnv?: string;
  clientSecretEnv?: string;
  redirectPort?: number;
  redirectPath?: string;
}

export interface ProviderPipelineConfig {
  requestFormat: PipelineFormat;
  responseFormat: PipelineFormat;
  chatEndpoint?: string;
  modelsEndpoint?: string;
  /** Step 2: external provider build/check nudge URLs */
  nudgeBuildEndpoint?: string;
  nudgeCheckEndpoint?: string;
}

export interface ProviderDefinition {
  id: string;
  name: string;
  description: string;
  category: ProviderCategory;
  authType: ProviderAuthType;
  platforms: ProviderPlatform[];
  brandColor: string;
  logoLetter: string;
  docsUrl?: string;
  websiteUrl?: string;
  oauth?: ProviderOAuthConfig;
  envFields?: ProviderEnvField[];
  pipeline: ProviderPipelineConfig;
  /** Models available when connected (synced or static) */
  defaultModels?: string[];
  /** Provider exposes skills/tools/system files */
  features: {
    models: boolean;
    skills: boolean;
    tools: boolean;
    systemFiles: boolean;
    proxy: boolean;
  };
  verified: boolean;
  freeTier?: boolean;
  /** Built-in Ocean service key (github, cloudshell) */
  embeddedService?: string;
}

export interface ProviderConnection {
  providerId: string;
  name: string;
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  config: Record<string, string>;
  error?: string;
  connectedAt?: number;
  accountLabel?: string;
  avatarUrl?: string;
  syncedModels?: string[];
  syncedTools?: { name: string; description: string }[];
  syncedSkills?: string[];
}

export interface ProviderConnectionStatus {
  providerId: string;
  status: ProviderConnection['status'];
  message: string;
  accountLabel?: string;
}

export interface ProxyServerStatus {
  running: boolean;
  port: number;
  url: string;
  pid?: number;
  message: string;
}
