---
name: ocean-routing
description: Provider routing, gateway combos, model inference, and playground tool modes — read before routing requests across providers or configuring multi-step workflows.
---

# Ocean.studio Routing & Inference Context

This skill describes how Ocean routes requests across providers, combos, and models. It is injected into every agent session via `buildAgentContext().routing`.

## Provider Taxonomy

Providers are classified by **auth type** and **category**:

| Auth type | Meaning | Examples |
|-----------|---------|----------|
| `oauth_pkce` | Browser OAuth with PKCE, token rotation | Antigravity, Codex, Claude Code |
| `oauth_redirect` | Standard OAuth redirect | Google AI Studio, some media APIs |
| `api_key` | API key in env/vault | OpenAI, Anthropic, Models API Service |
| `bearer_token` | Personal access token | GitHub, HuggingFace |
| `device_flow` | Device code flow | AWS Kiro-style agents |
| `web_cookie` | Session cookie paste | Some web-only agents |
| `proxy_gateway` | Route via gateway URL | OmniRoute, LiteLLM |
| `manual` | Custom endpoint + headers | Self-hosted OpenAI-compatible |
| `embedded` | Built-in Ocean service | GitHub, Cloud Shell |

| Category | Purpose |
|----------|---------|
| `coding` | IDE/coding agents with tools + system files |
| `llm` | General chat/completion APIs |
| `gateway` | Multi-provider routers (Ocean Gateway, OmniRoute) |
| `cloud` | Cloud platform APIs |
| `scraping` | Web fetch/crawl |
| `productivity` | Docs, email, calendar |
| `database` | SQL/vector stores |

## Active Route Format

Each connected provider appears as a **route step**:

```
{providerId} / {modelId} · account: {accountLabel|dynamic} · format: {pipeline}
```

- **providerId** — catalog id (e.g. `google.antigravity`, `openai.codex`)
- **modelId** — resolved model string sent to API
- **account** — pinned account label or `dynamic` (auto-select at runtime)
- **format** — `openai` | `anthropic` | `google` | `antigravity` | `custom`

Multiple providers can be active simultaneously (`activeProviderIds[]`). The model selector and gateway resolve the winning route per request.

## Gateway Model Combos (Ocean Proxy :20128)

When Ocean proxy is running, the embedded Gateway Engine routes via **combos**:

| Combo ID | Strategy | Behavior |
|----------|----------|----------|
| `ocean-smart` | `auto_combo` | Score by latency, success rate, quota, cost |
| `ocean-cost` | `cost_optimal` | Cheapest provider with quota headroom |
| `ocean-coding` | `fallback` | Ordered fallback chain for coding agents |

**Auto-combo scoring factors:** success rate (30%), latency (20%), quota headroom (25%), cost (15%), recency (10%). Circuit breaker opens on repeated failures.

**Model aliases** map user-facing names to upstream IDs (e.g. `gpt-4` → `gpt-4o`).

## Multi-Agent Combo Steps

Multi-agent workflows serialize as ordered steps:

```
1. {ProviderName} / {ModelLabel} ({thinkingLevel}) · {accountLabel|dynamic}
   Role: {head|worker|specialist|reviewer} · Compatibility: {mode}
   Params: temp={t}, max_tokens={n}, top_p={p}
   Access: terminal={bool}, mcp={bool}, plugins={bool}
```

**Modes:**
- `parallel` — head decomposes, workers execute, head synthesizes
- `collaborative` — all agents receive same prompt, outputs merge

## Inference Profile (Right Sidebar Config)

The agent receives the user's active inference profile:

| Parameter | Range / values | Effect |
|-----------|----------------|--------|
| `temperature` | 0–2 | Randomness |
| `topP` | 0–1 | Nucleus sampling |
| `maxTokens` | 256–128000 | Output cap |
| `thinkingLevel` | off, low, medium, high, max | Extended reasoning where supported |
| `strictness` | relaxed, normal, strict, maximum | Instruction adherence |
| `presencePenalty` / `frequencyPenalty` | -2 to 2 | Repetition control (when provider supports) |
| `seed` | integer or random | Reproducibility |
| `stopSequences` | string[] | Early termination |
| `jsonMode` | boolean | Structured output constraint |
| `toolsEnabled` | boolean | Function calling on/off |
| `customInstructions` | string | System prompt overlay |
| `customEndpoint` | URL | OpenAI-compatible override |
| `agentTimeoutSec` | 30–900 | Session timeout |
| `antiTimeout` | boolean | Extend to 10 min loops until complete |

**Custom models** add entries with `id`, `name`, `provider`, optional endpoint. Format: `{provider}.{model}` or `custom.{uuid}`.

## Playground Test Modes

When testing in Playground, three capability modes apply:

| Mode | API behavior |
|------|--------------|
| **Tools** | Function calling — model may invoke `customFunctions`, MCP tools, plugin tools |
| **JSON** | `response_format: { type: "json_object" }` or schema — structured output only |
| **Tools + JSON** | Tool calls allowed; final assistant message must match JSON schema |

Agent should respect `toolsEnabled` and any `customFunctions` in `modelConfig` when operating in Tools mode.

## Request Resolution Order

1. Explicit `modelConfig.provider` + `modelConfig.modelId`
2. Multi-agent member override (when running as team member)
3. Gateway combo strategy (when proxy + combo active)
4. **Fallback chain** (when `fallback.enabled` — rate limit / quota / 5xx / timeout)
5. `activeProviderIds[0]` fallback
6. Local inference stub (offline only — reports honestly)

## Fallback & Backup Models (toggle)

When **Fallback ON** in Tools → Fallback Models:

| Provider | Role | Auth |
|----------|------|------|
| Hugging Face | Free-tier serverless models | `HF_TOKEN` access token |
| NVIDIA NIM | Backup microservices | `NVIDIA_API_KEY` |

**Triggers:** 429 rate limit, quota exceeded, 5xx server errors, timeout.

**Distribution:** sequential (chain order), round_robin (rotate per subagent), least_used.

Subagents inherit fallback when `applyToSubagents` is enabled.

Agent context: `fallback.enabled`, `fallback.chain`, `fallback.lastFallbackModelId`.

## Vault Contents (per connected provider)

Each provider vault exposes (secrets redacted in agent context):

- `models.json` — synced model list
- `tools.json` — native tool definitions
- `skills.md` — provider-specific instructions
- `config.json` — endpoints (no tokens)
- `manifest.json` — connection metadata

Read via `providerContext.serialized` or `providers.{id}` in agent payload.

## IDE Bridge Endpoints

| Client | Base URL |
|--------|----------|
| Cursor | `http://127.0.0.1:20128/cursor/v1` |
| OpenAI-compatible | `http://127.0.0.1:20128/v1` |
| Anthropic via gateway | `ANTHROPIC_BASE_URL=http://127.0.0.1:20128/v1` |

Use virtual keys from `window.ocean.gateway.createVirtualKey()` for IDE auth.
