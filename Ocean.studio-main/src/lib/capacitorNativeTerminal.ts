import { OceanNativeTerminal } from 'ocean-native-terminal';
import type { SetupStatus } from 'ocean-native-terminal';

type DataCallback = (id: string, data: string) => void;
type ExitCallback = (id: string, code: number) => void;
type PortCallback = (port: number, url: string) => void;

const dataCallbacks = new Set<DataCallback>();
const exitCallbacks = new Set<ExitCallback>();
const portCallbacks = new Set<PortCallback>();

let listenersReady = false;
let listenerHandles: Array<{ remove: () => void }> = [];

async function ensureListeners(): Promise<void> {
  if (listenersReady) return;
  listenersReady = true;

  const dataHandle = await OceanNativeTerminal.addListener('terminalData', (event) => {
    dataCallbacks.forEach((cb) => cb(event.id, event.data));
  });
  const exitHandle = await OceanNativeTerminal.addListener('terminalExit', (event) => {
    exitCallbacks.forEach((cb) => cb(event.id, event.code));
  });
  const portHandle = await OceanNativeTerminal.addListener('portDetected', (event) => {
    portCallbacks.forEach((cb) => cb(event.port, event.url));
  });

  listenerHandles = [dataHandle, exitHandle, portHandle];
}

export async function setupNativeTerminal(): Promise<SetupStatus> {
  return OceanNativeTerminal.setup();
}

export async function getNativeSetupStatus(): Promise<SetupStatus> {
  return OceanNativeTerminal.getSetupStatus();
}

export async function getNativeHomePath(): Promise<string> {
  const { path } = await OceanNativeTerminal.getHomePath();
  return path;
}

export async function readNativeDir(path: string) {
  const { items } = await OceanNativeTerminal.readDir({ path });
  return items;
}

export async function readNativeFile(path: string): Promise<string> {
  const { content } = await OceanNativeTerminal.readFile({ path });
  return content;
}

export async function writeNativeFile(path: string, content: string): Promise<void> {
  await OceanNativeTerminal.writeFile({ path, content });
}

export function onNativeSetupProgress(callback: (message: string, percent: number) => void): () => void {
  let handle: { remove: () => void } | null = null;
  void OceanNativeTerminal.addListener('setupProgress', (event) => {
    callback(event.message, event.percent);
  }).then((h) => { handle = h; });
  return () => { handle?.remove(); };
}

export async function createNativeSession(id: string, cwd?: string): Promise<void> {
  await ensureListeners();
  await OceanNativeTerminal.create({ id, cwd });
}

export async function writeNativeSession(id: string, data: string): Promise<void> {
  await OceanNativeTerminal.write({ id, data });
}

export async function resizeNativeSession(id: string, cols: number, rows: number): Promise<void> {
  await OceanNativeTerminal.resize({ id, cols, rows });
}

export async function killNativeSession(id: string): Promise<void> {
  await OceanNativeTerminal.kill({ id });
}

export async function restartNativeSession(id: string, cwd?: string): Promise<void> {
  await ensureListeners();
  await OceanNativeTerminal.kill({ id });
  await OceanNativeTerminal.create({ id, cwd });
}

export async function clearNativeSession(id: string): Promise<void> {
  await OceanNativeTerminal.clear({ id });
}

export function onNativeData(callback: DataCallback): () => void {
  dataCallbacks.add(callback);
  void ensureListeners();
  return () => dataCallbacks.delete(callback);
}

export function onNativeExit(callback: ExitCallback): () => void {
  exitCallbacks.add(callback);
  void ensureListeners();
  return () => exitCallbacks.delete(callback);
}

export function onNativePort(callback: PortCallback): () => void {
  portCallbacks.add(callback);
  void ensureListeners();
  return () => portCallbacks.delete(callback);
}

export function disposeNativeTerminal(): void {
  listenerHandles.forEach((h) => h.remove());
  listenerHandles = [];
  listenersReady = false;
  dataCallbacks.clear();
  exitCallbacks.clear();
  portCallbacks.clear();
}
