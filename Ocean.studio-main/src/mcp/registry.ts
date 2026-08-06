import type { McpConnection, McpConnectorDefinition } from './types';
import { getConnectorById } from './catalog';

const STORAGE_KEY = 'ocean-mcp-connections';
const TOKENS_KEY = 'ocean-mcp-tokens';

export function getConnections(): McpConnection[] {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function saveConnections(list: McpConnection[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

export function getConnection(connectorId: string): McpConnection | undefined {
  return getConnections().find((c) => c.connectorId === connectorId);
}

export function upsertConnection(conn: McpConnection): void {
  const list = getConnections();
  const idx = list.findIndex((c) => c.connectorId === conn.connectorId);
  if (idx >= 0) list[idx] = conn;
  else list.push(conn);
  saveConnections(list);
}

export function removeConnection(connectorId: string): void {
  saveConnections(getConnections().filter((c) => c.connectorId !== connectorId));
}

export function updateConnectionStatus(
  connectorId: string,
  status: McpConnection['status'],
  error?: string
): void {
  const conn = getConnection(connectorId);
  if (!conn) return;
  conn.status = status;
  conn.error = error;
  if (status === 'connected') conn.connectedAt = Date.now();
  upsertConnection(conn);
}

export function getTokens(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(TOKENS_KEY) ?? '{}');
  } catch {
    return {};
  }
}

export function setToken(connectorId: string, token: string): void {
  const tokens = getTokens();
  tokens[connectorId] = token;
  localStorage.setItem(TOKENS_KEY, JSON.stringify(tokens));
}

export function getToken(connectorId: string): string | undefined {
  return getTokens()[connectorId];
}

export function buildConnectionFromDefinition(
  def: McpConnectorDefinition,
  config: Record<string, string> = {}
): McpConnection {
  return {
    connectorId: def.id,
    name: def.name,
    transport: def.transport,
    hosting: def.hosting,
    config,
    status: 'disconnected',
    custom: false,
  };
}

export function buildCustomConnection(
  name: string,
  transport: McpConnection['transport'],
  config: Record<string, string>
): McpConnection {
  return {
    connectorId: `custom.${Date.now()}`,
    name,
    transport,
    hosting: transport === 'stdio' ? 'local' : 'cloud',
    config,
    status: 'disconnected',
    custom: true,
  };
}

/** Export agent-facing MCP config */
export function exportAgentMcpConfig(): Record<string, unknown> {
  const connected = getConnections().filter((c) => c.status === 'connected');
  const mcpServers: Record<string, unknown> = {};
  for (const c of connected) {
    const key = c.connectorId.replace(/^mcp\./, '');
    const catalogDef = getConnectorById(c.connectorId);
    if (c.transport === 'stdio') {
      mcpServers[key] = {
        command: c.config.command ?? catalogDef?.command ?? 'npx',
        args: c.config.args
          ? (typeof c.config.args === 'string' ? JSON.parse(c.config.args) : c.config.args)
          : catalogDef?.args ?? ['-y', key],
        env: Object.fromEntries(
          Object.entries(c.config).filter(([k]) => !['command', 'args', 'package', 'url', 'headers'].includes(k))
        ),
      };
    } else {
      mcpServers[key] = {
        transport: c.transport,
        url: c.config.url ?? catalogDef?.url,
        headers: c.config.headers
          ? (typeof c.config.headers === 'string' ? JSON.parse(c.config.headers) : c.config.headers)
          : catalogDef?.headers,
        env: Object.fromEntries(
          Object.entries(c.config).filter(([k]) => !['url', 'headers'].includes(k))
        ),
      };
    }
  }
  return { mcpServers, connectedCount: connected.length, updatedAt: new Date().toISOString() };
}
