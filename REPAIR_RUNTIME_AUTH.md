# Runtime and authorization repair — 3 October 2026

Baseline: app main d4bdefadbb0e70a832ca0a2903eb40ffc8486be1. The three supplied Drive PDFs were read; newer source takes precedence over their October 1–2 snapshots.

## Implemented

- Allow keyless local inference only at an explicit HTTP port on 127.0.0.1. Cloud endpoints retain HTTPS/key validation. Local requests bypass network proxies.
- Move model startup off the UI thread, drain child output, provide Android prefix/runtime environment, allocate a free loopback port, wait up to 120 seconds for inference, reject dead processes, and clear stale local connections after app restart. Preserve Connected during disk refresh and stop a connected runtime before model deletion. Local-only preference fails visibly instead of selecting a cloud provider.
- Attach the OpenAI loopback callback handler. Use the state/path-validated receiver for OpenAI and MCP. Store access tokens in runtime credential references rather than refresh tokens; retain structured refresh storage. Save the provider runtime endpoint with the connection.
- MCP registers exactly the live loopback callback. Remove invented client IDs and guessed authorization/token endpoints. Handle JSON/SSE replies, notification responses and session/protocol headers; fail tools discovery when it returns an error.
- Native Google login exchanges the Google credential through FirebaseAuth. Existing manual email/password login is preserved.
- Remove unreachable Antigravity simulated plan/account/expiry and nonempty-token success probe. Direct Antigravity remains unavailable until an actual provider-supported integration is supplied.
- Browser proxy application no longer labels the route Verified merely because WebView accepted configuration.

## Evidence

`python scripts/tests/test_runtime_auth_boundaries.py` passes executable JVM checks against OceanModelConfig and OAuthLoopbackReceiver, including real TCP/HTTP callback handling, invalid state/path/duplicate state rejection, and valid callback completion. Java syntax parsing passes across app sources; this is not Android compilation.

`./gradlew :app:testDebugUnitTest --console=plain` is blocked downloading Gradle 8.11.1: Network is unreachable. This environment also lacks Android SDK/NDK. No APK was built, installed, or device-tested.

The repository's latest bundled APK certificate SHA-1 is `931ff5463765bce8074bc2cdc455e4023ba495b0`. The checked-in Firebase Android client for studio.ocean.app declares `fedcd3a64a62749a5bc18194a01a346651ff3346`. The installed device APK certificate has not been measured. Provider enablement does not establish certificate matching; do not rewrite Firebase or manufacture an app ID to conceal the mismatch.

## Remaining gates

- Real browser consent/token exchange/inference for each supported provider and Cloudflare MCP. These changes do not establish provider entitlement or third-party client permission.
- OpenAI dynamic registration, endpoint/model compatibility, signed ID-token validation and refresh must be validated against current official documentation and a real supported account. Fixing callback dispatch is insufficient to certify Direct Connect.
- Google status 10 requires actual installed APK signing identity to match registration. INVALID_APP_ID needs SDK diagnostics and the authoritative downloaded Firebase config; no guessed config changes were made.
- MCP pending PKCE transactions remain in memory. App-process death during MCP authorization still requires recovery work. Automatic authorization completion does not guarantee the browser automatically foregrounds Ocean.
- Automatic Antigravity authentication is not implemented. OmniRoute's third-party desktop credential flow is not a generic Android OAuth registration. Do not borrow its client credentials.
- Local runtime server must be compiled for Android and deployed. New native build recipes are in the canonical package repository; no runtime binaries were fabricated.
- Private browser has real separate-process/profile, blocker and saved-session code, but VPN has no forwarding data plane and DNS selection alone does not prove resolver routing. Proxy exit verification remains unimplemented. No changes to the working browser crash fixes were made.

OmniRoute reference: https://github.com/diegosouzapw/OmniRoute/blob/release/v3.8.51/docs/guides/REMOTE-MODE.md . Its /v1/chat/completions is an inference gateway route, separate from OAuth /callback. Local login helpers receive loopback callbacks, exchange tokens and push/import credentials; remote browser/server loopback mismatches require a different flow.

## Follow-up: supported ChatGPT plan inference

Aligned the direct OpenAI integration to official SIWC open-source documentation: exact pending client binding, mandatory state and plan-use scopes, RS256 JWKS signature and issuer/audience/expiry/nonce validation, account catalog `models`/`slug`/`visibility`, and streamed `/v1/responses` with storage disabled. The agent adapts tool calls and results to Responses and requires `response.completed`; late quota failures and interrupted streams fail. A model listing is no longer inference proof. Numeric quota remains unknown without provider data. Tests cover tool-history conversion, incomplete/late-quota streams and generated RSA signatures with wrong audience, nonce and signature rejection. Live consent, physical-device return, refresh lifecycle and subscription accounting remain unverified.
