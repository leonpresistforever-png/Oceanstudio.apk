#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "Architecture check failed: $1" >&2; exit 1; }
application_id="$(sed -n 's/^\s*applicationId "\([^"]*\)"\s*$/\1/p' android/app/build.gradle)"
[[ "$application_id" = studio.ocean.app ]] || fail "unexpected applicationId"
source packages/ocean-prefix.env
[[ "$OCEAN_APP_PACKAGE" = "$application_id" ]] || fail "package namespace mismatch"
[[ "$OCEAN_BUILD_PREFIX" = "/data/data/${application_id}/files/usr" ]] || fail "prefix mismatch"
rg -q '^\s*minSdkVersion = 28$' android/variables.gradle || fail "minSdk must be 28"
rg -q '^\s*targetSdkVersion = 28$' android/variables.gradle || fail "targetSdk must be 28"
if rg -n 'com\.getcapacitor|android\.webkit\.WebView|com\.termux|/data/data/com\.termux' android/app/src/main; then
  fail "forbidden wrapper or Termux identity found"
fi
echo "Ocean native architecture verified."
