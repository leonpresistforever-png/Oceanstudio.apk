const WS_URL = import.meta.env.VITE_TERMINAL_WS_URL || 'ws://localhost:3847';

type DataHandler = (id: string, data: string) => void;
type ExitHandler = (id: string, code: number) => void;
type PortHandler = (port: number, url: string) => void;

interface Session {
  ws: WebSocket;
}

const sessions = new Map<string, Session>();
const dataHandlers = new Set<DataHandler>();
const exitHandlers = new Set<ExitHandler>();
const portHandlers = new Set<PortHandler>();

export async function isDevTerminalAvailable(): Promise<boolean> {
  if (typeof WebSocket === 'undefined') return false;
  return new Promise((resolve) => {
    const ws = new WebSocket(WS_URL);
    const timer = setTimeout(() => { ws.close(); resolve(false); }, 1200);
    ws.onopen = () => { clearTimeout(timer); ws.close(); resolve(true); };
    ws.onerror = () => { clearTimeout(timer); resolve(false); };
  });
}

function wireWs(id: string, ws: WebSocket) {
  sessions.set(id, { ws });

  ws.onmessage = (event) => {
    try {
      const msg = JSON.parse(event.data);
      if (msg.type === 'data') dataHandlers.forEach((cb) => cb(id, msg.data));
      if (msg.type === 'exit') exitHandlers.forEach((cb) => cb(id, msg.code));
      if (msg.type === 'port') portHandlers.forEach((cb) => cb(msg.port, msg.url));
    } catch { /* ignore */ }
  };

  ws.onclose = () => sessions.delete(id);
}

export async function createSession(id: string): Promise<void> {
  if (sessions.has(id)) return;
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(WS_URL);
    ws.onopen = () => { wireWs(id, ws); resolve(); };
    ws.onerror = () => reject(new Error('Terminal server unavailable'));
  });
}

export function writeSession(id: string, data: string) {
  sessions.get(id)?.ws.send(JSON.stringify({ type: 'input', data }));
}

export function resizeSession(id: string, cols: number, rows: number) {
  sessions.get(id)?.ws.send(JSON.stringify({ type: 'resize', cols, rows }));
}

export function killSession(id: string) {
  sessions.get(id)?.ws.close();
  sessions.delete(id);
}

export function clearSession(id: string) {
  sessions.get(id)?.ws.send(JSON.stringify({ type: 'clear' }));
}

export function restartSession(id: string) {
  killSession(id);
  return createSession(id);
}

export function onData(cb: DataHandler) {
  dataHandlers.add(cb);
  return () => dataHandlers.delete(cb);
}

export function onExit(cb: ExitHandler) {
  exitHandlers.add(cb);
  return () => exitHandlers.delete(cb);
}

export function onPort(cb: PortHandler) {
  portHandlers.add(cb);
  return () => portHandlers.delete(cb);
}
