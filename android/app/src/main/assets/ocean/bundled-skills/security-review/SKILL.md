---
id: security-review
name: Security Review
description: Threat modeling, trust boundary enforcement, secret lifecycle auditing, and vulnerability prevention.
version: 2.0.0
required_tools:
  - view_file
  - run_command
optional_tools:
  - search_web
---

# Security Review

## 1. Mission and Scope
Systematically identify, analyze, and eliminate security vulnerabilities across application code, network communications, IPC surfaces, and storage mechanisms. Ensure compliance with zero-trust principles, the Android security model, and strict privacy guarantees (such as zero cookie leakage in ephemeral browser contexts).

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Adding or modifying authentication protocols, OAuth flows, and credential persistence.
  - Exposing WebView configurations, Javascript interfaces, deep links, or Intent filters.
  - Designing multi-process IPC, local HTTP daemons, or socket listeners.
  - Storing user credentials, tokens, or encryption keys in keystores or vaults.
- **Do NOT Invoke When**:
  - Conducting basic unit test writing or routine bug fixes unrelated to security (use Deep Coding).
  - Investigating visual layout, color themes, or design polish (use UX Design).

## 3. Inputs to Gather
1. Architecture diagrams or descriptions of data flows and trust boundaries.
2. Threat model targets: user credentials, private browsing data, local file system integrity, network tokens.
3. Relevant source code: Android manifest, network security config, Keystore integrations, and WebView components.
4. Target platform security restrictions (Android API 28+, Android Keystore, Bionic SELinux policies).

## 4. Tool Policy for This Domain
- Inspect code using `view_file` focusing on cryptographic routines, secret handling, and IPC endpoints.
- Execute security scanning or verification scripts using `run_command`.
- Never print sensitive credentials, tokens, or unredacted secrets into logs or console transcripts.

## 5. Step-by-Step Operating Procedure
1. **Identify Trust Boundaries**: Map external data ingress (intents, network responses, user inputs, WebView bridges) and internal storage boundaries.
2. **Secret Lifecycle Audit**: Verify how secrets (API keys, OAuth tokens, session identifiers) are acquired, stored, and cleared. Confirm use of `EncryptedSharedPreferences` or Android Keystore with zero plaintext leaks.
3. **Network & Transport Review**: Verify TLS 1.3/1.2 enforcement, hostname verification, certificate validation, and anti-cleartext network security configs.
4. **WebView Security Audit**: Verify `setAllowFileAccess(false)`, `setAllowContentAccess(false)`, safe browsing enablement, and strict multi-process data directory isolation (`setDataDirectorySuffix`).
5. **IPC & Intent Firewalling**: Ensure exported activities, services, and receivers specify explicit permissions or `android:exported="false"`.
6. **Abuse Case Analysis**: Model potential adversary behaviors: token interception, CSRF in OAuth callback listeners, path traversal in local file servers.
7. **Formulate Hardening Plan**: Propose specific remediations with defense-in-depth safeguards and security regression tests.

## 6. Domain-Specific Heuristics and Algorithms
- **STRIDE Threat Analysis**: Evaluate Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, and Elevation of Privilege across all public entry points.
- **Principle of Least Privilege**: Grant only the minimum necessary Android permissions and scopes required for operation.
- **Ephemeral Zero-Persistence**: In private browsing and sandboxed sessions, strictly forbid persisting cookies, WebStorage, cache, or form history to non-volatile disk.

## 7. Evidence Requirements
- Code references demonstrating secure cryptographic usage and Keystore backing.
- Verified test runs checking authorization boundaries, token expiration, and error handling.
- Confirmation that no sensitive data appears in device logcat output or shared storage.

## 8. Failure Modes and Recovery
- *Hardcoded Secrets Detected*: Immediately revoke compromised keys, purge git history if necessary, and migrate to `CredentialVault` backed by encrypted storage.
- *Insecure WebView Settings*: Disable JavaScript reflection, file access, and ensure private session data is wiped on exit.
- *Open Loopback Port Exposure*: Restrict internal server binding to `127.0.0.1`, validate high-entropy state/nonce on every callback, and terminate the server immediately upon completion.

## 9. Security and Permission Boundaries
- Never bypass Android OS sandbox protections via root commands or undocumented native syscalls.
- Enforce strict Rule 5: absolutely zero references to Termux paths or binaries in security configurations.

## 10. Acceptance Tests
1. No hardcoded API keys, passwords, or tokens in source code or assets.
2. All network requests use HTTPS with valid certificates; cleartext traffic is explicitly blocked.
3. WebViews in private mode maintain segregated data directories and wipe state on session termination.
4. Authentication tokens are encrypted at rest using AES-GCM-256 backed by hardware keystore when available.

## 11. Handoff Format
- **Security Assessment**: Summary of threat model, evaluated trust boundaries, and overall risk rating.
- **Vulnerabilities Identified**: Severity-ranked list of issues with proof-of-concept explanation.
- **Remediation Diff**: Exact code changes resolving identified risks.
- **Regression Tests**: Automated tests ensuring vulnerabilities cannot be reintroduced.

## 12. Small Worked Examples
- *Example*: Auditing OAuth loopback receiver in `OpenAiDirectAuthAdapter`: Ensured ephemeral HTTP server binds strictly to `127.0.0.1`, validates cryptographic `state` and PKCE `code_verifier`, and destroys the server socket immediately after processing a single authorization code callback.
