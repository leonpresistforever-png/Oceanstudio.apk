---
id: api-integration
name: API & Network Integration
description: HTTP/REST, SSE/WebSocket networking, provider API adapters, OAuth token lifecycle, and resilient retry policies.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
  - read_url_content
---

# API & Network Integration

## 1. Mission and Scope
Build, integrate, and verify robust network adapters for external AI providers (OpenAI, Anthropic, Gemini, Kimi, Antigravity) and web services. Ensure strict adherence to upstream protocol specifications, truthful connection states, secure credential vaults, and resilient transport layers.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Implementing or modifying provider direct auth adapters (OAuth 2.0/2.1, PKCE, API keys).
  - Building REST or streaming HTTP clients for completions, chat, or embedding endpoints.
  - Handling HTTP 401 challenges, rate limits (HTTP 429), or transient network timeouts.
  - Validating network security configurations, TLS cipher suites, and proxy configurations.
- **Do NOT Invoke When**:
  - Managing on-device local GGUF models (use Local Models).
  - Designing UI buttons, modals, or sheets (use UX Design).

## 3. Inputs to Gather
1. Upstream API documentation, endpoint URLs, headers, and payload schemas.
2. Required authentication strategy: Direct OAuth, Managed Loopback, or API Key (BYOK).
3. Supported streaming protocols: Server-Sent Events (SSE) or chunked transfer encoding.
4. Error response codes, rate limit headers (`Retry-After`), and retry guidelines.

## 4. Tool Policy for This Domain
- Inspect adapter implementations with `view_file`.
- Test network endpoints using `run_command` with `curl` to capture HTTP status codes and headers.
- Never hardcode API keys, secrets, or fake mock tokens into code or configuration files.

## 5. Step-by-Step Operating Procedure
1. **Protocol Verification**: Check official provider API documentation for endpoint URLs, required headers, and authentication schemas.
2. **Adapter Construction**: Implement the `ProviderAuthAdapter` contract with explicit preflight, initiation, callback handling, and probe methods.
3. **Loopback Server Handling**: If the provider requires loopback OAuth redirect (e.g. OpenAI SIWC), bind an ephemeral server to `127.0.0.1:<port>` with high-entropy PKCE verifier and state parameter.
4. **Token Exchange & Vaulting**: Exchange authorization codes for access and refresh tokens. Store encrypted tokens in `CredentialVault`.
5. **Connection Health Probe**: Execute a live 1-token or model-list query to verify that credentials are genuinely operational before setting status to `CONNECTED`.
6. **Resilience & Retry**: Implement exponential backoff for HTTP 429 and 503 errors, respecting `Retry-After` response headers.

## 6. Domain-Specific Heuristics and Algorithms
- **Zero-Faking Connection Rule**: Never report `CONNECTED` unless a real network probe to the provider's API returns HTTP 200 with valid payload data.
- **Provider Disambiguation**: Keep distinct products isolated; never allow generic tokens from one service (e.g. Google Gemini API) to masquerade as another (e.g. Antigravity product).
- **Ephemeral Session Security**: Terminate local loopback listening sockets immediately after handling the incoming OAuth redirect callback.

## 7. Evidence Requirements
- Raw HTTP response status codes and headers from probe requests.
- Log showing successful token exchange without printing token values.
- Clean transition through authentication states (`UNCONFIGURED` -> `AUTHORIZING` -> `CONNECTED`).

## 8. Failure Modes and Recovery
- *HTTP 401 (Unauthorized)*: Attempt token refresh using refresh token; if refresh fails, transition to `AUTH_REQUIRED` and prompt user for re-authentication.
- *HTTP 429 (Rate Limit)*: Parse `Retry-After` header, delay next request accordingly, and notify user of rate limit cooldown.
- *Network Unreachable*: Transition to `OFFLINE` state and queue outbound requests or notify the user.

## 9. Security and Permission Boundaries
- All network traffic must use TLS 1.3 or TLS 1.2; cleartext HTTP is prohibited except for `127.0.0.1` loopback testing.
- Store sensitive bearer tokens in encrypted keystore-backed storage.

## 10. Acceptance Tests
1. Adapter completes OAuth handshake or API key validation cleanly.
2. Health probe successfully executes and verifies token authenticity against live provider.
3. Refresh token flow seamlessly renews expired access tokens without user interruption.
4. Network errors and rate limits are handled gracefully with actionable diagnostics.

## 11. Handoff Format
- **Provider Identifier**: Name, endpoint, and authentication strategy.
- **Connection Status**: Operational state and latency metrics from health probe.
- **Supported Capabilities**: Available models, streaming support, and token limits.

## 12. Small Worked Examples
- *Example*: OpenAI Direct Connect SIWC integration: Spawned ephemeral loopback listener on `127.0.0.1:<port>`, launched browser to `https://auth.openai.com/api/accounts/authorize`, received authorization code, exchanged code using issued client ID, stored tokens in `CredentialVault`, executed 1-token Responses probe, and verified `CONNECTED` state.
