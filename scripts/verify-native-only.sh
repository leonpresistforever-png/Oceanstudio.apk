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
sed -n '/BuildConfig.DEBUG && BuildConfig.OCEAN_DEV_AUTH_BYPASS/p' android/app/src/main/java/studio/ocean/app/MainActivity.java | read -r guard \
  || fail "development authentication must require DEBUG and the dedicated flag"
sed -n '/release {/,/}/p' android/app/build.gradle | sed -n '/OCEAN_DEV_AUTH_BYPASS.*false/p' | read -r release_guard \
  || fail "release builds must hard-disable development authentication"
if find android/app/src/main -type f \( -name '*.java' -o -name '*.kt' -o -name '*.c' -o -name '*.cpp' \) \
    -exec sed -n '/com\.getcapacitor\|android\.webkit\.WebView\|com\.termux\|\/data\/data\/com\.termux/p' {} + \
    | sed -n '1p' | read -r forbidden; then
  fail "forbidden wrapper or Termux identity found"
fi
echo "Ocean native architecture verified."

# Ocean Terminal commands must remain local PTY children. Package downloads
# are separate; terminal/session code must not contain remote execution clients.
if find android/app/src/main/java/studio/ocean/app/terminal android/app/src/main/cpp -type f -print0 | xargs -0 sed -nE '/(HttpURLConnection|OkHttpClient|WebSocket|ssh |https?:\/\/.*(exec|shell|command))/p' | head -1 | grep -q .; then
  fail "remote command execution dependency found in Ocean Terminal runtime"
fi

# ART/Android owns fatal process signals and tombstone generation. JNI terminal
# diagnostics must never replace process-wide fatal signal handlers.
if sed -nE '/(sigaction|signal)\([^,]*(SIGSEGV|SIGABRT|SIGBUS|SIGILL|SIGFPE)/p' android/app/src/main/cpp/ocean_pty.c | grep -q .; then
  fail "Ocean PTY must not install process-wide fatal signal handlers"
fi
if grep -q 'WNOHANG' android/app/src/main/cpp/ocean_pty.c; then
  fail "Ocean PTY child lifecycle must use the dedicated blocking reaper"
fi
grep -q 'waitpid(p->pid,&status,0)' android/app/src/main/cpp/ocean_pty.c \
  || fail "Ocean PTY blocking waitpid reaper missing"
