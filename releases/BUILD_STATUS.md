# OceanStudio build checkpoint — 2026-09-16

OceanStudio 1.1.4 (version code 6) is published as the latest GitHub release:
https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.1.4

The ARM64 debug/testing APK is built from `fad6505bcf061e199d8aeff78fc3894bc7ae4843`. Its committed alias is `releases/OceanStudio-latest-debug.apk`; the release asset is `OceanStudio-1.1.4-arm64-debug.apk`. APK SHA-256: `bbaa174a9c2fa36a55a80432f9bad16e25c91442409b837536aa6829ca8cc4aa`.

The signing certificate matches 1.1.3, permitting an in-place update from that version. Preserve the private signing backup and never commit it or its password. Earlier installations signed with the old pre-1.1.3 key are not update-compatible. Production Firebase authentication remains unconfigured; this debug APK retains the visibly labelled testing entry.

The model now receives structured terminal tools and real native-service output/exit status. Gemini, Claude and OpenAI-compatible protocols have fixture coverage. The UI retains its layout with grey agent labels, vector icons, subtle separators and expandable tool output.

Verification: 42 Gradle unit tests passed; APK signature, alignment, version and packaged catalogue passed. The host lifecycle test passed 20 fast UTF-8 command exits plus cancellation using host adapters. Android service tests compiled but were not executed on a device. No live model call was made without a user BYOK key. Exact scope is recorded in `OceanStudio-1.1.4-validation.json`.

To reduce quota use, this local build reused the unchanged, verified Ocean PTY library from 1.1.3; bootstrap/catalogue assets are byte-identical. The normal CMake/NDK source build remains intact. Main checkpoint commits skip CI, and no Actions build was dispatched for this release. Only the APK was attached to the release.

Package work remains separate: 1,036 distinct indexed names / 1,046 records. Existing packages include Termux-derived files and do not have complete source provenance. Four pinned independent source recipes exist, and cmark/kilo candidates were previously saved; Android package acceptance and expansion to 2,200 remain unfinished. Do not claim that this release completes that work.
