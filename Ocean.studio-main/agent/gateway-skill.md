---
name: ocean-gateway
description: Ocean.studio embedded Gateway Engine — multi-provider routing, Cursor IDE bridge, strategic workflows, virtual keys. Read before configuring providers or IDE integrations.
---

# Ocean Gateway Engine Skill

Ocean.studio embeds a **native Gateway Engine** on the Ocean proxy (default port `20128`). It goes beyond optional OmniRoute/LiteLLM external gateways.

## Routes (proxy must be running)

| Route | Purpose |
|-------|---------|
| `GET /v1/models` | List models from connected providers |
| `POST /v1/chat/completions` | OpenAI-compatible chat |
| `POST /cursor/v1/chat/completions` | Cursor IDE payload normalization |
| `GET /gateway/status` | Engine health, stats, circuit breakers |
| `GET /gateway/workflows` | Strategic workflow definitions |

## Start Gateway

```typescript
await window.ocean.providers.startOceanProxy(20128);
const status = await window.ocean.gateway?.getStatus();
```

## Strategic Workflows (pre-configured)

1. **Cursor IDE Bridge** — transform → compress → route (ocean-coding) → fallback
2. **Agent Long-Run** — compress 12k chars → auto-route → 5-attempt fallback
3. **Playground Media** — cost-optimal route → webhook notify

## Model Combos

| Combo ID | Strategy | Use case |
|----------|----------|----------|
| `ocean-smart` | auto_combo | Default intelligent routing |
| `ocean-cost` | cost_optimal | Cheapest provider first |
| `ocean-coding` | fallback | Coding agents (Anthropic → OpenAI → Antigravity) |

## Virtual Keys (IDE auth)

```typescript
const { key } = await window.ocean.gateway.createVirtualKey('Cursor IDE');
// Use key as Bearer token in Cursor base URL override
```

## IDE Integration

| Client | Base URL |
|--------|----------|
| Cursor | `http://127.0.0.1:20128/cursor/v1` + `disableHttp2: true` |
| Cline / Windsurf / Zed | `http://127.0.0.1:20128/v1` |
| Claude Code | `ANTHROPIC_BASE_URL=http://127.0.0.1:20128/v1` |

Export configs: **Providers → Gateway Engine → IDE Integration Export**

## Agent Context Keys

Inject via `buildAgentContext()`:
- `providerContext.proxyStatus` — `{ running, url, lanUrl }`
- Gateway stats via `window.ocean.gateway.getStatus()`

## Optional External Gateways

OmniRoute and LiteLLM remain available as **provider connections** (proxy_gateway auth type), not required when Ocean Gateway is active.

## APK Note

Local gateway on device is limited; APK uses cloud APIs. Desktop Ocean proxy LAN URL enables mobile OAuth callbacks.
