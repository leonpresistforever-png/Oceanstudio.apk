# OceanStudio 1.1.5 recovery checkpoint

Status: source recovery in progress; no 1.1.5 release published.

Scope: agent output UI thread crash fix; BYOK spacing/type cleanup; Runtime Ports list/browser and real agent visual tools; user-enabled file/settings/accessibility controls with stop; signed APK release.

Recovered: Runtime Ports source, provider screenshot wiring, terminal Ports button, output main-thread dispatch, browser compilation fixes.

Pending: Device Access recovery and verification, BYOK polish, final build/signature/asset checks and release.

Earlier 49-test build passed before workspace loss, but that binary is unavailable. Rerun verification on recovered source. No connected Android device; do not claim live-device acceptance.

Separate unfinished work: expand package pool; existing pool is 1036 distinct names / 1046 records and is not certified official-source-only. Never claim 2200 packages or unsupported desktop apps work.

Use local builds and skip-ci commits to conserve Actions quota. Stable signing material remains private and must never be committed.
