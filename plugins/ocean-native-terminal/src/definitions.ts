export type TerminalType = 'shell' | 'cloud' | 'native';

export interface CreateOptions {
  id: string;
  cwd?: string;
}

export interface WriteOptions {
  id: string;
  data: string;
}

export interface ResizeOptions {
  id: string;
  cols: number;
  rows: number;
}

export interface SessionOptions {
  id: string;
}

export interface SetupStatus {
  ready: boolean;
  prefix: string;
  shell: string;
  message: string;
}

export interface OceanNativeTerminalPlugin {
  setup(): Promise<SetupStatus>;
  getSetupStatus(): Promise<SetupStatus>;
  getHomePath(): Promise<{ path: string }>;
  readDir(options: { path: string }): Promise<{ items: Array<{ name: string; path: string; type: 'file' | 'directory' }> }>;
  readFile(options: { path: string }): Promise<{ content: string }>;
  writeFile(options: { path: string; content: string }): Promise<void>;
  create(options: CreateOptions): Promise<void>;
  write(options: WriteOptions): Promise<void>;
  resize(options: ResizeOptions): Promise<void>;
  kill(options: SessionOptions): Promise<void>;
  restart(options: SessionOptions): Promise<void>;
  clear(options: SessionOptions): Promise<void>;
  addListener(
    eventName: 'terminalData',
    listenerFunc: (event: { id: string; data: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'terminalExit',
    listenerFunc: (event: { id: string; code: number }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'portDetected',
    listenerFunc: (event: { port: number; url: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'setupProgress',
    listenerFunc: (event: { message: string; percent: number }) => void
  ): Promise<{ remove: () => void }>;
}
