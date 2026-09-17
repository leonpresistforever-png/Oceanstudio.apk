OceanStudio 1.1.5 adds Runtime Ports and optional device tools while preserving the existing terminal bootstrap and package environment.

Changes:
- Route agent terminal output through Android's main thread, addressing a crash path when commands begin producing output.
- Add a clean Runtime Ports page from Terminal Tools and the sidebar. Discover listeners, enter a port manually, and open local HTTP or noVNC services.
- Connect the agent to real page inspection, screenshots, clicking, typing, keyboard and canvas interaction. Report unavailable pages and cancelled actions instead of pretending they succeeded.
- Add Device Access: shared-file and modify-settings permission pages, Accessibility setup, a separate live-control switch, and a Stop notification.
- Add agent tools for launchable-app discovery, app launch, visible-screen inspection, screenshots, and supported gestures/actions.
- Improve BYOK field sizes, spacing and separators; retain the existing simple layout and grey agent identity.

Verification:
- Local unit tests, APK assembly and Android instrumentation-source compilation. See the attached verification.json for exact results and artifact checksum.
- APK uses the existing signing certificate for updates from v1.1.4.
- Bootstrap archive and native arm64 PTY library match v1.1.4 byte for byte. Normal source builds retain the CMake configuration; the local build reused the verified unchanged library to conserve build quota.
- No GitHub Actions run was needed for this release.

Use:
- Install the APK as an update. This remains the explicitly labelled debug distribution used by prior Ocean releases.
- In Tools → Device Access, grant only the access you want. Enable the system Accessibility service, return to Ocean, then enable live agent control. Stop revokes live control.
- Live screen content and screenshots used by the agent go to your configured BYOK provider.
- Start a compatible local server in Terminal, then open Runtime Ports. Android may hide socket tables; common-port probes and manual entry remain available.

Limits and remaining work:
- No physical-device acceptance test was available. The reported crash, live gestures, screenshots and noVNC behavior need confirmation on the user's device; this is not a claim that every app/task has been tested.
- Accessibility does not grant root access, other apps' private storage, or protected-screen capture. Screenshot tools require Android 11+. OEM permission restrictions still apply.
- Headless commands run inside Ocean's terminal. This does not make arbitrary Android apps headless or install Blender, DaVinci Resolve or Docker. Those require compatible runtimes and separately available packages.
- The package pool is unchanged. The earlier request for 1000 additional compiled packages is still open; this release does not claim 2200 packages or audited official-source-only provenance.

Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService and https://developer.android.com/training/data-storage/manage-all-files
