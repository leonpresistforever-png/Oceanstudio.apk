#!/usr/bin/env bash
set -euo pipefail
ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
PREFIX="$ROOT/prefix"
BIN="$ROOT/bin"
LOG="$ROOT/apt.log"
mkdir -p "$PREFIX/var/run" "$BIN"

cat > "$BIN/apt-get" <<'SH'
#!/usr/bin/env bash
printf '%s start %s\n' "$$" "$*" >> "$OCEAN_TEST_LOG"
sleep "${OCEAN_TEST_SLEEP:-0}"
printf '%s end %s\n' "$$" "$*" >> "$OCEAN_TEST_LOG"
SH
chmod +x "$BIN/apt-get"
for tool in apt-cache dpkg-query; do ln -s apt-get "$BIN/$tool"; done

PKG="$PWD/ocean-packages/packages/ocean-pkg/pkg"
export PREFIX PATH="$BIN:$PATH" OCEAN_TEST_LOG="$LOG" OCEAN_PKG_LOCK_WAIT=5
OCEAN_TEST_SLEEP=1 bash "$PKG" update & first=$!
sleep .2
OCEAN_TEST_SLEEP=0 bash "$PKG" install ocean-hello & second=$!
wait "$first" "$second"
test "$(grep -c ' start ' "$LOG")" = 2
grep -q 'end update' "$LOG"
test "$(sed -n '2p' "$LOG")" = "$(grep 'end update' "$LOG")"

# A dead/non-numeric owner is recovered rather than becoming a permanent busy result.
mkdir -p "$PREFIX/var/run/ocean-pkg.lock.d"
printf 'not-a-pid\n' > "$PREFIX/var/run/ocean-pkg.lock.d/pid"
OCEAN_TEST_SLEEP=0 bash "$PKG" search ocean
test ! -e "$PREFIX/var/run/ocean-pkg.lock.d"
echo 'Ocean pkg atomic lock tests passed.'
