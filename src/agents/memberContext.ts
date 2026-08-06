import type { AgentMember } from './types';
import { enrichMemberForContext } from './archetypeDefaults';
import { getConnections } from '../mcp/registry';
import { getInstalledPlugins } from '../plugins/registry';
import { exportAgentMcpConfig } from '../mcp/registry';

/** Filter MCP servers for a specific agent member */
export function filterMcpForMember(
  allMcpServers: Record<string, unknown>,
  member: AgentMember
): { mcpServers: Record<string, unknown>; connectedCount: number } {
  if (!member.access.mcp) {
    return { mcpServers: {}, connectedCount: 0 };
  }

  if (member.access.mcpMode === 'all' || member.assignedMcpIds.length === 0) {
    return {
      mcpServers: allMcpServers,
      connectedCount: Object.keys(allMcpServers).length,
    };
  }

  const filtered: Record<string, unknown> = {};
  for (const id of member.assignedMcpIds) {
    const key = id.replace(/^mcp\./, '');
    if (allMcpServers[key]) filtered[key] = allMcpServers[key];
    if (allMcpServers[id]) filtered[id] = allMcpServers[id];
  }
  return { mcpServers: filtered, connectedCount: Object.keys(filtered).length };
}

/** Filter plugin tools for a specific agent member */
export function filterPluginsForMember(
  allPluginTools: { pluginId: string; name: string; description: string }[],
  member: AgentMember
): { pluginId: string; name: string; description: string }[] {
  if (!member.access.plugins) return [];

  if (member.access.pluginMode === 'all' || member.assignedPluginIds.length === 0) {
    return allPluginTools;
  }

  return allPluginTools.filter((t) => member.assignedPluginIds.includes(t.pluginId));
}

/** Build serialized skills content for agent injection */
export function serializeMemberSkills(member: AgentMember): string {
  const enriched = enrichMemberForContext(member);
  const parts: string[] = [];

  if (enriched.skillsJson && Object.keys(enriched.skillsJson).length > 0) {
    parts.push('## Skills JSON\n```json\n' + JSON.stringify(enriched.skillsJson, null, 2) + '\n```');
  }

  for (const file of enriched.skillFiles) {
    parts.push(`## ${file.name}\n\`\`\`${file.format}\n${file.content}\n\`\`\``);
  }

  for (const skill of enriched.skills) {
    if (skill.content) {
      parts.push(`## ${skill.name}\n${skill.content}`);
    }
  }

  return parts.join('\n\n');
}

/** Build function calling definitions for member */
export function serializeMemberFunctions(member: AgentMember): unknown[] {
  return member.functionCalls
    .filter((f) => f.enabled)
    .map((f) => ({
      type: 'function',
      function: {
        name: f.name,
        description: f.description,
        parameters: f.parameters,
      },
      ...(f.endpoint ? { endpoint: f.endpoint } : {}),
    }));
}

/** Apply param filters to a request body before sending to provider */
export function applyParamFilters(
  body: Record<string, unknown>,
  filters: AgentMember['paramFilters']
): Record<string, unknown> {
  const result = { ...body };

  for (const key of filters.blockedParams) {
    delete result[key];
  }

  if (filters.allowedParams.length > 0) {
    const allowed = new Set([...filters.allowedParams, 'model', 'messages', 'stream']);
    for (const key of Object.keys(result)) {
      if (!allowed.has(key)) delete result[key];
    }
  }

  return result;
}

/** List available MCP connectors for assignment UI */
export function listAssignableMcp(): { id: string; name: string; status: string }[] {
  return getConnections().map((c) => ({
    id: c.connectorId,
    name: c.name,
    status: c.status,
  }));
}

/** List available plugins for assignment UI */
export function listAssignablePlugins(): { id: string; name: string; enabled: boolean }[] {
  return getInstalledPlugins().map((p) => ({
    id: p.manifest.id,
    name: p.manifest.name,
    enabled: p.enabled,
  }));
}

/** Get connected MCP config for member context building */
export function getMcpConfigForMember(member: AgentMember): Record<string, unknown> {
  const raw = exportAgentMcpConfig();
  const allServers = (raw.mcpServers ?? {}) as Record<string, unknown>;
  return filterMcpForMember(allServers, member).mcpServers;
}
