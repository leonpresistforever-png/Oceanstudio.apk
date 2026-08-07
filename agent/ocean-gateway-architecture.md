# Ocean Gateway Architecture — Beyond OmniRoute

**Date:** August 2026  
**Sources:** OmniRoute wiki, PDF architecture research, Ocean.studio implementation

---

## Executive Summary

Ocean.studio is **not** a thin OmniRoute wrapper. It embeds a native **Gateway Engine** with strategic workflows, while still supporting optional external gateways (OmniRoute, LiteLLM, OpenRouter) as provider connections.

| Capability | OmniRoute | Ocean Gateway Engine |
|------------|-----------|---------------------|
| OpenAI /v1 proxy | ✅ | ✅ Native in Ocean proxy |
| Cursor /cursor bridge | ✅ | ✅ Payload normalization + HTTP/1.1 |
| Auto-Combo 12-factor routing | ✅ | ✅ With circuit breaker |
| Strategic workflows | ❌ | ✅ Multi-step pipelines |
| Playground integration | ❌ | ✅ Media/agent/IDE triggers |
| Virtual keys for IDEs | ✅ | ✅ With one-time reveal |
| MCP bridge export | ✅ | ✅ IDE config generator |
| Context compression | RTK+Caveman | ✅ Caveman + RTK filters |
| OAuth PKCE deep links | ✅ | ✅ ocean:// protocol (desktop) |
| APK cloud offload | Partial | 🔜 Phase 4 (Capacitor bridge) |

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│  IDE Clients (Cursor, Cline, Windsurf, Claude Code)   │
└────────────────────────┬────────────────────────────────┘
                         │ http://127.0.0.1:20128/v1
                         │ http://127.0.0.1:20128/cursor/v1
┌────────────────────────▼────────────────────────────────┐
│  Ocean Proxy (0.0.0.0 bind — LAN for mobile OAuth)      │
│  ├── OAuth callbacks /oauth/{provider}/callback         │
│  ├── Gateway Engine /v1/*, /cursor/*, /gateway/*        │
│  └── Reverse proxy /proxy/{host}/...                    │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Gateway Engine (electron/services/gateway/)            │
│  ├── Workflow Engine (transform→compress→route→fallback)│
│  ├── Translator (role normalizer, dialect detection)    │
│  ├── Combo Router (12-factor Auto-Combo scoring)        │
│  ├── Circuit Breaker + cooldown-aware retry             │
│  ├── Stream sanitizer (SSE, thinking tags)              │
│  └── Gateway Store (combos, workflows, keys, telemetry) │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  Provider Manager + Vault (50+ providers, encrypted)    │
│  Optional: OmniRoute / LiteLLM external gateway         │
└─────────────────────────────────────────────────────────┘
```

---

## Strategic Workflows (Ocean Differentiator)

Pre-configured pipelines in `gateway-store.ts`:

1. **Cursor IDE Bridge** — transform → compress → route (ocean-coding) → fallback
2. **Agent Long-Run** — compress (12k char cap) → auto-route → 5-attempt fallback
3. **Playground Media** — cost-optimal route → webhook notify

Users extend workflows via Providers → Gateway Engine tab.

---

## IDE Integration

| Client | Endpoint | Config |
|--------|----------|--------|
| Cursor | `/cursor/v1` | Override OpenAI Base URL + `disableHttp2: true` |
| Cline | `/v1` | OpenAI Compatible provider |
| Windsurf | `/v1` | Custom Model Provider |
| Claude Code | `/v1` | ANTHROPIC_BASE_URL env |
| Zed | `/v1` | settings.json api_url |

Export configs from **Providers → Gateway Engine → IDE Integration Export**.

---

## Persistence Schema (Gateway Store)

| File | Contents |
|------|----------|
| `gateway/config.json` | Feature flags, default combo |
| `gateway/combos.json` | Fallback chains, aliases |
| `gateway/workflows.json` | Strategic pipeline definitions |
| `gateway/virtual-keys.json` | Hashed IDE auth keys |
| `gateway/usage.json` | Request telemetry (5000 rolling) |
| `gateway/decisions.json` | Auto-Combo audit trail |
| `gateway/health.json` | Per-connection health metrics |

---

## APK Note (Deferred)

Android cannot use Electron `session.setProxy`. Phase 4 will add:
- Capacitor OkHttp plugin for network routing
- Axios proxy agent in frontend for fetch-only APIs
- Cloud gateway URL pointing to desktop LAN or hosted Ocean gateway

---

## Files

| Path | Purpose |
|------|---------|
| `electron/services/gateway/gateway-engine.ts` | HTTP handler, routing, forwarding |
| `electron/services/gateway/combo-router.ts` | Auto-Combo scoring |
| `electron/services/gateway/workflow-engine.ts` | Strategic pipelines |
| `electron/services/gateway/translator.ts` | Role/dialect normalization |
| `electron/services/gateway/stream.ts` | SSE sanitization |
| `electron/services/gateway/circuit-breaker.ts` | Resilience |
| `electron/services/gateway/context-compressor.ts` | Caveman + RTK |
| `electron/services/gateway/gateway-store.ts` | Persistence |
| `electron/services/gateway/gateway-manager.ts` | IPC facade |
| `src/components/gateway/GatewayPanel.tsx` | UI dashboard |

---

## References

- https://github.com/diegosouzapw/OmniRoute
- Ocean PDF: Multi-Platform AI Gateway Architecture research
- `agent/provider-skill.md` — Ocean native proxy port 20128
