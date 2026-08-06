export interface OceanAPI {
  platform: {
    get: () => Promise<{
      platform: string;
      isElectron: boolean;
      isDesktop: boolean;
      isMobile: boolean;
      isWeb: boolean;
      hasNativeShell?: boolean;
      shellLabel?: string;
      preferredTerminal?: 'shell' | 'cloud' | 'native';
    }>;
    getPreferredTerminal: () => Promise<'shell' | 'cloud' | 'native'>;
  };
  fs: {
    readDir: (path: string) => Promise<unknown>;
    readFile: (path: string) => Promise<string>;
    writeFile: (path: string, content: string) => Promise<void>;
    selectWorkspace: () => Promise<string | null>;
    getDefaultWorkspace: () => Promise<string>;
  };
  terminal: {
    create: (options: { id: string; type: 'shell' | 'cloud' | 'native'; cwd?: string }) => Promise<void>;
    write: (id: string, data: string) => Promise<void>;
    resize: (id: string, cols: number, rows: number) => Promise<void>;
    kill: (id: string) => Promise<void>;
    restart: (id: string) => Promise<void>;
    clear: (id: string) => Promise<void>;
    onData: (callback: (id: string, data: string) => void) => () => void;
    onExit: (callback: (id: string, code: number) => void) => () => void;
  };
  preview: {
    getPorts: () => Promise<{ port: number; url: string; label?: string; source?: string; openUrl?: string }[]>;
    registerPort: (port: number, label?: string) => Promise<void>;
    registerCloudShellPort?: (port: number) => Promise<{ port: number; url: string; openUrl: string; source: string }>;
    unregisterPort: (port: number) => Promise<void>;
    onPortDetected: (callback: (port: number, url: string, meta?: { source?: string; openUrl?: string; label?: string }) => void) => () => void;
  };
  upload: {
    selectFiles: () => Promise<{ name: string; path: string; content: string; mimeType: string }[]>;
    selectImages: () => Promise<{ name: string; path: string; content: string; mimeType: string }[]>;
  };
  agent: {
    send: (message: string, context: unknown) => Promise<string>;
    pause: () => Promise<void>;
    getStatus: () => Promise<string>;
    onEvent: (callback: (event: unknown) => void) => () => void;
  };
  shell: {
    openExternal: (url: string) => Promise<void>;
  };
  cloudshell: {
    authenticate: () => Promise<{ connected: boolean; authenticated: boolean; environmentReady: boolean; email?: string; message: string }>;
    activate: () => Promise<{ connected: boolean; authenticated: boolean; environmentReady: boolean; email?: string; message: string }>;
    getStatus: () => Promise<{ connected: boolean; authenticated: boolean; environmentReady: boolean; email?: string; message: string }>;
    disconnect: () => Promise<void>;
    registerPort: (port: number) => Promise<{ port: number; url: string; openUrl: string; source: 'cloudshell' }>;
    getPortPreviews: () => Promise<{ port: number; url: string; openUrl: string; source: 'cloudshell' }[]>;
  };
  plugins?: {
    list: () => Promise<unknown[]>;
    install: (manifest: unknown) => Promise<void>;
    uninstall: (id: string) => Promise<void>;
    start: (id: string) => Promise<void>;
    stop: (id: string) => Promise<void>;
    setEnabled: (id: string, enabled: boolean) => Promise<void>;
    updateConfig: (id: string, config: Record<string, unknown>) => Promise<void>;
    getTools: () => Promise<{ pluginId: string; name: string; description: string }[]>;
  };
  mcp?: {
    list: () => Promise<unknown[]>;
    connect: (config: unknown) => Promise<{ connectorId: string; status: string; error?: string }>;
    disconnect: (id: string) => Promise<void>;
    authenticate: (config: unknown, clientId?: string, clientSecret?: string) => Promise<{ success: boolean; message: string }>;
    test: (id: string) => Promise<{ ok: boolean; message: string }>;
    getAgentConfig: () => Promise<Record<string, unknown>>;
  };
  github?: {
    getStatus: () => Promise<{ connected: boolean; authenticated: boolean; username?: string; avatarUrl?: string; message: string }>;
    authenticate: () => Promise<{ connected: boolean; authenticated: boolean; username?: string; message: string }>;
    disconnect: () => Promise<void>;
    listRepos: () => Promise<unknown[]>;
    getRepo: (owner: string, repo: string) => Promise<unknown>;
    importRepo: (url: string, dir: string) => Promise<{ success: boolean; path: string; message: string }>;
    exportRepo: (workspacePath: string, name: string, isPrivate: boolean, msg: string) => Promise<{ success: boolean; repo: unknown; message: string }>;
    commitAndPush: (workspacePath: string, message: string) => Promise<{ success: boolean; message: string; output: string }>;
    openRepo: (url: string) => Promise<void>;
  };
  providers?: {
    list: () => Promise<unknown[]>;
    connect: (config: unknown) => Promise<unknown>;
    disconnect: (id: string) => Promise<void>;
    authenticate: (config: unknown) => Promise<{ success: boolean; message: string }>;
    test: (id: string) => Promise<{ ok: boolean; message: string }>;
    getStatus: (id: string) => Promise<{ connected: boolean; message: string; accountLabel?: string }>;
    getAgentConfig: () => Promise<Record<string, unknown>>;
    getVault: (id: string) => Promise<Record<string, unknown>>;
    getAllVaults: () => Promise<Record<string, unknown>>;
    startProxy: (type: string, port: number) => Promise<{ running: boolean; url: string; lanUrl?: string; message: string }>;
    stopProxy: () => Promise<void>;
    getProxyStatus: () => Promise<{ running: boolean; port: number; url: string; lanUrl?: string; message: string; type?: string }>;
    startOceanProxy: (port?: number) => Promise<{ running: boolean; url: string; lanUrl: string; message: string }>;
    stopOceanProxy: () => Promise<{ running: boolean; message: string }>;
    getOceanProxyStatus: () => Promise<{ running: boolean; url: string; lanUrl: string; port: number; message: string }>;
  };
  gateway?: {
    getStatus: () => Promise<Record<string, unknown>>;
    getConfig: () => Promise<Record<string, unknown>>;
    saveConfig: (config: Record<string, unknown>) => Promise<void>;
    getCombos: () => Promise<unknown[]>;
    saveCombos: (combos: unknown[]) => Promise<void>;
    getWorkflows: () => Promise<unknown[]>;
    saveWorkflows: (workflows: unknown[]) => Promise<void>;
    createVirtualKey: (name: string, models?: string[]) => Promise<{ key: string; record: { id: string } }>;
    revokeVirtualKey: (id: string) => Promise<void>;
    getVirtualKeys: () => Promise<unknown[]>;
    getUsage: (limit?: number) => Promise<unknown[]>;
    getDecisions: (limit?: number) => Promise<unknown[]>;
    getIdeConfigs: (port?: number) => Promise<Record<string, unknown>>;
  };
  recording: {
    getHardwareInfo: () => Promise<Record<string, unknown>>;
    getSources: () => Promise<{ id: string; name: string; type: string; thumbnail?: string }[]>;
    start: (config: Record<string, unknown>, sourceId?: string) => Promise<{ ok: boolean; message: string }>;
    pause: () => Promise<{ ok: boolean; message: string }>;
    resume: () => Promise<{ ok: boolean; message: string }>;
    stop: () => Promise<{ ok: boolean; path: string; message: string }>;
    cancel: () => Promise<{ ok: boolean; message: string }>;
    getStatus: () => Promise<Record<string, unknown>>;
    onStatus: (callback: (status: unknown) => void) => () => void;
  };
}
