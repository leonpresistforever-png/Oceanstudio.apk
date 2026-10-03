# Ocean native inference and account gateway

Local Models → **Connect** starts the bundled Android arm64 `llama-server` with the installed GGUF, a random loopback port, and a random API key. The runtime must generate text before Ocean saves CONNECTED. Ocean Agent uses that saved route and its encrypted key. A foreground service owns the server; disconnect terminates it. The server is installed in Android's executable native-library directory, including on Android 10 and later.

Providers → **Install & Start Ocean Gateway** installs the published upstream OmniRoute into a separate Ocean prefix directory. It uses the existing Ocean Node.js/npm packages and runs the actual upstream server. Antigravity IDE, Antigravity Models, Antigravity CLI and OpenAI/Codex Connect use the gateway. No provider authorization URL or account credential is fabricated by the app.

The same lifecycle command is available in Ocean Terminal:

```sh
ocean-gateway install
ocean-gateway start
ocean-gateway status
ocean-gateway stop
```

The default gateway port is 20129, separate from existing OmniRoute installations. The native app selects another loopback port if necessary. The gateway binds to 127.0.0.1. Its private state, generated management password, token encryption secrets and logs are under `$PREFIX/var/lib/ocean-gateway`. Installation always configures upstream `STORAGE_ENCRYPTION_KEY`, enabling actual AES-256-GCM encryption of persisted account credentials; an older environment gains that key without rotating its existing secrets. Ocean invokes the upstream server without its package postinstall hooks or optional native dependencies. No foreign terminal packages or patches are downloaded.

## Account authorization contracts

| Ocean account | Upstream adapter | Browser transaction | Native callback |
|---|---|---|---|
| Antigravity IDE / Models | `antigravity` | Google authorization code, upstream client and scopes | Bound random `127.0.0.1` port, `/callback` |
| Antigravity CLI | `agy` | Upstream Google CLI adapter | Bound random `127.0.0.1` port, `/callback` |
| OpenAI subscription | `codex` | Authorization code with S256 PKCE | `http://localhost:1455/auth/callback` |
| Remote MCP | Server-discovered OAuth authorization server | Dynamic registration where available, S256 PKCE and resource binding | Bound random loopback port, `/callback` |

The callback listener binds **before** authorization begins. The app checks that the upstream transaction's state, redirect URI and PKCE challenge match the receiver and verifier. Invalid paths, duplicate state/code parameters and mismatched state cannot exchange credentials. Accepted callbacks return the browser to Ocean with an independent, one-use ticket; the deep link carries neither the authorization code nor a token.

The Antigravity adapters currently use an authorization-code flow without PKCE upstream. Ocean preserves that actual contract rather than claiming those flows use S256. Codex uses its provider-specific registered callback and S256. Firebase app sign-in is independent of these model account transactions.

Ocean exchanges the code through the gateway's authenticated management API, forces live model discovery, issues an account-scoped inference key, and requires an actual model completion. Provider tokens, refresh, encryption and request/response translation stay inside the unmodified upstream gateway. Ocean stores its own inference key in Android Keystore-backed storage. Inference requests do not carry the broader management cookie.

`/v1/chat/completions` is the inference endpoint. `/v1` is **not** an OAuth callback URL. When at least two gateway accounts pass inference checks, Ocean creates an upstream priority combo containing each actual model and connection ID. Its inference key is restricted to those accounts. The gateway performs retry, refresh and fallback; Ocean's router selects the model that passed validation.

Quota refresh calls `/api/usage/{connectionId}`. The native quota record uses the selected model's reported window or actual account-wide windows, with the reported plan and reset time. Unknown quota stays unknown; no subscription, balance or entitlement is invented. Inference uses the connected subscription adapter rather than silently switching that account to a separately billed API-key route.

MCP authorization returns to the MCPs screen automatically. A foreground service holds its native callback listener while consent is open, then the client exchanges the token and performs `initialize`, `notifications/initialized` and actual capability discovery. CONNECTED follows that handshake.

## Reproducible source and verification

- Official llama.cpp source: `ggml-org/llama.cpp`, commit `4d9176092d00586775af140581bb0b558ddc4389`. The Android build checks arm64, the Android linker and absence of unbundled llama/ggml/C++/OpenMP/OpenSSL dependencies. APK packaging must preserve the executable's recorded SHA-256.
- Published OmniRoute: npm `omniroute@3.8.51`, exact registry SHA-512 checked by the canonical Ocean gateway command. Source was inspected at `diegosouzapw/OmniRoute` commit `23a11484862b3bb589a55e85b00e4ac53ffeb234`; integration checks exercise the published 3.8.51 package, not an unpublished checkout.
- Canonical Ocean gateway source: `leonpresistforever-png/Oceanstudio-packages`, commit `9780660ffb0c7ed09d86c52c8cee4f90d1ee18a3`. APK preparation checks the command's exact SHA-256. Existing package payloads are unchanged.

`scripts/tests/test_gateway_integration.py` starts the **actual published gateway** and an actual llama.cpp server with the official `ggml-org/models` tiny GGUF fixture. It compiles Ocean's production Java HTTP client and checks management authentication, rejection of anonymous inference, real model discovery, generated text, an invalid account key followed by successful account fallback, actual provider authorization URL contracts, Codex's mathematically verified S256 challenge, and the native callback HTTP redirect. It also inspects the actual SQLite credential columns for AES-GCM ciphertext and checks that adding the storage encryption key preserves an existing gateway's other secrets.

`scripts/tests/test_runtime_auth_boundaries.py` exercises real JVM sockets, state rejection, callback paths, both application return destinations and loopback-only inference URL validation. The connection-store tests use actual atomic JSON files to check visibility across the provider screen, model manager and agent, concurrent saves, and selection of a verified account over older failed setup records. Android CI compiles native and Java sources, runs unit tests, verifies APK contents, alignment, native checksums and signing.

These checks do not complete a person's provider consent or prove that person's paid plan. Live account consent, subscription quota responses and physical-device execution still require running this APK on the user's device. The integration model is a small test fixture, not a fabricated provider response.
