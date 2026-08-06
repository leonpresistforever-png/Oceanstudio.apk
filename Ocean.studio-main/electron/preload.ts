import { contextBridge, ipcRenderer } from 'electron';
import type { OceanAPI } from './types/ocean.js';

const oceanAPI: OceanAPI = {
  platform: {
    get: () => ipcRenderer.invoke('platform:get'),
    getPreferredTerminal: () => ipcRenderer.invoke('platform:getPreferredTerminal'),
  },
  fs: {
    readDir: (path) => ipcRenderer.invoke('fs:readDir', path),
    readFile: (path) => ipcRenderer.invoke('fs:readFile', path),
    writeFile: (path, content) => ipcRenderer.invoke('fs:writeFile', path, content),
    selectWorkspace: () => ipcRenderer.invoke('fs:selectWorkspace'),
    getDefaultWorkspace: () => ipcRenderer.invoke('fs:getDefaultWorkspace'),
  },
  terminal: {
    create: (options) => ipcRenderer.invoke('terminal:create', options),
    write: (id, data) => ipcRenderer.invoke('terminal:write', id, data),
    resize: (id, cols, rows) => ipcRenderer.invoke('terminal:resize', id, cols, rows),
    kill: (id) => ipcRenderer.invoke('terminal:kill', id),
    restart: (id) => ipcRenderer.invoke('terminal:restart', id),
    clear: (id) => ipcRenderer.invoke('terminal:clear', id),
    onData: (callback) => {
      const handler = (_: unknown, id: string, data: string) => callback(id, data);
      ipcRenderer.on('terminal:data', handler);
      return () => ipcRenderer.removeListener('terminal:data', handler);
    },
    onExit: (callback) => {
      const handler = (_: unknown, id: string, code: number) => callback(id, code);
      ipcRenderer.on('terminal:exit', handler);
      return () => ipcRenderer.removeListener('terminal:exit', handler);
    },
  },
  preview: {
    getPorts: () => ipcRenderer.invoke('preview:getPorts'),
    registerPort: (port, label) => ipcRenderer.invoke('preview:registerPort', port, label),
    registerCloudShellPort: (port) => ipcRenderer.invoke('preview:registerCloudShellPort', port),
    unregisterPort: (port) => ipcRenderer.invoke('preview:unregisterPort', port),
    onPortDetected: (callback) => {
      const handler = (_: unknown, port: number, url: string, meta?: { source?: string; openUrl?: string }) =>
        callback(port, url, meta);
      ipcRenderer.on('preview:portDetected', handler);
      return () => ipcRenderer.removeListener('preview:portDetected', handler);
    },
  },
  upload: {
    selectFiles: () => ipcRenderer.invoke('upload:selectFiles'),
    selectImages: () => ipcRenderer.invoke('upload:selectImages'),
  },
  agent: {
    send: (message, context) => ipcRenderer.invoke('agent:send', message, context),
    pause: () => ipcRenderer.invoke('agent:pause'),
    getStatus: () => ipcRenderer.invoke('agent:getStatus'),
    onEvent: (callback) => {
      const handler = (_: unknown, event: unknown) => callback(event);
      ipcRenderer.on('agent:event', handler);
      return () => ipcRenderer.removeListener('agent:event', handler);
    },
  },
  shell: {
    openExternal: (url) => ipcRenderer.invoke('shell:openExternal', url),
  },
  cloudshell: {
    authenticate: () => ipcRenderer.invoke('cloudshell:authenticate'),
    activate: () => ipcRenderer.invoke('cloudshell:activate'),
    getStatus: () => ipcRenderer.invoke('cloudshell:getStatus'),
    disconnect: () => ipcRenderer.invoke('cloudshell:disconnect'),
    registerPort: (port: number) => ipcRenderer.invoke('cloudshell:registerPort', port),
    getPortPreviews: () => ipcRenderer.invoke('cloudshell:getPortPreviews'),
  },
  plugins: {
    list: () => ipcRenderer.invoke('plugins:list'),
    install: (manifest) => ipcRenderer.invoke('plugins:install', manifest),
    uninstall: (id) => ipcRenderer.invoke('plugins:uninstall', id),
    start: (id) => ipcRenderer.invoke('plugins:start', id),
    stop: (id) => ipcRenderer.invoke('plugins:stop', id),
    setEnabled: (id, enabled) => ipcRenderer.invoke('plugins:setEnabled', id, enabled),
    updateConfig: (id, config) => ipcRenderer.invoke('plugins:updateConfig', id, config),
    getTools: () => ipcRenderer.invoke('plugins:getTools'),
  },
  mcp: {
    list: () => ipcRenderer.invoke('mcp:list'),
    connect: (config) => ipcRenderer.invoke('mcp:connect', config),
    disconnect: (id) => ipcRenderer.invoke('mcp:disconnect', id),
    authenticate: (config, clientId, clientSecret) => ipcRenderer.invoke('mcp:authenticate', config, clientId, clientSecret),
    test: (id) => ipcRenderer.invoke('mcp:test', id),
    getAgentConfig: () => ipcRenderer.invoke('mcp:getAgentConfig'),
  },
  github: {
    getStatus: () => ipcRenderer.invoke('github:getStatus'),
    authenticate: () => ipcRenderer.invoke('github:authenticate'),
    disconnect: () => ipcRenderer.invoke('github:disconnect'),
    listRepos: () => ipcRenderer.invoke('github:listRepos'),
    getRepo: (owner, repo) => ipcRenderer.invoke('github:getRepo', owner, repo),
    importRepo: (url, dir) => ipcRenderer.invoke('github:importRepo', url, dir),
    exportRepo: (workspacePath, name, isPrivate, msg) => ipcRenderer.invoke('github:exportRepo', workspacePath, name, isPrivate, msg),
    commitAndPush: (workspacePath, message) => ipcRenderer.invoke('github:commitAndPush', workspacePath, message),
    openRepo: (url) => ipcRenderer.invoke('github:openRepo', url),
  },
  providers: {
    list: () => ipcRenderer.invoke('providers:list'),
    connect: (config) => ipcRenderer.invoke('providers:connect', config),
    disconnect: (id) => ipcRenderer.invoke('providers:disconnect', id),
    authenticate: (config) => ipcRenderer.invoke('providers:authenticate', config),
    test: (id) => ipcRenderer.invoke('providers:test', id),
    getStatus: (id) => ipcRenderer.invoke('providers:getStatus', id),
    getAgentConfig: () => ipcRenderer.invoke('providers:getAgentConfig'),
    getVault: (id: string) => ipcRenderer.invoke('providers:getVault', id),
    getAllVaults: () => ipcRenderer.invoke('providers:getAllVaults'),
    startProxy: (type, port) => ipcRenderer.invoke('providers:startProxy', type, port),
    stopProxy: () => ipcRenderer.invoke('providers:stopProxy'),
    getProxyStatus: () => ipcRenderer.invoke('providers:getProxyStatus'),
    startOceanProxy: (port?: number) => ipcRenderer.invoke('providers:startOceanProxy', port),
    stopOceanProxy: () => ipcRenderer.invoke('providers:stopOceanProxy'),
    getOceanProxyStatus: () => ipcRenderer.invoke('providers:getOceanProxyStatus'),
  },
  gateway: {
    getStatus: () => ipcRenderer.invoke('gateway:getStatus'),
    getConfig: () => ipcRenderer.invoke('gateway:getConfig'),
    saveConfig: (config: Record<string, unknown>) => ipcRenderer.invoke('gateway:saveConfig', config),
    getCombos: () => ipcRenderer.invoke('gateway:getCombos'),
    saveCombos: (combos: unknown[]) => ipcRenderer.invoke('gateway:saveCombos', combos),
    getWorkflows: () => ipcRenderer.invoke('gateway:getWorkflows'),
    saveWorkflows: (workflows: unknown[]) => ipcRenderer.invoke('gateway:saveWorkflows', workflows),
    createVirtualKey: (name: string, models?: string[]) => ipcRenderer.invoke('gateway:createVirtualKey', name, models),
    revokeVirtualKey: (id: string) => ipcRenderer.invoke('gateway:revokeVirtualKey', id),
    getVirtualKeys: () => ipcRenderer.invoke('gateway:getVirtualKeys'),
    getUsage: (limit?: number) => ipcRenderer.invoke('gateway:getUsage', limit),
    getDecisions: (limit?: number) => ipcRenderer.invoke('gateway:getDecisions', limit),
    getIdeConfigs: (port?: number) => ipcRenderer.invoke('gateway:getIdeConfigs', port),
  },
  recording: {
    getHardwareInfo: () => ipcRenderer.invoke('recording:getHardwareInfo'),
    getSources: () => ipcRenderer.invoke('recording:getSources'),
    start: (config, sourceId?: string) => ipcRenderer.invoke('recording:start', config, sourceId),
    pause: () => ipcRenderer.invoke('recording:pause'),
    resume: () => ipcRenderer.invoke('recording:resume'),
    stop: () => ipcRenderer.invoke('recording:stop'),
    cancel: () => ipcRenderer.invoke('recording:cancel'),
    getStatus: () => ipcRenderer.invoke('recording:getStatus'),
    onStatus: (callback) => {
      const handler = (_: unknown, status: unknown) => callback(status);
      ipcRenderer.on('recording:status', handler);
      return () => ipcRenderer.removeListener('recording:status', handler);
    },
  },
};

contextBridge.exposeInMainWorld('ocean', oceanAPI);
