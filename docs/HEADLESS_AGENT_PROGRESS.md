# Headless agent update — 2026-09-16

Base: main e82a7dbc7fa531299ed02c0d925efa0c43f37d29 (published APK 1.1.3).

The 1.1.3 terminal has been confirmed working by the user. Its model transport sent no tool declarations and only extracted response text. This update connects structured Gemini, Claude and OpenAI-compatible calls to OceanTerminalRuntimeService, returns real output/exit status, preserves provider call IDs/signatures, and bounds tool runs. Commands remain in the Ocean prefix; assistant prose is never treated as shell code.

Additional changes: model-ID validation (reject provider names such as `google`); correctly honored base URLs; direct open-terminal action; cancellation; command/interactive session separation; draining output after child exit; short chat history with a real New Chat reset. UI retains its layout and adds grey agent text, vector icons, fine separators, per-command output rows and basic native message formatting.

Verification in progress:
- Host Java lifecycle smoke: PASS, 20 fast subprocess exits preserve complete UTF-8 output and actual exit 7; cancellation reaps the child. This uses host process adapters, not Android JNI.
- Provider protocol/configuration regression tests: written; full Gradle execution pending.
- Device service tests: written; no attached Android device available here.
- Live BYOK calls: pending user device validation; no user model key available in this workspace.
- APK 1.1.4 (versionCode 6): build/sign/verify/publication pending. Reuse the privately backed-up 1.1.3 signing key.

The workspace was cleared by automated maintenance; source and signing key were recovered. To keep build cost down, the local Java-only APK build reuses the unchanged, hash-verified Ocean PTY library from the 1.1.3 APK. The repository's normal CMake/NDK source build remains intact.

This change does not claim additional packages: the pool still has 1,036 unique indexed names. Earlier official-source expansion and full device package acceptance remain separate unfinished work.

Protocol references: https://ai.google.dev/api/generate-content ; https://ai.google.dev/gemini-api/docs/function-calling ; https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools ; https://developers.openai.com/api/docs/guides/function-calling .
