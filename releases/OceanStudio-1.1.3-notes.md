OceanStudio 1.1.3 — signed ARM64 testing APK.

Includes automatic package catalogue refresh, repair of the legacy dpkg file-list failure, terminal styling and the keyboard toggle. The embedded catalogue contains 1,036 distinct package names. Fifty Android unit-test executions and focused catalogue/dpkg/migration checks passed; APK signature, alignment and embedded bootstrap/catalogue hashes were verified.

This retains the existing debug build's visibly labelled testing entry because Firebase login is unconfigured. It is not a production-authenticated build.

Signing compatibility: a new preserved key is used. This APK cannot update older installations signed with the previous key. Keep existing app data backed up before changing installation; uninstalling removes app-private data.

Physical-device installation and execution of every package remain unverified. The existing runtime still includes legacy Termux-derived files. The requested additional package expansion and source-only bootstrap are not completed or included in this release.

Download the APK directly; no outer ZIP is needed.

SHA-256: 75f9d0372284ee9010bbe1d86d1eb1db2f3d070d0177c779e34d9e154615cf50

The source and a copy of this APK are saved on main.

