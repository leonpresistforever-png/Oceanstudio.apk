# OmniRoute Research Report — Ocean.studio Provider Architecture

**Date:** August 2026  
**Source:** [OmniRoute GitHub](https://github.com/diegosouzapw/OmniRoute), wiki, proxy guide, v3.8.2 release notes

---

## Executive Summary

OmniRoute is an open-source AI gateway that routes requests across **290 providers** and **516 models** through a single OpenAI-compatible endpoint. Ocean.studio adopts its provider connection patterns: unified auth flows, proxy layering, two-step request pipelines, and model/tool/skill sync after connect.

---

## Core Architecture (OmniRoute)

### 1. Provider Connection Modes

| Mode | Description | Ocean.studio equivalent |
|------|-------------|-------------------------|
| **OAuth PKCE** | Browser redirect → localhost callback → token exchange | `oauth_pkce` — Antigravity, Codex, Claude Code |
| **OAuth Redirect** | Standard OAuth 2.0 with client ID/secret | `oauth_redirect` — GitHub Copilot, Azure, Notion |
| **API Key** | Bearer token in header | `api_key` — OpenAI, Anthropic, Groq |
| **Web Cookie** | Session cookies (claude.ai) | `web_cookie` — Claude Web |
| **Device Flow** | AWS Cognito device code | `device_flow` — AWS Bedrock |
| **Gateway URL** | Connect to proxy (OmniRoute, LiteLLM) | `proxy_gateway` — OmniRoute, LiteLLM |
| **Manual** | Custom endpoint + headers | `manual` — Ollama, LM Studio, Custom |

### 2. Proxy Resolution (Priority Order)

OmniRoute resolves proxies in strict specificity — Ocean.studio mirrors this:

1. **Account/Connection** proxy (per API key)
2. **Provider** proxy (e.g., all OpenAI traffic)
3. **Combo** proxy (routing config)
4. **Global** proxy (fallback)

All traffic types are proxied: chat, OAuth token exchange, token refresh, model sync, connection tests.

### 3. Two-Step Pipeline

```
┌─────────────┐    Step 1: formatted request    ┌──────────────────┐
│ Ocean.studio│ ──────────────────────────────► │ External Provider │
│   Client    │    (openai/anthropic/google)    │ (Antigravity etc) │
└─────────────┘                                 └──────────────────┘
       ▲                                                  │
       │         Step 2: build/check nudge                │
       └──────────────────────────────────────────────────┘
```

- **Step 1:** Client sends request in provider's accepted format (`openai`, `anthropic`, `google`, `custom`)
- **Step 2:** Provider processes; client nudges with `/build` or `/check` endpoints
- Response translated back to Ocean agent context

### 4. Post-Connect Sync

After successful auth, OmniRoute syncs:
- **Models** — `GET /v1/models` or provider-specific endpoint
- **Tools** — MCP tool definitions
- **Skills** — System instructions, project context
- **Usage/quota** — Free tier tracking

Ocean.studio pushes synced models into `modelStore` when user sets active provider.

### 5. Antigravity / Coding Agents

OmniRoute supports:
- **Google Antigravity** — remote mode OAuth helper for VPS installs
- **Cursor Cloud Agent** — API key connection
- **Claude Code** — OAuth PKCE with Anthropic token endpoint
- **OpenAI Codex** — OAuth PKCE via `auth.openai.com`

When connected, chat uses provider's models + skills + tools natively.

### 6. Platform Support

| Platform | OAuth Redirect | API Key | Proxy Server | Notes |
|----------|---------------|---------|--------------|-------|
| Electron .exe | ✅ localhost:8768 | ✅ | ✅ npx omniroute | Full support |
| Web | ❌ (no localhost) | ✅ | ❌ | API key + gateway URL only |
| APK | ❌ | ✅ | ❌ | Cloud providers via API key |

OAuth requires Electron desktop for localhost callback (same as GitHub/Cloud Shell).

---

## Ocean.studio Implementation

### Files

| Path | Purpose |
|------|---------|
| `src/providers/types.ts` | Provider definitions, auth types, pipeline config |
| `src/providers/catalog.ts` | 50 providers with auth workflows |
| `src/providers/auth.ts` | Auth badges, platform checks |
| `src/providers/registry.ts` | localStorage connections |
| `src/providers/pipeline.ts` | Two-step request/nudge formatting |
| `electron/services/provider-manager.ts` | OAuth, connect, model sync |
| `electron/services/proxy-server.ts` | Start OmniRoute/LiteLLM proxy |
| `src/pages/ProvidersPage.tsx` | Provider marketplace UI |
| `src/store/providerStore.ts` | Active provider + proxy state |

### OAuth Ports

| Port | Service |
|------|---------|
| 8765 | Google Cloud Shell |
| 8766 | MCP OAuth |
| 8767 | GitHub |
| 8768 | **Providers (generic)** |
| 20128 | OmniRoute default |

### Terminal Backends (unchanged)

| Backend | Platform | Network |
|---------|----------|---------|
| `shell` | Electron desktop | User's hardware + network |
| `native` | Android APK | User's device + network (proot) |
| `cloud` | Website | Google Cloud Shell VM |

---

## Provider Catalog Summary (50)

| Auth Type | Count | Examples |
|-----------|-------|----------|
| OAuth PKCE | 3 | Antigravity, Codex, Claude Code |
| OAuth Redirect | 6 | GitHub Copilot, Azure, Vertex, Notion, Slack |
| API Key | 22 | OpenAI, Anthropic, Google, Groq, DeepSeek, Firecrawl |
| Bearer Token | 5 | Vercel, GitLab, Sentry, Asana |
| Gateway URL | 3 | OmniRoute, LiteLLM, OpenRouter |
| Web Cookie | 1 | Claude Web |
| Device Flow | 1 | AWS Bedrock |
| Manual | 3 | Ollama, LM Studio, Custom Endpoint |
| Embedded | 2 | GitHub, Cloud Shell |

---

## Recommendations

1. **Use OmniRoute as optional gateway** — user starts proxy via terminal, connects `omniroute` provider to `http://localhost:20128`
2. **OAuth on Electron only** — web/APK show "Requires desktop app" for OAuth providers
3. **Active provider drives chat** — model selector shows synced models from connected provider
4. **Agent context** — inject `providers` blob alongside `modelConfig` and `mcpServers`

---

## References

- https://github.com/diegosouzapw/OmniRoute
- https://github.com/diegosouzapw/OmniRoute/wiki/Proxy-Guide
- https://omniroute.online
- Ocean.studio: `agent/mcp-config.json`, `agent/model-config.json`
