/**
 * Structured routing & inference context for agent injection.
 * Richer than UI metadata — gives agents actionable routing knowledge.
 */
import { PROVIDER_CATALOG } from '../providers/catalog';
import type { ProviderAuthType, ProviderCategory } from '../providers/types';
import { exportProviderAgentConfig } from '../providers/registry';
import { getModelById } from '../models/catalog';
import type { ModelDefinition } from '../models/types';
import type { AgentCombo } from '../agents/types';

const SECRET_KEY_RE = /secret|token|key|password|credential/i;

export interface ProviderCatalogStats {
  total: number;
  byAuthType: Record<string, number>;
  byCategory: Record<string, number>;
  oauth: number;
  apiKey: number;
  freeTier: number;
  withTools: number;
  withSkills: number;
}

export interface ActiveRouteSummary {
  providerId: string;
  name: string;
  modelId?: string;
  modelLabel?: string;
  account: string;
  authType: ProviderAuthType;
  category: ProviderCategory;
  pipelineFormat: string;
  active: boolean;
  models: string[];
  toolCount: number;
  skillCount: number;
  endpoint?: string;
}

export interface InferenceProfile {
  selectedModelId: string;
  modelName?: string;
  provider?: string;
  temperature?: number;
  topP?: number;
  maxTokens?: number;
  thinkingLevel?: string;
  strictness?: string;
  toolsEnabled?: boolean;
  customInstructions?: string;
  customEndpoint?: string;
  agentTimeoutSec?: number;
  antiTimeout?: boolean;
  customFunctionCount: number;
  customModelCount: number;
  customModels: { id: string; name: string; provider: string }[];
  playgroundModes: ('tools' | 'json' | 'tools+json')[];
}

export interface GatewayComboSummary {
  id: string;
  name: string;
  strategy: string;
  fallbackSequence: string[];
  modelAliases?: Record<string, string>;
}

export interface RoutingContextPayload {
  catalogStats: ProviderCatalogStats;
  activeRoutes: ActiveRouteSummary[];
  inference: InferenceProfile;
  gatewayCombos: GatewayComboSummary[];
  gatewayRunning: boolean;
  gatewayUrl?: string;
  serialized: string;
}

const DEFAULT_GATEWAY_COMBOS: GatewayComboSummary[] = [
  { id: 'ocean-smart', name: 'Ocean Smart Route', strategy: 'auto_combo', fallbackSequence: ['openai', 'anthropic', 'google', 'groq', 'deepseek'] },
  { id: 'ocean-cost', name: 'Cost Optimal', strategy: 'cost_optimal', fallbackSequence: ['groq', 'deepseek', 'google', 'openai'] },
  {
    id: 'ocean-coding', name: 'Coding Agents', strategy: 'fallback',
    fallbackSequence: ['anthropic', 'openai', 'antigravity', 'github-copilot'],
    modelAliases: { 'gpt-4': 'gpt-4o', 'claude-3-5-sonnet': 'claude-sonnet-4-20250514' },
  },
  {
    id: 'ocean-backup', name: 'HF + NIM Backup', strategy: 'fallback',
    fallbackSequence: ['huggingface', 'nvidia'],
  },
];

function countBy<T extends string>(items: T[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const item of items) {
    out[item] = (out[item] ?? 0) + 1;
  }
  return out;
}

export function buildCatalogStats(): ProviderCatalogStats {
  const authTypes = PROVIDER_CATALOG.map((p) => p.authType);
  const categories = PROVIDER_CATALOG.map((p) => p.category);
  return {
    total: PROVIDER_CATALOG.length,
    byAuthType: countBy(authTypes),
    byCategory: countBy(categories),
    oauth: PROVIDER_CATALOG.filter((p) => p.authType.startsWith('oauth')).length,
    apiKey: PROVIDER_CATALOG.filter((p) => p.authType === 'api_key').length,
    freeTier: PROVIDER_CATALOG.filter((p) => p.freeTier).length,
    withTools: PROVIDER_CATALOG.filter((p) => p.features.tools).length,
    withSkills: PROVIDER_CATALOG.filter((p) => p.features.skills).length,
  };
}

function redactConfig(config: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(config)) {
    if (SECRET_KEY_RE.test(k)) {
      out[k] = '[redacted]';
    } else if (typeof v === 'string' && v.length > 200) {
      out[k] = `${v.slice(0, 200)}…`;
    } else {
      out[k] = v;
    }
  }
  return out;
}

/** Summarize provider vault bundle — strip secrets, keep actionable metadata */
export function summarizeProviderVault(providerId: string, bundle: unknown): Record<string, unknown> {
  if (!bundle || typeof bundle !== 'object') return { providerId, empty: true };
  const b = bundle as Record<string, unknown>;
  const models = (b.models as unknown[]) ?? (b.modelsJson as unknown[]);
  const tools = (b.tools as unknown[]) ?? (b.toolsJson as unknown[]);
  const skills = b.skillsMd ?? b.skills ?? b['skills.md'];
  const config = (b.config as Record<string, unknown>) ?? (b.configJson as Record<string, unknown>) ?? {};
  const manifest = (b.manifest as Record<string, unknown>) ?? {};

  return {
    providerId,
    accountLabel: manifest.accountLabel ?? b.accountLabel,
    models: Array.isArray(models) ? models.slice(0, 50) : models,
    modelCount: Array.isArray(models) ? models.length : undefined,
    tools: Array.isArray(tools) ? (tools as { name?: string }[]).slice(0, 20).map((t) => t.name ?? t) : undefined,
    toolCount: Array.isArray(tools) ? tools.length : undefined,
    skillsExcerpt: typeof skills === 'string' ? skills.slice(0, 2000) : undefined,
    config: redactConfig(config as Record<string, unknown>),
    pipeline: manifest.pipeline ?? b.pipeline,
    connectedAt: manifest.connectedAt ?? b.connectedAt,
  };
}

export function buildActiveRoutes(
  modelConfig: Record<string, unknown>,
  customModels: ModelDefinition[] = []
): ActiveRouteSummary[] {
  const providerRaw = exportProviderAgentConfig();
  const providers = (providerRaw.providers ?? {}) as Record<string, {
    name?: string;
    models?: string[];
    tools?: unknown[];
    skills?: unknown[];
    accountLabel?: string;
    active?: boolean;
    config?: Record<string, string>;
  }>;
  const activeIds = (providerRaw.activeProviderIds as string[]) ?? [];
  const selectedModelId = modelConfig.modelId as string | undefined;
  const selectedModel = selectedModelId ? getModelById(selectedModelId, customModels) : undefined;

  const routes: ActiveRouteSummary[] = [];

  for (const [providerId, conn] of Object.entries(providers)) {
    const def = PROVIDER_CATALOG.find((p) => p.id === providerId);
    const isActive = conn.active ?? activeIds.includes(providerId);
    const primaryModel = isActive && selectedModel?.provider === providerId.split('.')[0]
      ? selectedModelId
      : conn.models?.[0];

    routes.push({
      providerId,
      name: conn.name ?? def?.name ?? providerId,
      modelId: isActive ? primaryModel : undefined,
      modelLabel: isActive && selectedModel ? selectedModel.name : undefined,
      account: conn.accountLabel ?? 'dynamic',
      authType: def?.authType ?? 'api_key',
      category: def?.category ?? 'llm',
      pipelineFormat: def?.pipeline.requestFormat ?? 'openai',
      active: isActive,
      models: conn.models ?? def?.defaultModels ?? [],
      toolCount: conn.tools?.length ?? 0,
      skillCount: conn.skills?.length ?? 0,
      endpoint: conn.config?.chatEndpoint ?? def?.pipeline.chatEndpoint,
    });
  }

  // Include catalog entries for active providers not yet in connections
  for (const id of activeIds) {
    if (routes.some((r) => r.providerId === id)) continue;
    const def = PROVIDER_CATALOG.find((p) => p.id === id);
    if (!def) continue;
    routes.push({
      providerId: id,
      name: def.name,
      account: 'dynamic',
      authType: def.authType,
      category: def.category,
      pipelineFormat: def.pipeline.requestFormat,
      active: true,
      models: def.defaultModels ?? [],
      toolCount: def.features.tools ? 1 : 0,
      skillCount: def.features.skills ? 1 : 0,
      endpoint: def.pipeline.chatEndpoint,
    });
  }

  return routes.sort((a, b) => Number(b.active) - Number(a.active));
}

export function buildInferenceProfile(
  modelConfig: Record<string, unknown>,
  customModels: ModelDefinition[] = []
): InferenceProfile {
  const selectedModelId = (modelConfig.modelId as string) ?? '';
  const model = getModelById(selectedModelId, customModels);
  const customFns = (modelConfig.customFunctions as unknown[]) ?? [];
  const toolsEnabled = modelConfig.toolsEnabled !== false;

  return {
    selectedModelId,
    modelName: (modelConfig.modelName as string) ?? model?.name,
    provider: (modelConfig.provider as string) ?? model?.provider,
    temperature: modelConfig.temperature as number | undefined,
    topP: modelConfig.topP as number | undefined,
    maxTokens: modelConfig.maxTokens as number | undefined,
    thinkingLevel: modelConfig.thinkingLevel as string | undefined,
    strictness: modelConfig.strictness as string | undefined,
    toolsEnabled,
    customInstructions: modelConfig.customInstructions as string | undefined,
    customEndpoint: modelConfig.customEndpoint as string | undefined,
    agentTimeoutSec: modelConfig.agentTimeoutSec as number | undefined,
    antiTimeout: modelConfig.antiTimeout as boolean | undefined,
    customFunctionCount: customFns.length,
    customModelCount: customModels.length,
    customModels: customModels.map((m) => ({ id: m.id, name: m.name, provider: m.provider })),
    playgroundModes: toolsEnabled
      ? ['tools', 'json', 'tools+json']
      : ['json'],
  };
}

export function parseGatewayCombos(gateway?: Record<string, unknown>): GatewayComboSummary[] {
  const combos = gateway?.combos as GatewayComboSummary[] | undefined;
  if (Array.isArray(combos) && combos.length) return combos;
  return DEFAULT_GATEWAY_COMBOS;
}

export function serializeRoutingContext(payload: RoutingContextPayload): string {
  const lines: string[] = [
    '# Ocean Routing Context',
    '',
    '## Catalog',
    `Total providers: ${payload.catalogStats.total}`,
    `OAuth: ${payload.catalogStats.oauth} · API key: ${payload.catalogStats.apiKey} · Free tier: ${payload.catalogStats.freeTier}`,
    `With tools: ${payload.catalogStats.withTools} · With skills: ${payload.catalogStats.withSkills}`,
    '',
    '## Active Routes',
  ];

  const active = payload.activeRoutes.filter((r) => r.active);
  if (!active.length) {
    lines.push('No active provider routes. User must connect a provider.');
  } else {
    active.forEach((r, i) => {
      const model = r.modelId ? ` / ${r.modelLabel ?? r.modelId}` : '';
      lines.push(
        `${i + 1}. **${r.name}**${model} · account: \`${r.account}\` · format: \`${r.pipelineFormat}\``,
        `   - id: \`${r.providerId}\` · auth: ${r.authType} · models: ${r.models.slice(0, 5).join(', ') || 'default'}${r.models.length > 5 ? '…' : ''}`,
        `   - tools: ${r.toolCount} · skills: ${r.skillCount}${r.endpoint ? ` · endpoint: ${r.endpoint}` : ''}`,
        ''
      );
    });
  }

  lines.push(
    '## Inference Profile',
    `- Model: \`${payload.inference.selectedModelId}\`${payload.inference.modelName ? ` (${payload.inference.modelName})` : ''}`,
    `- Provider: ${payload.inference.provider ?? 'auto'}`,
    `- Params: temp=${payload.inference.temperature ?? 'default'}, top_p=${payload.inference.topP ?? 'default'}, max_tokens=${payload.inference.maxTokens ?? 'default'}, thinking=${payload.inference.thinkingLevel ?? 'default'}`,
    `- Tools: ${payload.inference.toolsEnabled ? 'enabled' : 'disabled'} · custom functions: ${payload.inference.customFunctionCount}`,
    `- Timeout: ${payload.inference.antiTimeout ? 'anti-timeout (extended)' : `${payload.inference.agentTimeoutSec ?? 120}s`}`,
    `- Playground modes: ${payload.inference.playgroundModes.join(', ')}`,
  );

  if (payload.inference.customInstructions?.trim()) {
    lines.push('', '### Custom Instructions', payload.inference.customInstructions.slice(0, 1500));
  }

  if (payload.inference.customModels.length) {
    lines.push('', '### Custom Models');
    for (const m of payload.inference.customModels) {
      lines.push(`- \`${m.id}\` — ${m.name} (${m.provider})`);
    }
  }

  lines.push('', '## Gateway Combos');
  if (payload.gatewayRunning) {
    lines.push(`Proxy: ${payload.gatewayUrl ?? 'http://127.0.0.1:20128'}`);
  } else {
    lines.push('Gateway proxy not running — direct provider APIs used.');
  }
  for (const c of payload.gatewayCombos) {
    lines.push(`- **${c.name}** (\`${c.id}\`) — strategy: \`${c.strategy}\`, chain: ${c.fallbackSequence.join(' → ')}`);
    if (c.modelAliases) {
      const aliases = Object.entries(c.modelAliases).map(([k, v]) => `${k}→${v}`).join(', ');
      lines.push(`  aliases: ${aliases}`);
    }
  }

  return lines.join('\n');
}

export function buildRoutingContext(opts: {
  modelConfig: Record<string, unknown>;
  customModels?: ModelDefinition[];
  providerBundles?: Record<string, unknown>;
  gateway?: Record<string, unknown>;
  proxyStatus?: { running: boolean; url: string };
  activeCombo?: AgentCombo;
}): RoutingContextPayload {
  const catalogStats = buildCatalogStats();
  const activeRoutes = buildActiveRoutes(opts.modelConfig, opts.customModels);
  const inference = buildInferenceProfile(opts.modelConfig, opts.customModels);
  const gatewayCombos = parseGatewayCombos(opts.gateway);
  const gatewayRunning = opts.proxyStatus?.running ?? (opts.gateway?.running as boolean) ?? false;
  const gatewayUrl = opts.proxyStatus?.url ?? (opts.gateway?.url as string);

  const payload: RoutingContextPayload = {
    catalogStats,
    activeRoutes,
    inference,
    gatewayCombos,
    gatewayRunning,
    gatewayUrl,
    serialized: '',
  };

  payload.serialized = serializeRoutingContext(payload);
  return payload;
}

/** Summarize all vault bundles for provider context injection */
export function summarizeAllVaults(bundles: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [id, bundle] of Object.entries(bundles)) {
    out[id] = summarizeProviderVault(id, bundle);
  }
  return out;
}
