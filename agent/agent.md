# Ocean.studio Agent

Ocean.studio Agent is a hardware-level coding agent with direct access to your workspace, terminals, and preview environment.

## Terminal Architecture (Real — Not Simulated)

Ocean.studio uses **three real terminal backends**, automatically selected by platform:

| Platform | Terminal Used | What It Is |
|----------|---------------|------------|
| **Electron .exe (Windows)** | `shell` | Real **PowerShell** on your PC (WSL Bash if WSL installed) |
| **Electron .exe (Linux/Mac)** | `shell` | Real **Bash/Zsh** — apt, pip, brew, all host binaries |
| **Website** | `cloud` | Real **Google Cloud Shell** — Debian VM in GCP via OAuth |
| **Mobile APK** | `native` | Real **proot Linux** on device — full package manager, no root |

### Shell Terminal (`shell`) — Desktop .exe

- Powered by `node-pty` — same technology as VS Code's integrated terminal
- **Windows**: PowerShell by default; auto-detects WSL and uses `wsl.exe -e bash` if available
- **Linux**: Full Bash with apt, pip, snap, and every binary on the system
- **Mac**: Zsh/Bash with brew, pip, and all installed tools
- Agent session ID: `ocean-agent-terminal`
- **This is NOT a mockup** — commands execute on the user's actual machine

### Cloud Terminal (`cloud`) — Website

- Google Cloud Shell API integration
- OAuth flow: user clicks Setup → picks Google account → token stored at user level
- Activates `users/me/environments/default` via GCP API
- Debian Linux VM with apt, pip, gcloud, docker, 5GB persistent home
- Same auth pattern as GCP Console / Omni Router integrations
- Requires `GOOGLE_CLOUD_CLIENT_ID` + `GOOGLE_CLOUD_CLIENT_SECRET` in environment

### Native Terminal (`native`) — Mobile APK

- Capacitor plugin `ocean-native-terminal` — real shell process on Android
- **Termux-style**: targetSdk 28 (like Termux) to bypass scoped storage restrictions
- Full bootstrap download: proot, pkg, apt, bash, git, curl, python3
- Falls back to bundled busybox if bootstrap download fails
- Agent session: `ocean-agent-terminal`
- Auto-selected on Android APK (`preferredTerminal: native`)

**Build APK:**
```bash
npm run busybox:download   # fetch busybox arm64 into plugin assets
npm run cap:sync           # build web + sync to android/
npm run android:build      # requires Android SDK — outputs debug APK
```

## Plugin System

Ocean.studio supports a full plugin ecosystem compatible with **Cursor**, **Claude Code**, **Codex**, and **MCP** formats.

### Three Platform Stores (auto-detected)
| Platform | Store | Examples |
|----------|-------|----------|
| **Desktop (.exe)** | Electron plugins | Python runner, PostgreSQL MCP, filesystem MCP |
| **Website** | Web plugins | Firecrawl scraper, SSE MCP servers |
| **Mobile APK** | Mobile plugins | Termux pkg, mobile git tools |

### Two Marketplaces
1. **Official Marketplace** — curated by Ocean.studio, production-ready
2. **Open Source** — live researcher scans GitHub + npm for real MCP servers

### Plugin Formats Supported
- `ocean.plugin.json` — native Ocean format
- `mcp.json` — Cursor / Claude Code / Codex import
- MCP stdio and SSE transports

### Agent Integration
- Agent receives installed plugin tools via `plugins.getTools()`
- MCP servers spawn as child processes on desktop
- Mobile plugins install via `pkg install` in native terminal

## Agent Terminal Routing

The agent **automatically picks the correct terminal**:

```
if Electron desktop → shell
if Website          → cloud
if Mobile APK       → native
```

The agent always ensures `ocean-agent-terminal` session exists before running commands.

## Capabilities

### Codebase Access
- Read, write, modify files in workspace
- Recursive directory navigation
- Monaco editor integration

### Terminal Integration
- Real command execution via node-pty (desktop) or Cloud Shell API (web)
- Port auto-detection → Preview panel
- Agent can run: `npm install`, `pip install`, `apt install`, `gcloud`, etc.

### Preview System
- Live iframe preview of localhost ports
- Auto-detected from terminal output
- Full-screen mode available

### Agent Modes

| Mode | Behavior |
|------|----------|
| **Review-driven** | All commands need user approval |
| **Auto** | Safe commands run immediately; destructive ops paused |
| **Bypass** | Full autonomous execution |

## Event Protocol

```
OCEAN_EVENT:{"type":"command","summary":"Execute in shell terminal","detail":"$ npm install",...}
```

Event types: `thought`, `action`, `command`, `todo`, `result`, `error`, `status`

## GitHub Workflow — Full Configuration

Ocean.studio has **native GitHub integration** plus **terminal git** with PAT. Both work independently.

### Setup (Electron .exe)

```env
GITHUB_CLIENT_ID=your_oauth_app_client_id
GITHUB_CLIENT_SECRET=your_oauth_app_secret
```

GitHub OAuth App settings:
- Homepage: `https://oceanstudio-ef4c5.web.app`
- Callback: `http://127.0.0.1:8767/oauth/github/callback`
- Scopes: `repo`, `read:user`, `workflow`, `read:org`

### UI Access
- **Bottom integrations bar** → GitHub icon → Authorize once
- **GitHub Panel**: Import, Export, Commit & Push, View Repos

### API (Electron)
```typescript
await window.ocean.github.authenticate();
const repos = await window.ocean.github.listRepos();
await window.ocean.github.importRepo('https://github.com/user/repo.git', workspacePath);
await window.ocean.github.exportRepo(workspacePath, 'my-repo', false, 'Initial commit');
await window.ocean.github.commitAndPush(workspacePath, 'Fix bug');
await window.ocean.github.openRepo('https://github.com/user/repo');
```

### Terminal Git (all platforms with shell)
Agent can run real git commands in terminal using OAuth token or user PAT:
```bash
git clone https://github.com/user/repo.git
git add -A && git commit -m "message" && git push origin main
```
On Electron, token is auto-injected into remote URL after GitHub OAuth.

### Agent Context
```typescript
const gh = await window.ocean.github.getStatus();
const modelConfig = useModelStore.getState().getAgentPayload();
await window.ocean.agent.send(message, { github: gh, workspacePath, terminalType, modelConfig });
```

## Model Configuration (Right Sidebar)

The agent panel includes dynamic model selection and configuration:

| Provider | Models |
|----------|--------|
| **Google** | Gemini 2.5 Pro/Flash, 2.0 Flash, 1.5 Pro/Flash |
| **Claude** | Opus 4, Sonnet 4, 3.7 Sonnet, 3.5 Sonnet/Haiku |
| **OpenAI / Codex** | GPT-5, o3, o4-mini, GPT-4.1, GPT-4o, Codex |

**Configurable parameters:** temperature, top-p, max tokens, thinking level, strict rules, custom instructions (bypass default), custom OpenAI-compatible endpoints, PostgreSQL connection, web scraping provider, skills upload, webhooks, custom functions.

**Platform capabilities:**
- **Electron**: all scraping backends (Firecrawl, Puppeteer, Playwright, Fetch), stdio MCP, PostgreSQL, skill upload
- **Web**: cloud SSE MCP, Firecrawl/Fetch scraping, custom endpoints
- **Mobile APK**: cloud SSE MCP, native Linux terminal (all commands), Firecrawl/Fetch

## MCP Connector Auth Tags

Each MCP card shows how it connects:

| Badge | Meaning |
|-------|---------|
| **Instant Connect** | SSE with no API key (e.g. Cloudflare Docs, DeepWiki) — one click |
| **API Key** | Requires token/key fields before connect |
| **OAuth** | Browser OAuth redirect |
| **OAuth Client** | Client ID + Secret + OAuth |
| **Local npx** | Desktop stdio — no config if no env vars |

Connectors support **stdio (npx)**, **SSE**, and **HTTP** transports seamlessly per platform.

## Cloud Shell Setup (One-Time)

1. User clicks **Setup Cloud Shell** in agent ⋮ menu
2. Browser opens → user picks Google account
3. OAuth callback to `localhost:8765` captures token automatically
4. App calls Cloud Shell API to start environment
5. Cloud Terminal opens — user does nothing else

## Honest Limitations

| Claim | Reality |
|-------|---------|
| "Linux shell on Windows" | PowerShell by default; use WSL for Linux commands |
| "Billions of binaries" | Whatever is installed on the host OS / Cloud Shell VM |
| "Beats Termux" | Native busybox prefix on device; full proot-distro optional later |
| "Cloud Shell" | Requires GCP OAuth credentials in `.env` |
| "Android APK" | `npm run android:build` — requires Android SDK |

## Safety

**Terminals run all commands** — no command whitelist. Only catastrophic operations (`rm -rf /`, `mkfs`, `dd of=/dev/`) require approval in review mode. Auto and bypass modes execute freely.

## Ocean Gateway Engine

Embedded in Ocean proxy (`:20128`) — see `agent/gateway-skill.md` and `agent/ocean-gateway-architecture.md`.

- `/v1/chat/completions` — OpenAI-compatible multi-provider routing
- `/cursor/v1/chat/completions` — Cursor IDE payload normalization
- Strategic workflows, Auto-Combo scoring, virtual keys, circuit breaker
- Optional OmniRoute/LiteLLM as external gateway providers

## Playground

Full creative + automation studio — see `agent/playground-skill.md`.

- 14 categories: Active Bot, Recording, agents, research, image/video/3D, upscale, editor, TTS, music, schedule, MCP Store (130+), Plugin Store (110+)
- 3D Studio: open-source local models on EXE; cloud free-tier on APK
- Agent timeout + anti-timeout in right sidebar (30s–15m)

## User Profile & Security

- Left sidebar footer: Google profile picture (when signed in with Google), name, email
- Profile settings: destructive-op confirmation, mask API keys, session lock, biometric lock (APK)
- Security prefs in `userProfileStore` (persisted locally)
