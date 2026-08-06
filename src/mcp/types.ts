export type McpTransport = 'stdio' | 'sse' | 'http' | 'websocket';
export type McpHosting = 'local' | 'cloud';
export type McpAuthType = 'none' | 'api_key' | 'oauth' | 'bearer' | 'client_credentials';
export type McpPlatform = 'electron' | 'web' | 'mobile';

export interface McpEnvField {
  key: string;
  label: string;
  secret?: boolean;
  placeholder?: string;
  required?: boolean;
}

export interface McpConnectorDefinition {
  id: string;
  name: string;
  description: string;
  category: string;
  transport: McpTransport;
  hosting: McpHosting;
  platforms: McpPlatform[];
  command?: string;
  args?: string[];
  url?: string;
  headers?: Record<string, string>;
  envFields?: McpEnvField[];
  auth?: {
    type: McpAuthType;
    oauthAuthorizeUrl?: string;
    oauthTokenUrl?: string;
    oauthScopes?: string[];
    tokenHeader?: string;
  };
  verified: boolean;
  docsUrl?: string;
  icon?: string;
}

export interface McpConnection {
  connectorId: string;
  name: string;
  transport: McpTransport;
  hosting: McpHosting;
  config: Record<string, string>;
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  error?: string;
  connectedAt?: number;
  custom?: boolean;
}

export interface McpConnectionStatus {
  id: string;
  status: McpConnection['status'];
  message: string;
  toolCount?: number;
}
