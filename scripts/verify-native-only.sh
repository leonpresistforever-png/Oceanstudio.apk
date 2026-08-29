#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "Architecture check failed: $1" >&2; exit 1; }
application_id="$(sed -n 's/^\s*applicationId "\([^"]*\)"\s*$/\1/p' android/app/build.gradle)"
[[ "$application_id" = studio.ocean.app ]] || fail "unexpected applicationId"
source packages/ocean-prefix.env
[[ "$OCEAN_APP_PACKAGE" = "$application_id" ]] || fail "package namespace mismatch"
[[ "$OCEAN_BUILD_PREFIX" = "/data/data/${application_id}/files/usr" ]] || fail "prefix mismatch"
min_sdk="$(sed -n 's/^\s*minSdkVersion = \([0-9][0-9]*\)\s*$/\1/p' android/variables.gradle)"
target_sdk="$(sed -n 's/^\s*targetSdkVersion = \([0-9][0-9]*\)\s*$/\1/p' android/variables.gradle)"
[[ "$min_sdk" = 28 ]] || fail "minSdk must be 28"
[[ "$target_sdk" = 28 ]] || fail "targetSdk must be 28"
if find android/app/src/main -type f \( -name '*.java' -o -name '*.kt' -o -name '*.c' -o -name '*.cpp' \) \
    -exec sed -n '/com\.getcapacitor\|android\.webkit\.WebView\|com\.termux\|\/data\/data\/com\.termux/p' {} + \
    | sed -n '1p' | read -r forbidden; then
  fail "forbidden wrapper or Termux identity found"
fi
echo "Ocean native architecture verified."
