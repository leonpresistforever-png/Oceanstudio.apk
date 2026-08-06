import { WebSocketServer, WebSocket } from 'ws';
import * as pty from 'node-pty';
import os from 'os';

const PORT = Number(process.env.OCEAN_TERMINAL_WS_PORT || 3847);
const shell = os.platform() === 'win32' ? 'powershell.exe' : process.env.SHELL || '/bin/bash';
const shellName = os.platform() === 'win32' ? 'PowerShell' : 'Bash';

const wss = new WebSocketServer({ port: PORT });
const sessions = new Map();

console.log(`[ocean-terminal] Dev shell server (${shellName}) on ws://localhost:${PORT}`);

wss.on('connection', (ws) => {
  const ptyProcess = pty.spawn(shell, [], {
    name: 'xterm-256color',
    cols: 120,
    rows: 30,
    cwd: process.cwd(),
    env: { ...process.env, TERM: 'xterm-256color', OCEAN_TERMINAL_TYPE: 'shell' },
  });

  sessions.set(ws, ptyProcess);

  ptyProcess.onData((data) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'data', data }));
      detectPort(data, ws);
    }
  });

  ptyProcess.onExit(({ exitCode }) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'exit', code: exitCode }));
    }
    sessions.delete(ws);
  });

  ws.on('message', (raw) => {
    try {
      const msg = JSON.parse(raw.toString());
      if (msg.type === 'input') ptyProcess.write(msg.data);
      if (msg.type === 'resize') ptyProcess.resize(msg.cols, msg.rows);
      if (msg.type === 'clear') {
        ptyProcess.write(os.platform() === 'win32' ? 'cls\r' : 'clear\r');
      }
      if (msg.type === 'restart') {
        ptyProcess.kill();
        sessions.delete(ws);
        ws.close();
      }
    } catch {
      ptyProcess.write(raw.toString());
    }
  });

  ws.on('close', () => {
    ptyProcess.kill();
    sessions.delete(ws);
  });

  ws.send(JSON.stringify({ type: 'ready', shell: shellName, platform: os.platform() }));
});

function detectPort(data, ws) {
  const patterns = [/localhost:(\d{4,5})/gi, /127\.0\.0\.1:(\d{4,5})/gi, /http:\/\/localhost:(\d{4,5})/gi];
  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(data)) !== null) {
      const port = parseInt(match[1], 10);
      if (port > 1024 && port < 65536 && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'port', port, url: `http://localhost:${port}` }));
      }
    }
  }
}
