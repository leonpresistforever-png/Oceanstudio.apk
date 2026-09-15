# OceanStudio build checkpoint — 2026-09-15

PR #3 was merged into main as e5244e83eb4b5cfe5334e2c9ab775546d31dcb5b. It includes automatic catalogue refresh, dpkg metadata repair, terminal UI/keyboard controls and the official-source candidate builder.

A signed ARM64 debug/testing APK, version 1.1.3 (code 5), has been built from efc6567ae2021f5b689fc7d684125763a840bb9e. The committed alias is `releases/OceanStudio-latest-debug.apk`. The versioned GitHub release `v1.1.3` is published and marked latest, with the signed APK attached.

APK SHA-256: 75f9d0372284ee9010bbe1d86d1eb1db2f3d070d0177c779e34d9e154615cf50.

50 Gradle unit-test executions, seven catalogue tests, the isolated dpkg reproduction/repair regression and frontend migration checks passed. The packaged catalogue/bootstrap, APK signature and ZIP alignment were verified. The validation JSON records precise scope; no device-wide or all-package guarantee is claimed.

The original APK signing key was unavailable. A new signing key is preserved privately in `OceanStudio-private-signing-backup-20260915.zip` in the user's saved files. Do not upload that private backup to GitHub. It contains the key and its password, not the GitHub access token. Reuse it for future APKs. The new APK cannot update old installations signed with the previous key; retain existing user data until backed up. Firebase login remains unconfigured; this APK retains the prior debug build's visibly labelled testing entry.

The live APT pool is unchanged: 1,036 distinct names. The existing pool includes Termux-derived files and lacks complete source provenance. The independent source builder has four pinned recipes. This session rebuilt and retained cmark and kilo; subsequent source downloads were blocked. The privately saved candidate ZIP is not a live APT update and Android installation/execution tests are pending. Expansion to 2,200 packages and a fully upstream-source-built bootstrap remain unfinished.

Future work should begin with this checkpoint and the release assets. Preserve each completed batch before beginning another one; reuse caches and avoid re-running passing checks without a concrete reason. Do not describe this checkpoint as completion of all requested package work.

Automatic approval review rejected the original combined release upload because it included supporting reports and upstream-source archives beyond the explicitly requested APK. Publication was narrowed to the APK only. Candidate sources/binaries, the validation report and signature report were saved privately. No supporting archive was uploaded to the release.

Download: https://github.com/leonpresistforever-png/Oceanstudio.apk/releases/tag/v1.1.3

