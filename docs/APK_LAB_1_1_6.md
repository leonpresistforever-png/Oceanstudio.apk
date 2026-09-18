# OceanStudio 1.1.6 — APK Lab and device-control checkpoint

This checkpoint extends the existing 1.1.6 crash-survival work without claiming Android privilege bypasses.

## APK Lab

Ocean Terminal now has a coherent APK workspace flow built on the package repository:

- `ocean-apk-lab tools` reports the available Android/APK toolchain.
- `ocean-apk-lab extract <package>` copies the installed package APK set that Android allows Ocean to read.
- `ocean-apk-lab open <apk>` creates a workspace. It prefers apktool for rebuildable resources/smali and falls back to raw extraction plus baksmali when apktool is unavailable.
- `ocean-apk-lab package <package>` extracts an installed package and opens its base APK as a workspace, while warning that split APK sets are not equivalent to a rebuilt base APK.
- `ocean-apk-lab build <workspace>` rebuilds with apktool, then zipaligns, signs and verifies through Ocean's existing signing wrapper.
- `ocean-apk-lab install <apk>` hands the APK to Android PackageInstaller; Android remains responsible for approval and signature rules.
- `ocean-apk-lab inspect <apk>` uses aapt when available.

The APK Lab scripts are executable files in the Ocean tools package.

## Android handoff

The app declares REQUEST_INSTALL_PACKAGES and Device Access exposes Android's per-app "Install unknown apps" settings page. FileProvider paths now permit explicit read grants for APKs produced in Ocean's files directory or selected shared storage, so PackageInstaller can receive an Ocean-built APK without relying on a raw file URI.

## Expanded opt-in live control

The existing Accessibility tool now supports:

- home / back
- recents
- notification shade
- quick settings
- tap
- long press
- swipe
- accessible-node click / type / scroll
- screen inspection and Android 11+ screenshots

These remain behind the user-enabled Accessibility service and Ocean's separate live-control switch. Protected screens and other apps' private sandboxes are not bypassed.

## Release gate

The normal Android validation workflow can publish an APK only when a main-branch commit contains `[release-apk]`. It verifies the APK signing certificate against the certificate used by OceanStudio 1.1.5.

If the certificate matches, the build can publish the stable version tag. If it differs, the build publishes a clearly labelled prerelease instead, because Android will not accept a differently signed APK as an in-place update over 1.1.5.


Release build retry: workflow YAML was corrected before the runner build was triggered.
