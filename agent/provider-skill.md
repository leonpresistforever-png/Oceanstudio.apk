---
name: ocean-provider-agent
description: Provider agent integration for Ocean.studio — dual harness of provider native capabilities and Ocean platform services.
---

# Ocean.studio Provider Agent Skill

When a provider (Antigravity, Codex, Claude Code, Kimi, etc.) is connected, you operate as a **provider agent** — combining the provider's models/tools/skills with Ocean.studio's platform.

## Dual Harness Architecture

```
User → Ocean.studio Agent Panel
         ↓
    buildAgentContext() — injects all docs + provider vaults
         ↓
    Provider API (native models/tools)  ←→  Ocean Platform (terminals, MCP, FS, preview)
         ↓
    Two-step pipeline: request → provider → /build /check nudge
```

## Per-Provider Vault (auto-created on connect)

Each connected provider gets isolated storage at `userData/providers/{providerId}/`:

| File | Contents |
|------|----------|
| `tokens.json` | OAuth tokens (encrypted mode 0600) |
| `config.json` | Endpoint URLs, account settings |
| `tools.json` | Provider-native tools synced on connect |
| `skills.md` | Provider-specific skill injections |
| `models.json` | Synced model list |
| `manifest.json` | Full vault metadata |

Agent reads vault via `window.ocean.providers.getVault(providerId)` or bundled in `buildAgentContext()`.

## Ocean Proxy (OAuth + API Gateway)

Ocean native proxy — **includes embedded Gateway Engine**. Port default: `20128`.

### Gateway Routes (when proxy running)
- `POST /v1/chat/completions` — multi-provider OpenAI-compatible
- `POST /cursor/v1/chat/completions` — Cursor IDE bridge
- `GET /gateway/status` — stats, combos, circuit breakers
- Virtual keys via `window.ocean.gateway.createVirtualKey()`

See `agent/gateway-skill.md` for full IDE integration and strategic workflows.

- **Electron**: `window.ocean.providers.startOceanProxy(20128)`
- **APK/Mobile**: Start proxy on device, OAuth callbacks hit LAN IP
- **Status**: `GET http://localhost:20128/ocean-proxy/status`
- **OAuth callback**: `http://{host}:20128/oauth/{provider-id}/callback` — auto-configured, user never enters redirect URI

Built-in OAuth providers (Antigravity, Codex, Claude Code, etc.) use env credentials — no manual client ID entry.

## Multi-Provider Support

Multiple providers can be active simultaneously:
- `activeProviderIds: string[]` in agent context
- Each provider's vault, tools, skills injected separately
- Model selector shows models from all connected providers

## Communication Formats

Match provider pipeline config:
- **openai**: Chat completions JSON (`/v1/chat/completions`)
- **anthropic**: Messages API JSON
- **google**: Gemini contents/parts JSON

Ocean proxy reverse route: `/proxy/{host}/path` → upstream API

## Latency Optimization

1. Stream responses when provider supports streaming
2. Batch independent tool calls
3. Use MCP connectors instead of shell curl for external APIs
4. Keep Ocean proxy running to avoid OAuth re-auth hops
5. Prefer provider's native tool calling over reimplementation

## Default Injected Context (always in agent session)

These files are bundled via `buildProviderAgentContext()`:
- `agent/agent.md` — platform architecture
- `agent/skill.md` — terminal selection, workflow
- `agent/tools-skill.md` — MCP, plugins, GitHub, providers API
- `agent/mcp-config.json` — MCP routing
- `agent/model-config.json` — model defaults
- `agent/provider-skill.md` — this file
- `agent/gateway-skill.md` — Gateway Engine, IDE bridge
- `agent/playground-skill.md` — Playground categories and runners
- `agent/routing-skill.md` — provider taxonomy, gateway combos, inference profile, route steps
- `providers/{id}/skills.md` — per-provider vault skills

## Agent Terminal: Start Proxy

The agent can start Ocean proxy via terminal when needed:
```bash
# Electron — IPC preferred, but terminal fallback:
curl -s http://localhost:20128/ocean-proxy/status || node scripts/ocean-proxy-standalone.mjs
```

On APK native terminal, same script runs in proot if node is available.

## Connection Canvas

Users manage providers visually at **Connections** view — animated node graph with Ocean at center, provider cards as connected nodes. Disconnect/configure from there.
