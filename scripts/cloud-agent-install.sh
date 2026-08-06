#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="$ROOT/Ocean.studio-main"

cd "$ROOT"

if [[ ! -d "$APP_DIR" ]]; then
  unzip -qo Ocean.studio-main.zip -d .
fi

# Upstream zip ships TypeScript syntax in a .mjs file; strip it for Node ESM.
TERMINAL_WS="$APP_DIR/server/terminal-ws.mjs"
if grep -q 'Map<WebSocket' "$TERMINAL_WS" 2>/dev/null; then
  sed -i 's/new Map<WebSocket, pty.IPty>()/new Map()/' "$TERMINAL_WS"
  sed -i 's/ as Record<string, string>//' "$TERMINAL_WS"
  sed -i 's/function detectPort(data: string, ws: WebSocket)/function detectPort(data, ws)/' "$TERMINAL_WS"
fi

# Prevent workspace render loop when workspacePath is already set.
APP_STORE="$APP_DIR/src/store/appStore.ts"
if grep -q 'setWorkspacePath: (path) => set((s) => ({' "$APP_STORE" 2>/dev/null; then
  python3 - "$APP_STORE" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text()
old = """      setWorkspacePath: (path) => set((s) => ({
        workspacePath: path,
        workspaceConfig: s.workspaceConfig ? { ...s.workspaceConfig, workspacePath: path } : s.workspaceConfig,
      })),"""
new = """      setWorkspacePath: (path) => set((s) => {
        if (s.workspacePath === path) return {};
        return {
          workspacePath: path,
          workspaceConfig: s.workspaceConfig ? { ...s.workspaceConfig, workspacePath: path } : s.workspaceConfig,
        };
      }),"""
if old not in text:
    sys.exit(0)
path.write_text(text.replace(old, new, 1))
PY
fi

cd "$APP_DIR"
npm install
npm run build:electron
