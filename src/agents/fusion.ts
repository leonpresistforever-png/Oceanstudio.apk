import type { AgentCombo, AgentFusionConfig, AgentMember } from './types';
import { createAgentMember, createAgentCombo } from './types';
import { enrichMemberForContext } from './archetypeDefaults';

export const FUSION_MIN_MEMBERS = 2;
export const FUSION_MAX_MEMBERS = 5;

export const FUSION_PRESETS: {
  id: string;
  name: string;
  description: string;
  icon: string;
  members: Partial<AgentMember>[];
  fusionConfig: Omit<AgentFusionConfig, 'memberIds' | 'memberWeights'>;
}[] = [
  {
    id: 'codex-antigravity-cursor',
    name: 'Codex + Antigravity + Cursor',
    description: 'OpenAI Codex architecture, Antigravity reasoning, Cursor IDE bridge — unified super-agent',
    icon: '⚡',
    members: [
      { name: 'Codex', providerId: 'openai.codex', providerName: 'OpenAI Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', archetype: 'core', role: 'head' },
      { name: 'Antigravity Opus', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus Thinking', brandColor: '#8b5cf6', archetype: 'researcher', role: 'worker' },
      { name: 'Cursor Cloud', providerId: 'cursor.cloud', providerName: 'Cursor', modelId: 'cursor-agent', modelLabel: 'Cursor Agent', brandColor: '#6366f1', archetype: 'tools', role: 'specialist' },
    ],
    fusionConfig: {
      strategy: 'weighted-vote',
      fusedName: 'Fusion — Codex × Antigravity × Cursor',
      fusedSystemPrompt: 'You are a fused agent combining Codex (implementation), Antigravity (deep reasoning), and Cursor (IDE context). Union all capabilities. Route complex logic to thinking mode; ship code with Codex precision; use Cursor bridge for @codebase context.',
      providerRouting: 'gateway-combo',
    },
  },
  {
    id: 'antigravity-cli-ide',
    name: 'Antigravity CLI + IDE',
    description: 'Antigravity CLI terminal power merged with IDE multi-model routing',
    icon: '🌊',
    members: [
      { name: 'Antigravity CLI', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash', brandColor: '#4285f4', archetype: 'tools', role: 'worker' },
      { name: 'Antigravity IDE', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus Thinking', brandColor: '#8b5cf6', archetype: 'core', role: 'head' },
    ],
    fusionConfig: {
      strategy: 'capability-union',
      fusedName: 'Antigravity Fusion',
      fusedSystemPrompt: 'Fused Antigravity CLI + IDE agent. Fast iteration via Flash; deep refactors via Opus. Union all MCP and terminal access.',
      providerRouting: 'best-of',
    },
  },
  {
    id: 'design-code-review',
    name: 'Designer + Coder + Reviewer',
    description: '5-agent fusion: design, implement, debug, review, research',
    icon: '🎨',
    members: [
      { name: 'Designer', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash', brandColor: '#ec4899', archetype: 'designer', role: 'worker' },
      { name: 'Codex', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', archetype: 'core', role: 'head' },
      { name: 'Debugger', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#d97706', archetype: 'debugger', role: 'specialist' },
      { name: 'Researcher', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus', brandColor: '#8b5cf6', archetype: 'researcher', role: 'specialist' },
      { name: 'Reviewer', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#ef4444', archetype: 'debugger', role: 'reviewer' },
    ],
    fusionConfig: {
      strategy: 'weighted-vote',
      fusedName: 'Full-Stack Fusion',
      fusedSystemPrompt: 'Fused team: design (Flash), code (Codex), debug (Claude), research (Opus), review (Claude). Synthesize all perspectives into one response.',
      providerRouting: 'parallel-merge',
    },
  },
];

/** Union MCP, plugins, skills, tools from multiple members into one virtual member */
export function buildFusedMember(
  members: AgentMember[],
  fusionConfig: AgentFusionConfig
): AgentMember {
  const enabled = members.filter((m) => m.enabled && fusionConfig.memberIds.includes(m.id));
  const primary = enabled.find((m) => m.id === fusionConfig.primaryMemberId) ?? enabled[0];

  if (!primary) {
    throw new Error('Fusion requires at least one enabled member');
  }

  const unionMcp = new Set<string>();
  const unionPlugins = new Set<string>();
  const unionTools: AgentMember['tools'] = [];
  const unionSkills: AgentMember['skills'] = [];
  const unionSkillFiles: AgentMember['skillFiles'] = [];
  const unionFunctions: AgentMember['functionCalls'] = [];
  const seenTool = new Set<string>();
  const seenSkill = new Set<string>();
  const seenFile = new Set<string>();
  const seenFn = new Set<string>();

  for (const m of enabled.map(enrichMemberForContext)) {
    m.assignedMcpIds.forEach((id) => unionMcp.add(id));
    m.assignedPluginIds.forEach((id) => unionPlugins.add(id));
    for (const t of m.tools) {
      if (!seenTool.has(t.id)) { seenTool.add(t.id); unionTools.push(t); }
    }
    for (const s of m.skills) {
      if (!seenSkill.has(s.id)) { seenSkill.add(s.id); unionSkills.push(s); }
    }
    for (const f of m.skillFiles) {
      if (!seenFile.has(f.name)) { seenFile.add(f.name); unionSkillFiles.push(f); }
    }
    for (const fn of m.functionCalls) {
      if (!seenFn.has(fn.id)) { seenFn.add(fn.id); unionFunctions.push(fn); }
    }
  }

  const providerRoster = enabled.map((m) => `${m.providerName}/${m.modelLabel} (w=${fusionConfig.memberWeights[m.id] ?? 1})`).join('; ');

  return createAgentMember({
    id: `fused-${fusionConfig.fusedName.replace(/\s+/g, '-').toLowerCase()}`,
    name: fusionConfig.fusedName,
    role: 'head',
    archetype: 'core',
    providerId: primary.providerId,
    providerName: `Fusion (${enabled.length} providers)`,
    modelId: primary.modelId,
    modelLabel: primary.modelLabel,
    taskAssignment: `Fused: ${providerRoster}`,
    systemPrompt: [
      fusionConfig.fusedSystemPrompt,
      '',
      '## Fused provider roster',
      ...enabled.map((m) => `- ${m.name}: ${m.providerName} / ${m.modelLabel} — ${m.taskAssignment || m.archetype}`),
      '',
      `Strategy: ${fusionConfig.strategy} | Routing: ${fusionConfig.providerRouting}`,
    ].join('\n'),
    tools: unionTools,
    skills: unionSkills,
    skillFiles: unionSkillFiles,
    functionCalls: unionFunctions,
    params: primary.params,
    brandColor: primary.brandColor,
    access: {
      terminal: enabled.some((m) => m.access.terminal),
      mcp: enabled.some((m) => m.access.mcp),
      plugins: enabled.some((m) => m.access.plugins),
      mcpMode: 'assigned',
      pluginMode: 'assigned',
    },
    assignedMcpIds: Array.from(unionMcp),
    assignedPluginIds: Array.from(unionPlugins),
    compatibility: primary.compatibility,
  });
}

export function createFusionComboFromPreset(presetId: string): AgentCombo | null {
  const preset = FUSION_PRESETS.find((p) => p.id === presetId);
  if (!preset) return null;

  const members = preset.members.map((partial) => createAgentMember({
    providerId: partial.providerId ?? 'openai.codex',
    providerName: partial.providerName ?? 'Provider',
    modelId: partial.modelId ?? 'default',
    modelLabel: partial.modelLabel ?? 'Model',
    ...partial,
  }));
  const memberIds = members.map((m) => m.id);
  const memberWeights: Record<string, number> = {};
  const weight = 1 / members.length;
  for (const m of members) memberWeights[m.id] = weight;

  return createAgentCombo({
    name: preset.name,
    description: preset.description,
    mode: 'fusion',
    headAgentId: members[0].id,
    members,
    fusionConfig: {
      ...preset.fusionConfig,
      memberIds,
      memberWeights,
      primaryMemberId: members[0].id,
    },
  });
}

export function validateFusionMembers(members: AgentMember[]): string | null {
  const enabled = members.filter((m) => m.enabled);
  if (enabled.length < FUSION_MIN_MEMBERS) return `Fusion requires at least ${FUSION_MIN_MEMBERS} agents`;
  if (enabled.length > FUSION_MAX_MEMBERS) return `Fusion supports at most ${FUSION_MAX_MEMBERS} agents`;
  return null;
}
