---
name: ocean-agent
description: Ocean.studio hardware-level coding agent. Automatically routes to shell (desktop), cloud (web), or native (mobile) terminals. Use for code editing, terminal commands, previews, and multi-step dev workflows.
---

# Ocean.studio Agent Skill

## Terminal Selection — CRITICAL

**Always resolve the terminal backend before running any command.**

```typescript
const platform = await window.ocean.platform.get();
const terminalType = platform.preferredTerminal
  ?? await window.ocean.platform.getPreferredTerminal();
```

| Condition | Use | Why |
|-----------|-----|-----|
| `isElectron && isDesktop` | `shell` | Real host OS terminal (PowerShell/Bash) |
| `isWeb && !isElectron` | `cloud` | Google Cloud Shell — user has no local shell in browser |
| `isMobile && !isDesktop` | `native` | proot Linux on device |
| Dev browser + `hasNativeShell` | `shell` | Dev WebSocket terminal server (`npm run dev`) |

**Never use `shell` on website builds. Never use `cloud` on desktop .exe unless user explicitly requests it.**

## Terminal Capabilities

### Shell (`shell`) — Desktop .exe
- **Real** node-pty process on user's machine
- Windows: PowerShell; WSL Bash if `wsl.exe` detected
- Linux: Bash + apt, pip, snap, cargo, go, everything installed
- Mac: Zsh/Bash + brew, pip
- Agent session: `ocean-agent-terminal`

### Cloud (`cloud`) — Website
- Google Cloud Shell via OAuth + GCP API
- Setup: `window.ocean.cloudshell.authenticate()` → `activate()`
- Debian VM: apt, pip, gcloud, docker, git
- User picks Google account once; token stored at user level
- Redirect URI: `http://127.0.0.1:8765/oauth/callback`

### Native (`native`) — Mobile APK
- Capacitor plugin `OceanNativeTerminal` on Android
- busybox-based Linux prefix in app storage (no root)
- Setup: auto on first terminal open via `nativeSetup.setup()`
- `ls`, `cat`, `wget`, `tar`, `python3` applets via busybox
- For full apt: user can install proot-distro separately
- **Never use `shell` or `cloud` on Android APK**

## Agent Workflow

### 1. Gather Context
```typescript
import { buildAgentContext } from '@/lib/agentContext';

const agentContext = await buildAgentContext({
  workspacePath,
  activeFile,
  agentMode, // review | auto | bypass
  terminalType: platform.preferredTerminal!,
});
const ports = await window.ocean.preview.getPorts();
```

### 2. Send Message with Full Context
```typescript
await window.ocean.agent.send(message, agentContext);
// Includes: modelConfig, mcpServers, pluginTools, capabilities, platform
```

Read `agent/model-config.json` for model defaults and `agent/mcp-config.json` for MCP routing.

### 3. Run Commands
Agent service auto-creates `ocean-agent-terminal` on the correct backend.

Manual terminal access:
```typescript
await window.ocean.terminal.create({
  id: 'ocean-agent-terminal',
  type: terminalType,
  cwd: workspacePath,
});
await window.ocean.terminal.write('ocean-agent-terminal', 'npm install\n');
```

### 4. Port Preview
Ports auto-detected from terminal stdout. Register manually if needed:
```typescript
await window.ocean.preview.registerPort(3000, 'dev server');
```

## Cloud Shell OAuth Flow

```
User clicks "Setup Cloud Shell"
  → authenticate() opens Google account picker
  → OAuth callback on localhost:8765
  → Token saved to userData/cloud-shell-tokens.json
  → activate() calls Cloud Shell API
  → Environment state = RUNNING
  → Cloud Terminal opens
```

Required env vars (Electron only):
```
GOOGLE_CLOUD_CLIENT_ID=...
GOOGLE_CLOUD_CLIENT_SECRET=...
```

Add `http://127.0.0.1:8765/oauth/callback` as authorized redirect URI in Google Cloud Console.

## Agent Modes

### Review (`review`)
- Emit `command` events with `requiresApproval: true`
- Wait for user confirmation before `terminal.write()`

### Auto (`auto`)
- Run safe commands immediately
- Pause on: `rm -rf`, `sudo`, `curl | sh`, credential files

### Bypass (`bypass`)
- Execute all commands; log everything
- No approval gates — terminals run unrestricted

## Model Configuration

Model selector in agent right sidebar. Config panel (gear icon):

| Setting | Description |
|---------|-------------|
| Model | Google Gemini, Claude, OpenAI/Codex, or custom endpoint |
| Temperature / Top-P / Max tokens | Sliders in config panel |
| Thinking level | off → max |
| Strict rules | relaxed → maximum |
| Custom instructions | Bypass default system prompt option |
| Skills | Upload .md/.txt or let agent create |
| Webhooks / Custom functions | User-defined integrations |
| Scraping provider | Platform-aware: Firecrawl, Puppeteer, Playwright, Fetch |
| PostgreSQL | Agent memory (desktop only) |

Context key: `modelConfig` — always inject via `buildAgentContext()`.

## Platform Matrix

| Build | Shell | Cloud | Native | Agent Default |
|-------|-------|-------|--------|---------------|
| Windows .exe | PowerShell/WSL | Available | Hidden | shell |
| Linux .exe | Bash | Available | Hidden | shell |
| Website | Hidden | GCP Cloud Shell | Hidden | cloud |
| APK | Hidden | Available | proot | native |
| Dev browser | WS terminal | Stub | Hidden | shell if WS up |

## File Operations
```typescript
const files = await window.ocean.fs.readDir(workspacePath);
const content = await window.ocean.fs.readFile(filePath);
await window.ocean.fs.writeFile(filePath, newContent);
```

## UI Event Format

Match Cursor-style logs:
- `thought` — grey shimmer, expandable `>` detail
- `command` — `$` pill, expandable logs
- `action` — surface-level English summary
- Include `metadata.terminalType` on command events

## Error Handling

| Error | Action |
|-------|--------|
| Cloud Shell not authenticated | Guide user to Setup Cloud Shell |
| Shell unavailable in browser | Explain Electron or `npm run dev` needed |
| Command destructive in auto mode | Emit approval request, do not execute |
| Token expired | Call `cloudshell.authenticate()` again |

## What Is NOT Simulated

- Desktop shell: real node-pty on host OS
- Cloud shell: real GCP Debian VM (when OAuth configured)
- Port preview: real localhost iframe
- File editor: real filesystem read/write

## What Requires Additional Setup

- Cloud Shell OAuth credentials in `.env`
- WSL on Windows for Linux commands
- APK native terminal (proot) — mobile build phase
- GCP billing may be required for Cloud Shell API
- MCP OAuth connectors need client ID/secret from provider
- Local stdio MCP requires Electron desktop (not web/APK)

## MCP Connectors — CRITICAL

**Read `agent/mcp-config.json` and `agent/tools-skill.md` before every run.**

```typescript
const mcpConfig = await window.ocean.mcp?.getAgentConfig();
const pluginTools = await window.ocean.plugins?.getTools();
```

| Platform | MCP Transports | Where |
|----------|----------------|-------|
| Electron | stdio + SSE + HTTP | Tools → MCP Connectors |
| Web | SSE + HTTP only | Tools → MCP Connectors |
| Mobile APK | SSE + HTTP only | Tools → MCP Connectors |

- **Plugins** ≠ **MCP Connectors** — plugins are extensions; MCPs are protocol servers
- Open source **plugin** search: Plugins → Open Source (NOT MCP)
- Open source **MCP** search: MCP Connectors → Open Source Cloud
- Local npx MCP: MCP Connectors → Local (desktop only)
- OAuth callback: `http://127.0.0.1:8766/oauth/callback`

### Inject MCP into agent context
```typescript
await window.ocean.agent.send(message, {
  terminalType,
  workspacePath,
  mcpServers: mcpConfig?.mcpServers,
  pluginTools,
});
```

## Gateway Engine

Read `agent/gateway-skill.md` before IDE or multi-provider routing tasks.

```typescript
await window.ocean.providers.startOceanProxy(20128);
const gateway = await window.ocean.gateway?.getStatus();
const ideConfigs = await window.ocean.gateway?.getIdeConfigs(20128);
```

## Playground

Read `agent/playground-skill.md` for media, 3D, Active Bot, recording, marketplace.

```typescript
import { runPlaygroundAgentTask, runPlaygroundMediaJob } from '@/lib/playgroundRunner';
```

## User Security Settings

```typescript
import { useUserProfileStore } from '@/store/userProfileStore';
const { security } = useUserProfileStore.getState();
// confirmDestructiveOps, maskApiKeys, sessionLockMinutes
```
