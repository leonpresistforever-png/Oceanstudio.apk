OceanStudio 1.1.3 adds automatic package-catalogue refresh, repairs the legacy dpkg file-list error shown in the screenshots, and includes the terminal styling and keyboard toggle.

This is a signed ARM64 **testing APK**, matching the previous release's debug/testing mode. Firebase login is not configured; the existing visibly labelled development entry is available. This is not a production-authenticated build.

**Installation compatibility:** this APK uses a new, preserved signing key. It cannot update older APKs signed with the previous key. Keep the existing app and its data until you have a backup or the original signing key; uninstalling deletes app-private data. Future APKs must reuse the new key.

Validation completed:
- 50 Android unit-test executions passed with zero failures/errors.
- Seven automatic-catalogue tests passed.
- Real dpkg file-list regression reproduced exit code 2 and verified the corrected format.
- Frontend migration checks passed for the actual bundled script, backups, custom files, symlinks and package-manager locks.
- Final APK signature, ZIP alignment, bundled bootstrap and catalogue hashes verified.
- Packaged catalogue contains 1,036 distinct package names (1,046 records).

Limits: physical-device installation, keyboard operation and every package's execution have not been verified. The existing runtime still includes legacy Termux-derived files; it is not an entirely upstream-source-built distribution. No new packages have been added to the live APT pool. The separate source-candidates ZIP contains cmark and kilo built from pinned official sources, with receipts and source archives; these are quarantined candidates pending Android runtime tests. The 2,200-package expansion remains incomplete.

The APK can be downloaded directly; no outer ZIP is required. Its SHA-256 and validation report are attached. Source and the committed APK copy are saved on main.
