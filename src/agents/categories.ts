import type { AgentArchetype, AgentCategory, AgentMember } from './types';
import { createAgentMember, DEFAULT_ACCESS_CONFIG, DEFAULT_PARAM_FILTERS } from './types';
import { applyArchetypeDefaults, DESIGNER_SYSTEM_PROMPT, DESIGNER_TASK_ASSIGNMENT } from './archetypeDefaults';

export interface ArchetypeDefinition {
  archetype: AgentArchetype;
  name: string;
  description: string;
  icon: string;
  color: string;
  defaultTask: string;
  defaultSystemPrompt: string;
  suggestedCompatibility: AgentMember['compatibility'];
  suggestedRole: AgentMember['role'];
}

export const ARCHETYPE_DEFINITIONS: ArchetypeDefinition[] = [
  {
    archetype: 'core',
    name: 'Core Agent',
    description: 'Main orchestrator — terminal, MCP, plugins, full workspace access',
    icon: '⚡',
    color: '#0ea5e9',
    defaultTask: 'Coordinate team, synthesize outputs, confirm with user',
    defaultSystemPrompt: 'You are the core agent. You have full terminal, MCP, and plugin access. Lead the team.',
    suggestedCompatibility: 'auto',
    suggestedRole: 'head',
  },
  {
    archetype: 'researcher',
    name: 'Researcher',
    description: 'Web search, docs, API discovery, context gathering',
    icon: '🔬',
    color: '#06b6d4',
    defaultTask: 'Research documentation, APIs, examples, and best practices',
    defaultSystemPrompt: 'Research agent — find docs, APIs, and working examples. Summarize findings clearly.',
    suggestedCompatibility: 'auto',
    suggestedRole: 'specialist',
  },
  {
    archetype: 'debugger',
    name: 'Debugger',
    description: 'Find bugs, trace errors, fix failing tests',
    icon: '🐛',
    color: '#ef4444',
    defaultTask: 'Debug errors, trace stack traces, fix failing tests',
    defaultSystemPrompt: 'Debugger — reproduce issues, read logs, fix root causes. Use terminal freely.',
    suggestedCompatibility: 'auto',
    suggestedRole: 'specialist',
  },
  {
    archetype: 'designer',
    name: 'Designer',
    description: 'UI/UX, layouts, styling, component design — ships with skill.md + design-skill + default-ui scaffold',
    icon: '🎨',
    color: '#ec4899',
    defaultTask: DESIGNER_TASK_ASSIGNMENT,
    defaultSystemPrompt: DESIGNER_SYSTEM_PROMPT,
    suggestedCompatibility: 'auto',
    suggestedRole: 'worker',
  },
  {
    archetype: 'tools',
    name: 'Tools Agent',
    description: 'Function calling, custom tools, file operations',
    icon: '🔧',
    color: '#f59e0b',
    defaultTask: 'Execute tools, function calls, file read/write, grep, search',
    defaultSystemPrompt: 'Tools agent — use all available tools and function calls to accomplish tasks.',
    suggestedCompatibility: 'openai-chat',
    suggestedRole: 'worker',
  },
  {
    archetype: 'mcp',
    name: 'MCP Agent',
    description: 'Dedicated MCP connector harness — assign specific MCP servers',
    icon: '🔌',
    color: '#8b5cf6',
    defaultTask: 'Operate assigned MCP connectors (GitHub, Firebase, Figma, etc.)',
    defaultSystemPrompt: 'MCP agent — use your assigned MCP connections to interact with external services.',
    suggestedCompatibility: 'mcp',
    suggestedRole: 'specialist',
  },
  {
    archetype: 'proxy',
    name: 'Proxy Agent',
    description: 'Ocean proxy routing, provider compatibility, API adaptation',
    icon: '🌊',
    color: '#0369a1',
    defaultTask: 'Route requests through Ocean proxy with correct API compatibility',
    defaultSystemPrompt: 'Proxy agent — ensure requests use the right compatibility mode and param filters.',
    suggestedCompatibility: 'antigravity',
    suggestedRole: 'worker',
  },
  {
    archetype: 'plugin',
    name: 'Plugin Agent',
    description: 'Assigned Ocean plugins — manual plugin bypass per agent',
    icon: '🧩',
    color: '#10b981',
    defaultTask: 'Use assigned plugins for specialized capabilities',
    defaultSystemPrompt: 'Plugin agent — leverage your assigned plugins. Bypass default model tool selection.',
    suggestedCompatibility: 'auto',
    suggestedRole: 'worker',
  },
];

export const DEFAULT_CATEGORIES: AgentCategory[] = [
  {
    id: 'cat-core',
    name: 'Core Team',
    description: 'Head agent and primary orchestrators',
    icon: '⚡',
    color: '#0ea5e9',
    builtIn: true,
    archetypes: ['core'],
    createdAt: Date.now(),
  },
  {
    id: 'cat-engineering',
    name: 'Engineering',
    description: 'Research, debug, and build specialists',
    icon: '🛠️',
    color: '#8b5cf6',
    builtIn: true,
    archetypes: ['researcher', 'debugger', 'tools'],
    createdAt: Date.now(),
  },
  {
    id: 'cat-design',
    name: 'Design & UX',
    description: 'UI, layout, and experience agents',
    icon: '🎨',
    color: '#ec4899',
    builtIn: true,
    archetypes: ['designer'],
    createdAt: Date.now(),
  },
  {
    id: 'cat-integrations',
    name: 'Integrations',
    description: 'MCP, proxy, and plugin harnesses',
    icon: '🔌',
    color: '#10b981',
    builtIn: true,
    archetypes: ['mcp', 'proxy', 'plugin'],
    createdAt: Date.now(),
  },
];

export function getArchetypeDef(archetype: AgentArchetype): ArchetypeDefinition {
  return ARCHETYPE_DEFINITIONS.find((a) => a.archetype === archetype) ?? ARCHETYPE_DEFINITIONS[0];
}

export function createMemberFromArchetype(
  archetype: AgentArchetype,
  categoryId: string,
  partial?: Partial<AgentMember>
): AgentMember {
  const def = getArchetypeDef(archetype);
  const base = {
    name: def.name,
    role: def.suggestedRole,
    providerId: partial?.providerId ?? 'openai.codex',
    providerName: partial?.providerName ?? 'OpenAI Codex',
    modelId: partial?.modelId ?? 'codex-latest',
    modelLabel: partial?.modelLabel ?? 'Codex',
    taskAssignment: def.defaultTask,
    systemPrompt: def.defaultSystemPrompt,
    brandColor: def.color,
    archetype,
    categoryId,
    compatibility: def.suggestedCompatibility,
    access: { ...DEFAULT_ACCESS_CONFIG },
    paramFilters: { ...DEFAULT_PARAM_FILTERS },
    ...partial,
  };
  const merged = applyArchetypeDefaults(archetype, base);
  return createAgentMember(merged as typeof base);
}

export function createCategory(partial?: Partial<AgentCategory>): AgentCategory {
  return {
    id: partial?.id ?? `cat-${crypto.randomUUID()}`,
    name: partial?.name ?? 'New Category',
    description: partial?.description ?? '',
    icon: partial?.icon ?? '📁',
    color: partial?.color ?? '#78716C',
    builtIn: false,
    archetypes: partial?.archetypes ?? ['custom'],
    createdAt: Date.now(),
  };
}
