import type { ThinkingLevel, StrictnessLevel } from '../models/types';
import { enrichMemberForContext } from './archetypeDefaults';

export type MultiAgentMode = 'collaborative' | 'parallel' | 'fusion';

export type FusionStrategy = 'identity-merge' | 'capability-union' | 'weighted-vote';
export type FusionProviderRouting = 'round-robin' | 'best-of' | 'gateway-combo' | 'parallel-merge';

export interface AgentFusionConfig {
  strategy: FusionStrategy;
  fusedName: string;
  fusedSystemPrompt: string;
  memberIds: string[];
  memberWeights: Record<string, number>;
  providerRouting: FusionProviderRouting;
  /** Primary provider for identity-merge single-call mode */
  primaryMemberId?: string;
}

export type AgentMemberRole = 'head' | 'worker' | 'specialist' | 'reviewer';

export type AgentArchetype =
  | 'core'
  | 'researcher'
  | 'debugger'
  | 'designer'
  | 'tools'
  | 'mcp'
  | 'proxy'
  | 'plugin'
  | 'custom';

export type CompatibilityMode =
  | 'auto'
  | 'openai-chat'
  | 'openai-responses'
  | 'anthropic-messages'
  | 'gemini'
  | 'antigravity'
  | 'mcp'
  | 'custom';

export type MemoryBackend = 'session' | 'postgres' | 'vault' | 'shared';

export interface AgentMemoryConfig {
  backend: MemoryBackend;
  maxMessages: number;
  contextWindow: number;
  postgresUri?: string;
  sharedVaultKey?: string;
  summarizeAfter?: number;
}

export interface AgentMemberTool {
  id: string;
  name: string;
  description: string;
  source: 'provider' | 'mcp' | 'plugin' | 'custom';
}

export interface AgentMemberSkill {
  id: string;
  name: string;
  content: string;
  source: 'vault' | 'upload' | 'builtin';
}

export interface AgentMemberParams {
  temperature: number;
  topP: number;
  maxTokens: number;
  thinkingLevel: ThinkingLevel;
  strictness: StrictnessLevel;
  stream: boolean;
}

export interface AgentAccessConfig {
  /** Terminal: electron→shell, web→cloud shell, apk→native */
  terminal: boolean;
  mcp: boolean;
  plugins: boolean;
  mcpMode: 'all' | 'assigned';
  pluginMode: 'all' | 'assigned';
}

export interface ParamFilterConfig {
  blockedParams: string[];
  allowedParams: string[];
  autoLearnFrom400: boolean;
}

export interface FunctionCallDef {
  id: string;
  name: string;
  description: string;
  parameters: Record<string, unknown>;
  endpoint?: string;
  enabled: boolean;
}

export interface SkillFileRef {
  id: string;
  name: string;
  path?: string;
  content: string;
  format: 'markdown' | 'json' | 'text';
}

export interface AgentCategory {
  id: string;
  name: string;
  description: string;
  icon: string;
  color: string;
  builtIn: boolean;
  archetypes: AgentArchetype[];
  createdAt: number;
}

export const DEFAULT_ACCESS_CONFIG: AgentAccessConfig = {
  terminal: true,
  mcp: true,
  plugins: true,
  mcpMode: 'all',
  pluginMode: 'all',
};

export const DEFAULT_PARAM_FILTERS: ParamFilterConfig = {
  blockedParams: [],
  allowedParams: [],
  autoLearnFrom400: true,
};

export const COMPATIBILITY_OPTIONS: { value: CompatibilityMode; label: string; description: string }[] = [
  { value: 'auto', label: 'Default (auto)', description: 'Detect from provider' },
  { value: 'openai-chat', label: 'OpenAI Chat Completions', description: '/v1/chat/completions' },
  { value: 'openai-responses', label: 'OpenAI Responses API', description: '/v1/responses' },
  { value: 'anthropic-messages', label: 'Anthropic Messages', description: 'Claude messages API' },
  { value: 'gemini', label: 'Gemini', description: 'Google Gemini generateContent' },
  { value: 'antigravity', label: 'Antigravity', description: 'Antigravity multi-model routing' },
  { value: 'mcp', label: 'MCP', description: 'Model Context Protocol tools' },
  { value: 'custom', label: 'Custom', description: 'Manual endpoint configuration' },
];

/** Single agent in a combo — one provider/model harness */
export interface AgentMember {
  id: string;
  name: string;
  role: AgentMemberRole;
  archetype: AgentArchetype;
  categoryId?: string;
  providerId: string;
  providerName: string;
  modelId: string;
  modelLabel: string;
  taskAssignment: string;
  systemPrompt: string;
  tools: AgentMemberTool[];
  skills: AgentMemberSkill[];
  params: AgentMemberParams;
  memory: AgentMemoryConfig;
  brandColor: string;
  enabled: boolean;
  /** Terminal, MCP, plugins access — all enabled by default */
  access: AgentAccessConfig;
  assignedMcpIds: string[];
  assignedPluginIds: string[];
  compatibility: CompatibilityMode;
  paramFilters: ParamFilterConfig;
  functionCalls: FunctionCallDef[];
  skillFiles: SkillFileRef[];
  skillsJson?: Record<string, unknown>;
}

/** Custom combo workflow — multiple agents on one project */
export interface AgentCombo {
  id: string;
  name: string;
  description: string;
  mode: MultiAgentMode;
  /** Parallel mode — which member is the head/surface agent */
  headAgentId: string;
  members: AgentMember[];
  /** Inject full workspace context to all members */
  sharedWorkspaceContext: boolean;
  /** Cloud / system overrides for the whole combo */
  cloudSettings: {
    useOceanProxy: boolean;
    proxyPort: number;
    terminalType: 'shell' | 'cloud' | 'native' | 'auto';
    agentMode: 'review' | 'auto' | 'bypass';
  };
  /** Fusion mode — merge 2–5 agents into one composite identity */
  fusionConfig?: AgentFusionConfig;
  createdAt: number;
  updatedAt: number;
}

export interface MultiAgentSystemConfig {
  defaultMode: MultiAgentMode;
  maxParallelWorkers: number;
  headSynthesisPrompt: string;
  collaborativeMergePrompt: string;
  globalMemory: AgentMemoryConfig;
  cloudShellEnabled: boolean;
  oceanProxyPort: number;
}

export type WorkflowPhaseStatus = 'pending' | 'running' | 'completed' | 'failed';

export interface WorkflowPhaseRecord {
  id: string;
  label: string;
  agentName: string;
  status: WorkflowPhaseStatus;
  startedAt: number;
  completedAt?: number;
}

export interface WorkflowRunRecord {
  id: string;
  comboId: string;
  comboName: string;
  mode: MultiAgentMode;
  prompt: string;
  status: 'running' | 'completed' | 'failed';
  currentPhase?: string;
  phases: WorkflowPhaseRecord[];
  agentNames: string[];
  startedAt: number;
  completedAt?: number;
}

export const DEFAULT_MEMBER_PARAMS: AgentMemberParams = {
  temperature: 0.7,
  topP: 0.95,
  maxTokens: 8192,
  thinkingLevel: 'medium',
  strictness: 'normal',
  stream: true,
};

export const DEFAULT_MEMORY_CONFIG: AgentMemoryConfig = {
  backend: 'session',
  maxMessages: 50,
  contextWindow: 128000,
  summarizeAfter: 30,
};

export const DEFAULT_SYSTEM_CONFIG: MultiAgentSystemConfig = {
  defaultMode: 'parallel',
  maxParallelWorkers: 6,
  headSynthesisPrompt:
    'You are the head agent. Synthesize sub-agent outputs, confirm key decisions with the user, and surface only the most important results.',
  collaborativeMergePrompt:
    'Merge all agent perspectives into one coherent response. Preserve disagreements as labeled alternatives.',
  globalMemory: { ...DEFAULT_MEMORY_CONFIG },
  cloudShellEnabled: true,
  oceanProxyPort: 20128,
};

export function createAgentMember(partial: Partial<AgentMember> & Pick<AgentMember, 'providerId' | 'providerName' | 'modelId' | 'modelLabel'>): AgentMember {
  const id = partial.id ?? crypto.randomUUID();
  return {
    id,
    name: partial.name ?? partial.modelLabel,
    role: partial.role ?? 'worker',
    archetype: partial.archetype ?? 'custom',
    categoryId: partial.categoryId,
    providerId: partial.providerId,
    providerName: partial.providerName,
    modelId: partial.modelId,
    modelLabel: partial.modelLabel,
    taskAssignment: partial.taskAssignment ?? '',
    systemPrompt: partial.systemPrompt ?? '',
    tools: partial.tools ?? [],
    skills: partial.skills ?? [],
    params: { ...DEFAULT_MEMBER_PARAMS, ...partial.params },
    memory: { ...DEFAULT_MEMORY_CONFIG, ...partial.memory },
    brandColor: partial.brandColor ?? '#0ea5e9',
    enabled: partial.enabled ?? true,
    access: { ...DEFAULT_ACCESS_CONFIG, ...partial.access },
    assignedMcpIds: partial.assignedMcpIds ?? [],
    assignedPluginIds: partial.assignedPluginIds ?? [],
    compatibility: partial.compatibility ?? 'auto',
    paramFilters: { ...DEFAULT_PARAM_FILTERS, ...partial.paramFilters },
    functionCalls: partial.functionCalls ?? [],
    skillFiles: partial.skillFiles ?? [],
    skillsJson: partial.skillsJson,
  };
}

export function createAgentCombo(partial?: Partial<AgentCombo>): AgentCombo {
  const id = partial?.id ?? crypto.randomUUID();
  const members = partial?.members ?? [];
  return {
    id,
    name: partial?.name ?? 'New Combo',
    description: partial?.description ?? '',
    mode: partial?.mode ?? 'parallel',
    headAgentId: partial?.headAgentId ?? members.find((m) => m.role === 'head')?.id ?? members[0]?.id ?? '',
    members,
    sharedWorkspaceContext: partial?.sharedWorkspaceContext ?? true,
    cloudSettings: partial?.cloudSettings ?? {
      useOceanProxy: true,
      proxyPort: 20128,
      terminalType: 'auto',
      agentMode: 'review',
    },
    createdAt: partial?.createdAt ?? Date.now(),
    updatedAt: Date.now(),
  };
}

/** Starter combo — Codex + Antigravity + Gemini CLI + Jules style team */
export function createDefaultStarterCombo(): AgentCombo {
  const members = [
    createAgentMember({
      name: 'Codex Lead',
      role: 'head',
      archetype: 'core',
      categoryId: 'cat-core',
      providerId: 'openai.codex',
      providerName: 'OpenAI Codex',
      modelId: 'codex-latest',
      modelLabel: 'Codex',
      taskAssignment: 'Architecture, code review, final synthesis',
      brandColor: '#10a37f',
      systemPrompt: 'Lead engineer — review all sub-agent work and surface decisions to user.',
    }),
    createAgentMember({
      name: 'Antigravity',
      role: 'worker',
      archetype: 'researcher',
      categoryId: 'cat-engineering',
      providerId: 'google.antigravity',
      providerName: 'Antigravity',
      modelId: 'antigravity/claude-opus-4-6-thinking',
      modelLabel: 'Claude Opus 4.6 Thinking',
      taskAssignment: 'Deep reasoning, complex refactors, thinking-mode tasks',
      brandColor: '#8b5cf6',
    }),
    createAgentMember({
      name: 'Gemini Flash',
      role: 'worker',
      archetype: 'designer',
      categoryId: 'cat-design',
      providerId: 'google.antigravity',
      providerName: 'Antigravity',
      modelId: 'antigravity/gemini-3.6-flash-high',
      modelLabel: 'Gemini 3.6 Flash (High)',
      taskAssignment: 'Fast iteration, UI polish, quick fixes',
      brandColor: '#4285f4',
    }),
    createAgentMember({
      name: 'Claude Code',
      role: 'specialist',
      archetype: 'debugger',
      categoryId: 'cat-engineering',
      providerId: 'anthropic.claude-code',
      providerName: 'Claude Code',
      modelId: 'claude-sonnet-4-6',
      modelLabel: 'Claude Sonnet 4.6',
      taskAssignment: 'Code review, tests, documentation',
      brandColor: '#d97706',
    }),
  ];
  return createAgentCombo({
    name: 'Full Stack Team',
    description: 'Codex head + Antigravity + Gemini Flash + Claude Code — parallel sub-agents with head synthesis',
    mode: 'parallel',
    headAgentId: members[0].id,
    members: members.map(enrichMemberForContext),
  });
}
