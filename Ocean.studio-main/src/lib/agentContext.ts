import { getOceanAPI } from './platform';
import { exportAgentMcpConfig } from '../mcp/registry';
import { getPlatformCapabilities } from './platformCapabilities';
import { useModelStore } from '../store/modelStore';
import { exportProviderAgentConfig } from '../providers/registry';
import { getInstalledPlugins } from '../plugins/registry';
import { buildProviderAgentContext, serializeProviderContext } from '../providers/context';
import { getActiveProviderIds } from '../providers/activeProviders';
import { fetchOceanProxyStatus } from './oceanProxyClient';
import { useMultiAgentStore } from '../store/multiAgentStore';
import { serializeComboForAgent } from './multiAgentOrchestrator';
import { useSkillsStore } from '../skills/skillsStore';
import { skillToAgentPayload, type SkillScope } from '../skills/types';
import { useExtensionsStore } from '../extensions/extensionsStore';
import { serializeExtensionsForAgent } from '../extensions/types';
import { buildRoutingContext } from './routingContext';
import { useFallbackStore } from '../store/fallbackStore';
import { usePromptEnhancerStore } from '../store/promptEnhancerStore';
import { checkExtensionHealth } from './extensionHealth';
import { serializeSessionForAgent, getAgentSession } from './agentSession';

export interface AgentSendContext {
  workspacePath: string;
  activeFile: string | null;
  agentMode: 'review' | 'auto' | 'bypass';
  terminalType: 'shell' | 'cloud' | 'native';
  attachments?: { name: string; mimeType: string; content?: string }[];
  modelConfig: Record<string, unknown>;
  mcpServers: Record<string, unknown>;
  mcpMeta: { connectedCount: number; updatedAt?: string };
  pluginTools: { pluginId: string; name: string; description: string }[];
  providers: Record<string, unknown>;
  providerMeta: {
    activeProviderId?: string;
    activeProviderIds: string[];
    connectedCount: number;
    updatedAt?: string;
  };
  providerContext: {
    serialized: string;
    oceanCoreSkill: string;
    activeProviderIds: string[];
    proxyStatus?: { running: boolean; url: string; lanUrl?: string };
  };
  platform: {
    platform: string;
    isElectron: boolean;
    isDesktop: boolean;
    isMobile: boolean;
    isWeb: boolean;
    isAndroid?: boolean;
    hasNativeShell?: boolean;
    hasNativeTerminal?: boolean;
    shellLabel?: string;
    preferredTerminal?: string;
  };
  capabilities: ReturnType<typeof getPlatformCapabilities>;
  multiAgent: Record<string, unknown>;
  gateway?: Record<string, unknown>;
  routing: {
    catalogStats: Record<string, unknown>;
    activeRoutes: unknown[];
    inference: Record<string, unknown>;
    gatewayCombos: unknown[];
    gatewayRunning: boolean;
    serialized: string;
  };
  fallback: Record<string, unknown>;
  promptEnhancer: Record<string, unknown>;
  extensionHealth: Record<string, unknown>;
  agentSession: Record<string, unknown>;
}

/** Build full agent context — MCP, plugins, model config, platform capabilities */
export async function buildAgentContext(opts: {
  workspacePath: string;
  activeFile: string | null;
  agentMode?: 'review' | 'auto' | 'bypass';
  terminalType: 'shell' | 'cloud' | 'native';
  attachments?: { name: string; mimeType: string }[];
  skillScope?: SkillScope;
}): Promise<AgentSendContext> {
  const api = getOceanAPI();
  const platform = await api.platform.get();
  const capabilities = getPlatformCapabilities(platform);

  const mcpRaw = api.mcp?.getAgentConfig
    ? await api.mcp.getAgentConfig()
    : exportAgentMcpConfig();

  const mcpServers = (mcpRaw.mcpServers ?? {}) as Record<string, unknown>;
  const mcpMeta = {
    connectedCount: (mcpRaw.connectedCount as number) ?? Object.keys(mcpServers).length,
    updatedAt: mcpRaw.updatedAt as string | undefined,
  };

  let pluginTools: AgentSendContext['pluginTools'] = [];
  const pluginFallback = () =>
    getInstalledPlugins()
      .filter((p) => p.enabled)
      .flatMap((p) =>
        (p.manifest.tools ?? []).map((t) => ({
          pluginId: p.manifest.id,
          name: t.name,
          description: t.description,
        }))
      );

  if (api.plugins?.getTools) {
    try {
      pluginTools = (await api.plugins.getTools()) as AgentSendContext['pluginTools'];
    } catch {
      pluginTools = pluginFallback();
    }
  } else {
    pluginTools = pluginFallback();
  }

  const providerRaw = api.providers?.getAgentConfig
    ? await api.providers.getAgentConfig()
    : exportProviderAgentConfig();

  // Active providers come from user selection (localStorage), not backend "all connected"
  const activeProviderIds = getActiveProviderIds();

  let providerBundles: Record<string, unknown> = {};
  if (api.providers?.getAllVaults) {
    try { providerBundles = await api.providers.getAllVaults(); } catch { /* web fallback */ }
  }

  const proxyStatus = api.providers?.getOceanProxyStatus
    ? await api.providers.getOceanProxyStatus().catch(() => fetchOceanProxyStatus())
    : await fetchOceanProxyStatus();

  const providerCtx = buildProviderAgentContext({
    providerBundles,
    activeProviderIds,
    proxyStatus: proxyStatus.running ? { running: true, url: proxyStatus.url, lanUrl: proxyStatus.lanUrl } : undefined,
  });

  const modelState = useModelStore.getState();
  const modelConfig = modelState.getAgentPayload();
  const customModels = modelState.customModels;
  const skillScope = opts.skillScope ?? 'main';
  const installedSkills = useSkillsStore.getState().getInstalledForScope(skillScope);
  const skillPayload = skillToAgentPayload(installedSkills);
  const enabledExtensions = useExtensionsStore.getState().getEnabled();
  const extensionsBlock = serializeExtensionsForAgent(enabledExtensions);
  const existingSkills = (modelConfig.skills as { name: string; content: string }[]) ?? [];
  const seenSkillNames = new Set(existingSkills.map((s) => s.name));
  const dedupedSkillPayload = skillPayload.filter((s) => !seenSkillNames.has(s.name));
  const mergedModelConfig = {
    ...modelConfig,
    skills: [...existingSkills, ...dedupedSkillPayload],
    extensionsGuide: extensionsBlock,
  };
  const multiAgentPayload = useMultiAgentStore.getState().getAgentPayload();
  const activeCombo = useMultiAgentStore.getState().getActiveCombo();

  let gateway: Record<string, unknown> | undefined;
  if (api.gateway?.getStatus) {
    try {
      gateway = await api.gateway.getStatus() as Record<string, unknown>;
    } catch { /* web fallback */ }
  }

  const proxyRunning = proxyStatus.running
    ? { running: true, url: proxyStatus.url }
    : undefined;

  const routing = buildRoutingContext({
    modelConfig: mergedModelConfig,
    customModels,
    providerBundles,
    gateway,
    proxyStatus: proxyRunning,
    activeCombo: activeCombo ?? undefined,
  });

  return {
    workspacePath: opts.workspacePath,
    activeFile: opts.activeFile,
    agentMode: opts.agentMode ?? 'review',
    terminalType: opts.terminalType,
    attachments: opts.attachments,
    modelConfig: mergedModelConfig,
    mcpServers,
    mcpMeta,
    pluginTools,
    providers: (providerRaw.providers ?? {}) as Record<string, unknown>,
    providerMeta: {
      activeProviderId: activeProviderIds[0],
      activeProviderIds,
      connectedCount: (providerRaw.connectedCount as number) ?? 0,
      updatedAt: providerRaw.updatedAt as string | undefined,
    },
    providerContext: {
      serialized: serializeProviderContext(providerCtx, routing.serialized),
      oceanCoreSkill: providerCtx.oceanCoreSkill,
      activeProviderIds: providerCtx.activeProviderIds,
      proxyStatus: providerCtx.proxyStatus,
    },
    platform: {
      platform: platform.platform,
      isElectron: platform.isElectron,
      isDesktop: platform.isDesktop,
      isMobile: platform.isMobile,
      isWeb: platform.isWeb,
      isAndroid: platform.isAndroid,
      hasNativeShell: platform.hasNativeShell,
      hasNativeTerminal: platform.hasNativeTerminal,
      shellLabel: platform.shellLabel,
      preferredTerminal: platform.preferredTerminal,
    },
    capabilities,
    multiAgent: {
      ...multiAgentPayload,
      serializedCombo: activeCombo ? serializeComboForAgent(activeCombo) : '',
    },
    gateway,
    routing: {
      catalogStats: { ...routing.catalogStats },
      activeRoutes: routing.activeRoutes,
      inference: { ...routing.inference },
      gatewayCombos: routing.gatewayCombos,
      gatewayRunning: routing.gatewayRunning,
      serialized: routing.serialized,
    },
    fallback: useFallbackStore.getState().getAgentPayload(),
    promptEnhancer: usePromptEnhancerStore.getState().getAgentPayload(),
    extensionHealth: (() => {
      const report = checkExtensionHealth();
      return { ...report, serialized: report.serialized };
    })(),
    agentSession: {
      ...getAgentSession(),
      serialized: serializeSessionForAgent(),
    },
  };
}
