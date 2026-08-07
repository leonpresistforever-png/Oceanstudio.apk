/**
 * Default agent context bundle — injected into every provider agent session.
 */
import agentMd from '../../agent/agent.md?raw';
import skillMd from '../../agent/skill.md?raw';
import toolsSkillMd from '../../agent/tools-skill.md?raw';
import providerSkillMd from '../../agent/provider-skill.md?raw';
import multiAgentSkillMd from '../../agent/multi-agent-skill.md?raw';
import gatewaySkillMd from '../../agent/gateway-skill.md?raw';
import playgroundSkillMd from '../../agent/playground-skill.md?raw';
import extensionsSkillMd from '../../agent/extensions-skill.md?raw';
import routingSkillMd from '../../agent/routing-skill.md?raw';
import cursorWorkflowsSkillMd from '../../agent/cursor-workflows-skill.md?raw';
import mcpConfigJson from '../../agent/mcp-config.json';
import modelConfigJson from '../../agent/model-config.json';
import { summarizeAllVaults } from '../lib/routingContext';

export const OCEAN_AGENT_CORE_SKILL = `# Ocean.studio Provider Agent Core

You are operating inside **Ocean.studio** — a hardware-level coding agent workspace.
When a provider (Antigravity, Codex, Claude Code, etc.) is connected, you are the **provider agent**
harnessing BOTH the provider's native capabilities AND Ocean.studio's platform services.

## Your Dual Role
1. **Provider native** — use the connected provider's models, tools, skills, system files
2. **Ocean platform** — use Ocean terminals, filesystem, MCP connectors, plugins, preview, GitHub

## Communication Protocol (Two-Step Pipeline)

### Step 1 — Request (you → provider API)
Format per provider pipeline config:
- \`openai\`: OpenAI chat completions JSON
- \`anthropic\`: Anthropic messages API JSON
- \`google\`: Gemini contents/parts JSON
- \`custom\`: provider-specific format from vault

### Step 2 — Nudge (provider → Ocean → build/check)
After provider processes, Ocean nudges with:
- \`/build\` — execute build/compile step in terminal
- \`/check\` — verify output, run tests, lint
Both sides use the same format the provider accepts. No translation loss.

## Ocean Platform Services (always available)
| Service | API | Platform |
|---------|-----|----------|
| Shell terminal | \`window.ocean.terminal\` | Electron desktop |
| Native Linux | \`window.ocean.terminal\` type=native | Android APK |
| Cloud Shell | \`window.ocean.cloudshell\` | Web |
| Filesystem | \`window.ocean.fs\` | All |
| MCP connectors | \`window.ocean.mcp\` | Electron + Web SSE |
| Plugins | \`window.ocean.plugins\` | All |
| Providers | \`window.ocean.providers\` | All |
| GitHub | \`window.ocean.github\` | Electron |
| Preview ports | \`window.ocean.preview\` | All |
| Gateway Engine | \`window.ocean.gateway\` | Electron (via proxy) |
| Playground | Playground page + \`playgroundRunner\` | All |
| User profile | Profile settings + security store | All |

## Terminal Rules
- **All commands run unrestricted** — no whitelist
- Desktop: real PowerShell/Bash via node-pty
- APK: proot Linux with apt/pip/git
- Web: Google Cloud Shell Debian VM
- Agent session ID: \`ocean-agent-terminal\`

## Model Configuration
User configures via right sidebar: temperature, thinking level, strict rules, custom instructions.
Provider models sync into model selector on connect. Multiple providers can be active simultaneously.

## Latency Optimization
- Prefer streaming responses when provider supports it
- Batch tool calls when possible
- Use connected MCP for external APIs instead of shell curl
- Ocean proxy routes OAuth/API without extra hops when proxy is running

## Default Injected Files
- agent/agent.md — full platform architecture
- agent/skill.md — terminal selection, workflow
- agent/tools-skill.md — MCP, plugins, GitHub, providers
- agent/mcp-config.json — MCP routing
- agent/model-config.json — model defaults
- agent/provider-skill.md — this file
- agent/multi-agent-skill.md — parallel/collaborative teams
- agent/gateway-skill.md — Ocean Gateway Engine, IDE bridge, virtual keys
- agent/playground-skill.md — Playground categories, 3D, Active Bot, recording
- agent/routing-skill.md — provider routing, gateway combos, inference profile
- providers/{id}/skills.md — per-provider skills from vault
- providers/{id}/tools.json — per-provider tools from vault
`;

export const PROVIDER_COMMUNICATION_FORMATS = {
  openai: {
    request: { model: 'string', messages: [{ role: 'user|assistant|system', content: 'string' }], tools: 'optional', stream: true },
    response: { choices: [{ message: { role: 'assistant', content: 'string', tool_calls: 'optional' } }] },
  },
  anthropic: {
    request: { model: 'string', max_tokens: 8192, messages: [{ role: 'user|assistant', content: 'string' }], tools: 'optional' },
    response: { content: [{ type: 'text', text: 'string' }], stop_reason: 'end_turn|tool_use' },
  },
  google: {
    request: { contents: [{ role: 'user|model', parts: [{ text: 'string' }] }], generationConfig: {} },
    response: { candidates: [{ content: { parts: [{ text: 'string' }] } }] },
  },
} as const;

export interface ProviderAgentContext {
  oceanCoreSkill: string;
  communicationFormats: typeof PROVIDER_COMMUNICATION_FORMATS;
  injectedDocs: Record<string, string>;
  providerBundles: Record<string, unknown>;
  activeProviderIds: string[];
  proxyStatus?: { running: boolean; url: string; lanUrl?: string };
}

/** Build provider agent context for injection — web/electron shared */
export function buildProviderAgentContext(opts: {
  providerBundles?: Record<string, unknown>;
  activeProviderIds?: string[];
  proxyStatus?: { running: boolean; url: string; lanUrl?: string };
  extraDocs?: Record<string, string>;
}): ProviderAgentContext {
  return {
    oceanCoreSkill: OCEAN_AGENT_CORE_SKILL,
    communicationFormats: PROVIDER_COMMUNICATION_FORMATS,
    injectedDocs: {
      'agent.md': agentMd,
      'skill.md': skillMd,
      'tools-skill.md': toolsSkillMd,
      'mcp-config.json': JSON.stringify(mcpConfigJson, null, 2),
      'model-config.json': JSON.stringify(modelConfigJson, null, 2),
      'provider-skill.md': providerSkillMd,
      'multi-agent-skill.md': multiAgentSkillMd,
      'gateway-skill.md': gatewaySkillMd,
      'playground-skill.md': playgroundSkillMd,
      'extensions-skill.md': extensionsSkillMd,
      'routing-skill.md': routingSkillMd,
      'cursor-workflows-skill.md': cursorWorkflowsSkillMd,
      ...opts.extraDocs,
    },
    providerBundles: opts.providerBundles ?? {},
    activeProviderIds: opts.activeProviderIds ?? [],
    proxyStatus: opts.proxyStatus,
  };
}

/** Priority docs get higher char limits — routing/provider skills are most actionable */
const DOC_CHAR_LIMITS: Record<string, number> = {
  'routing-skill.md': 12000,
  'provider-skill.md': 10000,
  'gateway-skill.md': 10000,
  'multi-agent-skill.md': 10000,
  'tools-skill.md': 8000,
  'extensions-skill.md': 8000,
  'playground-skill.md': 8000,
};

function docCharLimit(name: string): number {
  return DOC_CHAR_LIMITS[name] ?? 6000;
}

/** Serialize for agent Python backend */
export function serializeProviderContext(ctx: ProviderAgentContext, routingBlock?: string): string {
  const parts = [
    ctx.oceanCoreSkill,
    '\n\n## Injected Documentation\n',
  ];
  for (const [name, content] of Object.entries(ctx.injectedDocs)) {
    parts.push(`\n### ${name}\n`, content.slice(0, docCharLimit(name)), '\n');
  }
  if (routingBlock?.trim()) {
    parts.push('\n\n## Routing Context\n', routingBlock, '\n');
  }
  parts.push(
    '\n\n## Active Providers\n',
    JSON.stringify(ctx.activeProviderIds, null, 2),
    '\n\n## Provider Vault Summaries\n',
    JSON.stringify(summarizeAllVaults(ctx.providerBundles as Record<string, unknown>), null, 2),
    '\n\n## Communication Formats\n',
    JSON.stringify(ctx.communicationFormats, null, 2),
  );
  if (ctx.proxyStatus?.running) {
    parts.push(`\n\n## Ocean Proxy Active\nURL: ${ctx.proxyStatus.url}\nLAN: ${ctx.proxyStatus.lanUrl ?? ctx.proxyStatus.url}\n`);
  }
  return parts.join('');
}
