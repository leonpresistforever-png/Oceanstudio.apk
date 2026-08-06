---
name: ocean-tools
description: Ocean.studio tools skill — plugins, MCP connectors, and platform-specific capabilities. Agent reads this and mcp-config.json before running.
format: json-blocks
---

# Ocean.studio Tools Skill

Read `agent/mcp-config.json` and this file before every agent session.

## MCP Connectors (separate from Plugins)

MCP connectors live in **Tools → MCP Connectors**. Plugins live in **Tools → Plugins**.

```json
{
  "mcpApi": {
    "list": "window.ocean.mcp.list()",
    "connect": "window.ocean.mcp.connect(config)",
    "disconnect": "window.ocean.mcp.disconnect(id)",
    "authenticate": "window.ocean.mcp.authenticate(config, clientId, clientSecret)",
    "getAgentConfig": "window.ocean.mcp.getAgentConfig()"
  }
}
```

### Platform MCP Capabilities

```json
{
  "electron": {
    "transports": ["stdio", "sse", "http"],
    "local": "npx @modelcontextprotocol/server-* via child process",
    "cloud": "SSE/HTTP remote endpoints",
    "oauth": "localhost:8766 callback, token in userData/mcp-tokens.json",
    "extra": ["multi-server", "env injection", "filesystem path args"]
  },
  "web": {
    "transports": ["sse", "http"],
    "local": "NOT AVAILABLE — guide user to desktop for stdio",
    "cloud": "Firecrawl, DeepWiki, Cloudflare, Supabase, Linear, etc.",
    "oauth": "requires Electron"
  },
  "mobile": {
    "transports": ["sse", "http"],
    "local": "via native terminal pkg install (not stdio spawn)",
    "cloud": "same cloud SSE connectors as web",
    "extra": ["termux pkg for CLI tools"]
  }
}
```

### Agent MCP Workflow

```json
{
  "steps": [
    "1. const mcp = await window.ocean.mcp.getAgentConfig()",
    "2. Merge mcp.mcpServers into agent context metadata",
    "3. For tool calls: route to connected MCP server by connectorId",
    "4. If no MCP connected: suggest user open Tools → MCP Connectors",
    "5. Never invent MCP tools — only use connected server tool list"
  ]
}
```

## Plugins (separate from MCP)

```json
{
  "pluginApi": {
    "list": "window.ocean.plugins.list()",
    "install": "window.ocean.plugins.install(manifest)",
    "getTools": "window.ocean.plugins.getTools()"
  },
  "openSourceSearch": "Plugins → Open Source tab — searches runtime plugins, NOT MCP servers",
  "formats": ["ocean.plugin.json", "mcp.json import in Create tab"]
}
```

## Extensions (VS Code / Antigravity style — separate from Plugins)

```json
{
  "access": "Sidebar → Tools → Extensions",
  "marketplace": "Open VSX + 54 builtin + 2400 OSS seed — auto-populates on open",
  "agentInjection": "Installed extensions inject agentGuide into buildAgentContext().extensionsGuide",
  "examples": ["esbenp.prettier-vscode", "dbaeumer.vscode-eslint", "ms-python.python", "google.antigravity", "cursor.cursor"],
  "upload": "package.json / VS Code extension manifest with contributes.commands",
  "skillDoc": "agent/extensions-skill.md"
}
```

Extensions ≠ Plugins: extensions are IDE formatters/linters/languages/AI tools; plugins are runtime MCP/tool bundles.

## Agent Workflows (Cursor-style — Tools → Agent Workflows)

```json
{
  "access": "Sidebar → Tools → Agent Workflows",
  "workflows": [
    "codebase-chat — @codebase context, file citations",
    "composer-multi-file — multi-file edits in one pass",
    "bugbot-review — PR-style bug/security review",
    "design-preview — UI + preview viewport (website/mobile/desktop)",
    "terminal-agent — shell/cloud/native routing",
    "debug-systematic — reproduce → fix → verify"
  ],
  "applyApi": "applyWorkflow(id, params) from src/tools/cursorWorkflows.ts",
  "skillDoc": "agent/cursor-workflows-skill.md",
  "previewViewports": ["website", "mobile", "desktop"]
}
```

Each workflow installs bundled skills, sets `agentMode` (review/auto/bypass), and merges params into the agent prompt.

## Detection Before Run

Use `buildAgentContext()` — assembles MCP, plugins, model config, and platform capabilities:

```typescript
import { buildAgentContext } from '@/lib/agentContext';

const agentContext = await buildAgentContext({
  workspacePath,
  activeFile,
  agentMode: 'review',
  terminalType: platform.preferredTerminal,
});

await window.ocean.agent.send(message, agentContext);
```

Context keys injected automatically:
- `modelConfig` — selected model, temperature, thinking level, skills, webhooks, scraping provider
- `mcpServers` + `mcpMeta` — connected MCP connectors
- `pluginTools` — installed plugin tools
- `platform` — electron/web/mobile detection
- `capabilities` — platform-specific feature flags

Read `agent/model-config.json` for model defaults.

### MCP Auth Tags (on connector cards)

| Tag | Connect behavior |
|-----|------------------|
| Instant Connect | One-click — no modal (Cloudflare Docs, DeepWiki) |
| API Key | Config modal with required key fields |
| OAuth | Browser redirect |
| OAuth Client | Client ID + Secret + redirect |
| Local npx | Desktop stdio spawn |

```typescript
const platform = await window.ocean.platform.get();
const mcpConfig = await window.ocean.mcp?.getAgentConfig() ?? { mcpServers: {} };
const pluginTools = await window.ocean.plugins?.getTools() ?? [];
const modelConfig = useModelStore.getState().getAgentPayload();

await window.ocean.agent.send(message, {
  workspacePath,
  terminalType: platform.preferredTerminal,
  mcpServers: mcpConfig.mcpServers,
  mcpMeta: { connectedCount: mcpConfig.connectedCount },
  pluginTools,
  modelConfig,
  platform,
  capabilities: getPlatformCapabilities(platform),
});
```

## MCP vs Plugin Decision

| Need | Use |
|------|-----|
| External API via standard protocol | MCP Connector |
| Custom agent tool / UI extension | Plugin |
| Cursor/Codex mcp.json import | MCP Connectors → Create Custom OR Plugins → Import |
| GitHub repo tool | Plugin (open source tab) |
| Firecrawl, Cloudflare, Supabase | MCP Connector (cloud SSE) |
| Filesystem, GitHub PAT, Postgres | MCP Connector (local stdio, desktop) |

## OAuth MCP Auth Flow

```
User clicks Connect on OAuth MCP
  → mcp.authenticate(config, clientId, clientSecret)
  → Browser opens provider authorize URL
  → Callback http://127.0.0.1:8766/oauth/callback
  → Token saved to mcp-tokens.json
  → mcp.connect() uses Bearer token
```

## GitHub Workflow

```json
{
  "githubApi": {
    "authenticate": "window.ocean.github.authenticate()",
    "listRepos": "window.ocean.github.listRepos()",
    "importRepo": "window.ocean.github.importRepo(url, workspacePath)",
    "exportRepo": "window.ocean.github.exportRepo(workspacePath, name, isPrivate, message)",
    "commitAndPush": "window.ocean.github.commitAndPush(workspacePath, message)",
    "openRepo": "window.ocean.github.openRepo(url)"
  },
  "env": {
    "GITHUB_CLIENT_ID": "Electron .env",
    "GITHUB_CLIENT_SECRET": "Electron .env",
    "callback": "http://127.0.0.1:8767/oauth/github/callback"
  },
  "terminalFallback": {
    "clone": "git clone https://github.com/user/repo",
    "withToken": "git clone https://TOKEN@github.com/user/repo",
    "commit": "git add -A && git commit -m 'msg' && git push"
  },
  "ui": {
    "integrationsBar": "Bottom bar — GitHub, Cloud Shell, MCP, Plugins",
    "githubPanel": "Import / Export / Commit tabs"
  }
}
```

## MCP Store (130+ connectors)

```json
{
  "count": "130+",
  "playgroundTagged": true,
  "customAdd": "Playground → MCP Store → Custom MCP with env template",
  "cloudSse": "MCP Connectors → Ready to Use / Open Source Cloud",
  "localNpx": "MCP Connectors → Local (desktop only)",
  "connect": "window.ocean.mcp.connect(config)",
  "oauth": "window.ocean.mcp.authenticate(config, clientId, clientSecret)",
  "agentConfig": "window.ocean.mcp.getAgentConfig()"
}
```

## Ocean Gateway API

```json
{
  "gatewayApi": {
    "getStatus": "window.ocean.gateway.getStatus()",
    "createVirtualKey": "window.ocean.gateway.createVirtualKey(name)",
    "getIdeConfigs": "window.ocean.gateway.getIdeConfigs(port)",
    "routes": ["/v1/models", "/v1/chat/completions", "/cursor/v1/chat/completions", "/gateway/status"]
  },
  "skillFile": "agent/gateway-skill.md"
}
```

## Playground Runner

```json
{
  "playgroundApi": {
    "runAgent": "runPlaygroundAgentTask(task, workspacePath, activeFile)",
    "runMedia": "runPlaygroundMediaJob({ type, prompt, provider }, workspacePath)",
    "categories": 14,
    "skillFile": "agent/playground-skill.md"
  }
}
```

## Plugins Marketplace (110+ working)

```json
{
  "count": "110+",
  "playgroundTagged": true,
  "install": "window.ocean.plugins.install(manifest) + auto-start MCP if type=mcp",
  "agentTools": "window.ocean.plugins.getTools()",
  "openSource": "Plugins page → Open Source — searches extensions, NOT MCP",
  "official": "Plugins page → Marketplace — curated working plugins"
}
```

## MCP Store (38+ connectors)

```json
{
  "count": "130+",
  "cloudSse": "MCP Connectors → Ready to Use / Open Source Cloud",
  "localNpx": "MCP Connectors → Local (desktop only)",
  "connect": "window.ocean.mcp.connect(config)",
  "oauth": "window.ocean.mcp.authenticate(config, clientId, clientSecret)",
  "agentConfig": "window.ocean.mcp.getAgentConfig()"
}
```

## Error Handling

```json
{
  "stdio_on_web": "Tell user: local MCP needs Electron desktop app",
  "oauth_on_web": "Tell user: OAuth MCP needs Electron desktop app",
  "missing_api_key": "Open config modal, user enters key, retry connect",
  "connection_failed": "Check network, API key, and transport compatibility for platform"
}
```
