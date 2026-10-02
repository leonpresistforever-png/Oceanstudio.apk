---
id: mcp-integration
name: MCP Integration
description: Model Context Protocol lifecycle, HTTP 401 challenge negotiation, multi-dialect configuration parsing, and tool discovery.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# MCP Integration

## 1. Mission and Scope
Manage the complete lifecycle of Model Context Protocol (MCP) server connections within Ocean. Support stdio and remote HTTP/SSE transports, implement RFC 9728 Protected Resource Metadata discovery and OAuth 2.1 authentication challenges for protected endpoints, normalize heterogeneous client configuration dialects, discover tools/prompts/resources, and maintain resilient session headers.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Adding, configuring, or debugging local stdio or remote SSE/HTTP MCP servers.
  - Handling HTTP 401 unauthorized challenges and initiating OAuth browser consent flows.
  - Parsing multi-dialect JSON configurations (Cursor, Claude Desktop, VS Code, Antigravity).
  - Inspecting discovered tool definitions, JSON schemas, and execution permissions.
- **Do NOT Invoke When**:
  - Direct coding on core IDE features without MCP interactions (use Deep Coding).
  - Configuring native LLM provider API keys or direct OAuth flows (use API Integration).

## 3. Inputs to Gather
1. Server endpoint URL (for remote servers) or command/args/env (for stdio servers).
2. JSON configuration snippet or dialect format (`mcpServers`, `servers`, or direct URL).
3. HTTP response headers and status codes on connection attempt (`WWW-Authenticate`, `Mcp-Session-Id`).
4. OAuth metadata discovery endpoints (`/.well-known/oauth-protected-resource`, RFC 9728).

## 4. Tool Policy for This Domain
- Inspect MCP configuration files and schemas using `view_file`.
- Test remote endpoints and SSE streaming using `run_command` with `curl` or internal diagnostics.
- Ensure all discovered tool definitions and prompts are validated against valid JSON Schema before presentation.

## 5. Step-by-Step Operating Procedure
1. **Dialect Normalization**: Ingest incoming JSON configuration. Parse across Cursor/Claude format (`{"mcpServers": {"name": {...}}}`), VS Code format (`{"servers": [...]}`), or Ocean direct schema.
2. **Transport Initialization**: Establish connection to server. For stdio, launch isolated process. For SSE/HTTP, send JSON-RPC `initialize` request with client capabilities.
3. **HTTP 401 Challenge Handling**: If the remote server responds with HTTP 401:
   - Extract `WWW-Authenticate` header and parse realm or resource metadata parameters.
   - Transition server state to `AUTH_REQUIRED` / `AUTHORIZING` (never generic `PROTOCOL_ERROR`).
   - Discover OAuth authorization server via RFC 9728 protected resource metadata.
   - Perform Dynamic Client Registration (DCR) if required, construct PKCE authorization URI, and prompt user for browser consent.
4. **Token Exchange & Session Tracking**: Exchange authorization code for Bearer token, store in `CredentialVault`, and retry `initialize` with `Authorization: Bearer <token>`.
5. **Session Header Persistence**: Extract `Mcp-Session-Id` header from server responses and attach to all subsequent JSON-RPC calls.
6. **Handshake & Discovery**: Send `notifications/initialized`. Query `tools/list`, `resources/list`, and `prompts/list`.
7. **Permission Review**: Present discovered capabilities to user for explicit tool enablement/disabling.

## 6. Domain-Specific Heuristics and Algorithms
- **RFC 9728 Discovery Sequence**: Look for `link: rel="oauth-protected-resource"` or fetch `/.well-known/oauth-protected-resource` relative to server base URL before falling back to manual endpoint entry.
- **Session Continuity**: Never drop the `Mcp-Session-Id` header across HTTP POST requests; failing to send the session ID breaks stateful MCP endpoints like Cloudflare.
- **Graceful Reconnection**: On network drop or expired token, transition to `REAUTH_REQUIRED` or `OFFLINE` with exponential backoff retry.

## 7. Evidence Requirements
- JSON-RPC initialize handshake transcript showing server capabilities.
- HTTP 401 challenge header capture and successful RFC 9728 metadata resolution.
- Discovered tool list with validated input schemas.

## 8. Failure Modes and Recovery
- *Server Returns HTTP 401*: Automatically initiate MCP OAuth discovery and user consent rather than dropping the connection.
- *Stdio Process Crashing*: Inspect process stderr, check environment variables and binary architecture compatibility, and report exact exit code.
- *Tool Schema Parsing Error*: Gracefully skip malformed tool definitions while preserving remaining functional tools.

## 9. Security and Permission Boundaries
- Tool execution must require user consent before modifying local files or running shell commands.
- Never share credentials or tokens across different MCP server domains.

## 10. Acceptance Tests
1. JSON configuration from Claude, Cursor, or VS Code formats parses accurately without data loss.
2. HTTP 401 response triggers `AUTH_REQUIRED` state and valid OAuth discovery.
3. Successful handshake discovers available tools and updates UI status to `CONNECTED`.
4. `Mcp-Session-Id` is reliably attached to subsequent request headers.

## 11. Handoff Format
- **Server Name & Transport**: Connection type (stdio or SSE/HTTP) and endpoint.
- **Connection Status**: Final state (`CONNECTED`, `AUTH_REQUIRED`, etc.).
- **Discovered Tools**: Summary of registered tools, resources, and schemas.

## 12. Small Worked Examples
- *Example*: Connecting to `https://mcp.cloudflare.com/mcp`: Intercepted HTTP 401 response, parsed RFC 9728 challenge, transitioned status to `AUTH_REQUIRED`, launched browser OAuth authorization, received token callback, retried handshake with session ID, and discovered Cloudflare tools.
