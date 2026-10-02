---
id: research
name: Research & Synthesis
description: Evidence-backed literature review, upstream specification analysis, and structured decision syntheses.
version: 2.0.0
required_tools:
  - search_web
  - read_url_content
  - view_file
optional_tools:
  - run_command
---

# Research & Synthesis

## 1. Mission and Scope
Provide rigorous, evidence-grounded research, upstream protocol verification, and architectural decision briefs. This skill eliminates speculative guesswork by sourcing facts directly from authoritative standards (RFCs, official vendor API documentation, upstream source code, and release notes) and synthesizing them into auditable decision matrices.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Investigating new provider protocols (e.g., OAuth 2.1, RFC 9728, SSE transport).
  - Comparing architectural tradeoffs between libraries, database engines, or IPC models.
  - Sourcing cryptographic hashes, canonical release mirrors, or upstream source URLs.
  - Resolving contradictory information across disparate technical documentations.
- **Do NOT Invoke When**:
  - Direct code changes or bug fixing is requested without research needs (use Deep Coding).
  - The problem is an internal compiler syntax error already isolated to a single file (use Debugging).
  - Writing code modifications (route findings to appropriate implementation skill).

## 3. Inputs to Gather
1. Clear problem statement and specific technical question(s) to answer.
2. Canonical upstream documentation URLs, RFC numbers, or authoritative specifications.
3. Target deployment constraints (e.g., Android API 28+, ARM64, Bionic libc limitations).
4. Known failure symptoms or ambiguous behaviors observed in existing systems.

## 4. Tool Policy for This Domain
- Sourcing must prioritize primary upstream references (official developer docs, IETF RFCs, canonical git repositories) over third-party blog posts.
- Use `search_web` to discover authoritative documentation URLs.
- Use `read_url_content` to extract exact protocol specifications and parameter schemas.
- Record the exact URLs, document revision dates, and specific section headers as citations.

## 5. Step-by-Step Operating Procedure
1. **Deconstruct Query**: Break the inquiry into testable technical assertions and information requirements.
2. **Authoritative Discovery**: Search for primary sources (RFCs, official provider guides, vendor API references).
3. **Extraction & Cross-Examination**: Read the exact specification sections. If secondary sources disagree with primary RFCs, primary RFCs take precedence.
4. **Recency & Deprecation Check**: Verify whether APIs or protocols have been superseded (e.g., OAuth 2.0 implicit flow vs OAuth 2.1 PKCE).
5. **Claim-to-Source Matrix**: Map each proposed design decision to a specific, cited upstream specification requirement.
6. **Synthesize Decision Brief**: Structure recommendations with explicit tradeoffs, invariants, and fallback mechanisms.

## 6. Domain-Specific Heuristics and Algorithms
- **Primary Source Dominance**: Official protocol specs and canonical source code always override developer forums or outdated tutorials.
- **Triangulation of Ambiguity**: If an upstream spec is ambiguous, examine the reference implementation code in canonical GitHub repositories.
- **Zero Hallucination Rule**: If a provider does not support a desired capability (e.g., no public mobile OAuth flow), document the limitation truthfully rather than inventing workarounds.

## 7. Evidence Requirements
- Direct quotes and line/section citations from authoritative standards.
- Exact URLs with access timestamps and document versions.
- Explicit matrix comparing alternative approaches on performance, security, and complexity.

## 8. Failure Modes and Recovery
- *Contradictory Sources*: Verify publishing dates; newer protocol versions or official errata resolve conflicts.
- *Stale / Deprecated Documentation*: Check changelogs and API deprecation notices to confirm active validity.
- *Paywalled / Restricted Endpoints*: Rely on public official RFCs or open-source reference implementations.

## 9. Security and Permission Boundaries
- Never request or process confidential credentials during research inquiries.
- Adhere strictly to clean-room engineering principles: cite open public specifications only.

## 10. Acceptance Tests
1. Every technical recommendation is backed by at least one primary upstream citation.
2. Tradeoffs, known limitations, and edge cases are clearly enumerated.
3. No unsupported or speculative claims exist in the synthesis.

## 11. Handoff Format
- **Executive Summary**: 2-3 sentences summarizing the conclusion and recommended direction.
- **Claim-to-Source Matrix**: Markdown table mapping each architectural claim to its URL and section citation.
- **Actionable Steps**: Concrete implementation tasks ready to hand off to Deep Coding or API Integration.

## 12. Small Worked Examples
- *Example*: Verifying OpenAI Sign-in with ChatGPT (SIWC) protocol: Discovered dynamic client registration endpoint (`/api/accounts/authorize` with `client_id=dynamic_agent_client`), loopback redirect handling, and issued client ID exchange rules, citing official OpenAI developer documentation.
