# Headless agent update — 2026-09-16

Published: [OceanStudio 1.1.4](https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.1.4). Built source commit: `fad6505bcf061e199d8aeff78fc3894bc7ae4843`. The source changes are on main; this checkpoint adds the verified APK alias and release evidence.

The 1.1.3 terminal was confirmed working by the user, but model transport sent no tool declarations and extracted only response text. This update connects structured Gemini, Claude and OpenAI-compatible calls to OceanTerminalRuntimeService and returns actual output/exit status. Provider call IDs/signatures are retained, calls are bounded and cancellable, and assistant prose is not executed as shell code. Command and interactive sessions are separate. Fast child exits now drain pending output.

Other changes: reject provider names such as `google` as model IDs; honor custom base URLs; handle direct open-terminal requests; preserve bounded chat history; reset it with New Chat. The existing layout now uses grey agent labels, outlined icons, fine separators, per-command output rows and basic native message formatting.

Completed validation:
- 42 Gradle unit tests passed, including 17 model protocol/configuration cases.
- Host lifecycle check passed: 20 fast processes preserved exact UTF-8 output and exit 7; cancellation reaped the child. Android logging/context and NativePty are host adapters in that test.
- APK signature, ZIP alignment, version and packaged APT catalogue passed.
- Signing identity matches 1.1.3. Bootstrap assets and Ocean PTY native library are unchanged.
- Android headless-service test sources compiled.

Remaining acceptance: run the service tests on an Android device; use a configured BYOK provider to ask for a harmless command such as `printf 'ocean-agent-ok\n'`, confirm real output and exit status, test Stop, and verify opening the terminal preserves the interactive session. No attached device or live model API key was available in the build workspace.

The workspace was cleared during earlier work; remote source checkpoints and the private signing backup allowed recovery. The local Java build reused the verified, unchanged PTY library to reduce build cost. Normal source builds still use CMake/NDK. Publication attached only the APK and did not dispatch an Actions build.

The package pool remains 1,036 distinct indexed names. Official-source expansion and full package installation/execution acceptance are unfinished separate work.

Protocol references: https://ai.google.dev/api/generate-content ; https://ai.google.dev/gemini-api/docs/function-calling ; https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools ; https://developers.openai.com/api/docs/guides/function-calling .
