import { app, BrowserWindow, ipcMain, shell, dialog } from 'electron';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import { TerminalManager } from './services/terminal.js';
import { FileSystemService } from './services/filesystem.js';
import { PortPreviewService } from './services/port-preview.js';
import { AgentService } from './services/agent.js';
import { CloudShellService } from './services/cloud-shell.js';
import { PluginManager } from './services/plugin-manager.js';
import { McpManager } from './services/mcp-manager.js';
import { GitHubService } from './services/github.js';
import { ProviderManager } from './services/provider-manager.js';
import { ProxyServerService } from './services/proxy-server.js';
import { OceanProxyService } from './services/ocean-proxy.js';
import { ProviderVault } from './services/provider-vault.js';
import { RecordingService } from './services/recording.js';
import { GatewayManager } from './services/gateway/gateway-manager.js';
import { resolvePreferredTerminal } from './services/terminal-router.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const isDev = !app.isPackaged;

let mainWindow: BrowserWindow | null = null;
let cloudShellService: CloudShellService;
let terminalManager: TerminalManager;
let fileSystem: FileSystemService;
let portPreview: PortPreviewService;
let agentService: AgentService;
let pluginManager: PluginManager;
let mcpManager: McpManager;
let githubService: GitHubService;
let providerManager: ProviderManager;
let proxyServer: ProxyServerService;
let oceanProxy: OceanProxyService;
let providerVault: ProviderVault;
let recordingService: RecordingService;
let gatewayManager: GatewayManager;

function initServices() {
  const userData = app.getPath('userData');
  cloudShellService = new CloudShellService(userData);
  terminalManager = new TerminalManager(cloudShellService);
  fileSystem = new FileSystemService();
  portPreview = new PortPreviewService();
  pluginManager = new PluginManager(userData);
  mcpManager = new McpManager(userData);
  githubService = new GitHubService(userData);
  oceanProxy = new OceanProxyService();
  providerVault = new ProviderVault(userData);
  providerManager = new ProviderManager(userData, {
    github: {
      authenticate: async () => {
        const s = await githubService.authenticate();
        return { username: s.username, avatarUrl: s.avatarUrl };
      },
    },
    cloudshell: {
      authenticate: async () => {
        const s = await cloudShellService.authenticateWithBrowser();
        return { email: s.email };
      },
    },
  }, oceanProxy, providerVault);
  proxyServer = new ProxyServerService(oceanProxy);
  gatewayManager = new GatewayManager(userData, providerManager);
  oceanProxy.setGatewayEngine(gatewayManager.getEngine());
  agentService = new AgentService(terminalManager, fileSystem, portPreview, mcpManager, githubService);
  recordingService = new RecordingService();
  recordingService.on('status', (status) => {
    mainWindow?.webContents.send('recording:status', status);
  });
  void pluginManager.load();
  void mcpManager.load();
  void providerManager.load();
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    backgroundColor: '#FAFAF9',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  if (isDev) {
    mainWindow.loadURL('http://localhost:5173');
    mainWindow.webContents.openDevTools({ mode: 'detach' });
  } else {
    mainWindow.loadFile(path.join(__dirname, '../dist/index.html'));
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// Forward terminal output to renderer — wired after services init
function wireServiceEvents() {
  terminalManager.on('data', (id: string, data: string) => {
    mainWindow?.webContents.send('terminal:data', id, data);
  });

  terminalManager.on('exit', (id: string, code: number) => {
    mainWindow?.webContents.send('terminal:exit', id, code);
  });

  terminalManager.on('portDetected', async (port: number, url: string, terminalType?: string) => {
    if (terminalType === 'cloud') {
      try {
        const preview = await cloudShellService.registerPort(port);
        portPreview.registerCloudShellPort(port, preview.url, preview.openUrl);
        mainWindow?.webContents.send('preview:portDetected', port, preview.url, {
          source: 'cloudshell',
          openUrl: preview.openUrl,
          label: 'Cloud Shell',
        });
        return;
      } catch {
        /* fall through to local registration */
      }
    }
    portPreview.registerPort(port, 'terminal');
    mainWindow?.webContents.send('preview:portDetected', port, url, { source: 'local' });
  });

  cloudShellService.on('portRegistered', (preview: { port: number; url: string; openUrl: string }) => {
    portPreview.registerCloudShellPort(preview.port, preview.url, preview.openUrl);
    mainWindow?.webContents.send('preview:portDetected', preview.port, preview.url, {
      source: 'cloudshell',
      openUrl: preview.openUrl,
      label: 'Cloud Shell',
    });
  });

  agentService.on('event', (event: unknown) => {
    mainWindow?.webContents.send('agent:event', event);
  });

  portPreview.on('portDetected', (port: number, url: string, entry?: { source?: string; openUrl?: string }) => {
    mainWindow?.webContents.send('preview:portDetected', port, url, entry ?? {});
  });
}

app.whenReady().then(() => {
  // Single-instance lock for OAuth deep-link callbacks (Windows/Linux)
  const gotLock = app.requestSingleInstanceLock();
  if (!gotLock) {
    app.quit();
    return;
  }

  app.on('second-instance', (_event, argv) => {
    const url = argv.find((a) => a.startsWith('ocean://'));
    if (url) {
      mainWindow?.webContents.send('oauth:deep-link', url);
    }
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });

  if (process.defaultApp) {
    if (process.argv.length >= 2) {
      app.setAsDefaultProtocolClient('ocean', process.execPath, [path.resolve(process.argv[1])]);
    }
  } else {
    app.setAsDefaultProtocolClient('ocean');
  }

  app.on('open-url', (event, url) => {
    event.preventDefault();
    mainWindow?.webContents.send('oauth:deep-link', url);
  });

  initServices();
  wireServiceEvents();
  createWindow();
  registerIpcHandlers();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  terminalManager.disposeAll();
  pluginManager.disposeAll();
  mcpManager.disposeAll();
  githubService.dispose();
  void oceanProxy?.stop();
  void proxyServer?.stop();
  if (process.platform !== 'darwin') app.quit();
});

function registerIpcHandlers() {
  // Platform detection
  ipcMain.handle('platform:get', () => {
    const ctx = {
      platform: process.platform,
      isElectron: true,
      isDesktop: true,
      isMobile: false,
      isWeb: false,
      hasNativeShell: true,
      shellLabel: process.platform === 'win32' ? 'PowerShell' : process.platform === 'darwin' ? 'Zsh' : 'Bash',
    };
    agentService.setPlatformContext(ctx);
    return {
      ...ctx,
      preferredTerminal: resolvePreferredTerminal(ctx),
    };
  });

  ipcMain.handle('platform:getPreferredTerminal', () => {
    const ctx = {
      platform: process.platform,
      isElectron: true,
      isDesktop: true,
      isMobile: false,
      isWeb: false,
      hasNativeShell: true,
    };
    return resolvePreferredTerminal(ctx);
  });

  // File system
  ipcMain.handle('fs:readDir', (_, dirPath: string) => fileSystem.readDirectory(dirPath));
  ipcMain.handle('fs:readFile', (_, filePath: string) => fileSystem.readFile(filePath));
  ipcMain.handle('fs:writeFile', (_, filePath: string, content: string) => fileSystem.writeFile(filePath, content));
  ipcMain.handle('fs:selectWorkspace', async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ['openDirectory'],
      title: 'Select Workspace Folder',
    });
    if (result.canceled || !result.filePaths[0]) return null;
    return result.filePaths[0];
  });
  ipcMain.handle('fs:getDefaultWorkspace', () => {
    const workspace = path.join(app.getPath('userData'), 'workspaces', 'default');
    if (!existsSync(workspace)) {
      fs.mkdir(workspace, { recursive: true });
    }
    return workspace;
  });

  // Terminal
  ipcMain.handle('terminal:create', (_, options: { id: string; type: 'shell' | 'cloud' | 'native'; cwd?: string }) =>
    terminalManager.create(options)
  );
  ipcMain.handle('terminal:write', (_, id: string, data: string) => terminalManager.write(id, data));
  ipcMain.handle('terminal:resize', (_, id: string, cols: number, rows: number) =>
    terminalManager.resize(id, cols, rows)
  );
  ipcMain.handle('terminal:kill', (_, id: string) => terminalManager.kill(id));
  ipcMain.handle('terminal:restart', (_, id: string) => terminalManager.restart(id));
  ipcMain.handle('terminal:clear', (_, id: string) => terminalManager.clear(id));

  // Port preview
  ipcMain.handle('preview:getPorts', () => portPreview.getActivePorts());
  ipcMain.handle('preview:registerPort', (_, port: number, label?: string) =>
    portPreview.registerPort(port, label)
  );
  ipcMain.handle('preview:registerCloudShellPort', async (_, port: number) => {
    const preview = await cloudShellService.registerPort(port);
    portPreview.registerCloudShellPort(port, preview.url, preview.openUrl);
    return preview;
  });
  ipcMain.handle('preview:unregisterPort', (_, port: number) => portPreview.unregisterPort(port));

  // File upload
  ipcMain.handle('upload:selectFiles', async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ['openFile', 'multiSelections'],
    });
    if (result.canceled) return [];
    return Promise.all(
      result.filePaths.map(async (filePath) => {
        const content = await fs.readFile(filePath);
        const name = path.basename(filePath);
        return { name, path: filePath, content: content.toString('base64'), mimeType: getMimeType(name) };
      })
    );
  });

  ipcMain.handle('upload:selectImages', async () => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: 'Images', extensions: ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg'] }],
    });
    if (result.canceled) return [];
    return Promise.all(
      result.filePaths.map(async (filePath) => {
        const content = await fs.readFile(filePath);
        const name = path.basename(filePath);
        return { name, path: filePath, content: content.toString('base64'), mimeType: getMimeType(name) };
      })
    );
  });

  // Agent
  ipcMain.handle('agent:send', (_, message: string, context: unknown) =>
    agentService.sendMessage(message, context)
  );
  ipcMain.handle('agent:pause', () => agentService.pause());
  ipcMain.handle('agent:getStatus', () => agentService.getStatus());

  // External links
  ipcMain.handle('shell:openExternal', (_, url: string) => shell.openExternal(url));

  // Cloud Shell
  ipcMain.handle('cloudshell:getStatus', () => cloudShellService.getStatus());
  ipcMain.handle('cloudshell:authenticate', () => cloudShellService.authenticateWithBrowser());
  ipcMain.handle('cloudshell:activate', () => cloudShellService.activate());
  ipcMain.handle('cloudshell:disconnect', () => cloudShellService.disconnect());
  ipcMain.handle('cloudshell:registerPort', async (_, port: number) => cloudShellService.registerPort(port));
  ipcMain.handle('cloudshell:getPortPreviews', () => cloudShellService.getAuthorizedPorts());

  // Plugins
  ipcMain.handle('plugins:list', () => pluginManager.list());
  ipcMain.handle('plugins:install', (_, manifest: unknown) => pluginManager.install(manifest as Parameters<PluginManager['install']>[0]));
  ipcMain.handle('plugins:uninstall', (_, id: string) => pluginManager.uninstall(id));
  ipcMain.handle('plugins:start', (_, id: string) => pluginManager.start(id));
  ipcMain.handle('plugins:stop', (_, id: string) => pluginManager.stop(id));
  ipcMain.handle('plugins:setEnabled', (_, id: string, enabled: boolean) => pluginManager.setEnabled(id, enabled));
  ipcMain.handle('plugins:updateConfig', (_, id: string, config: Record<string, unknown>) => pluginManager.updateConfig(id, config));
  ipcMain.handle('plugins:getTools', () => pluginManager.getToolsForAgent());

  // MCP Connectors
  ipcMain.handle('mcp:list', () => mcpManager.list());
  ipcMain.handle('mcp:connect', (_, config) => mcpManager.connect(config));
  ipcMain.handle('mcp:disconnect', (_, id: string) => mcpManager.disconnect(id));
  ipcMain.handle('mcp:authenticate', (_, config, clientId?: string, clientSecret?: string) =>
    mcpManager.authenticate(config, clientId, clientSecret)
  );
  ipcMain.handle('mcp:test', (_, id: string) => mcpManager.test(id));
  ipcMain.handle('mcp:getAgentConfig', () => mcpManager.getAgentConfig());

  // GitHub workflow
  ipcMain.handle('github:getStatus', () => githubService.getStatus());
  ipcMain.handle('github:authenticate', () => githubService.authenticate());
  ipcMain.handle('github:disconnect', () => githubService.disconnect());
  ipcMain.handle('github:listRepos', () => githubService.listRepos());
  ipcMain.handle('github:getRepo', (_, owner: string, repo: string) => githubService.getRepo(owner, repo));
  ipcMain.handle('github:importRepo', (_, url: string, dir: string) => githubService.importRepo(url, dir));
  ipcMain.handle('github:exportRepo', (_, workspacePath: string, name: string, isPrivate: boolean, msg: string) =>
    githubService.exportRepo(workspacePath, name, isPrivate, msg)
  );
  ipcMain.handle('github:commitAndPush', (_, workspacePath: string, message: string) =>
    githubService.commitAndPush(workspacePath, message)
  );
  ipcMain.handle('github:openRepo', (_, url: string) => { githubService.openRepo(url); });

  // Providers (OmniRouter-style)
  ipcMain.handle('providers:list', () => providerManager.list());
  ipcMain.handle('providers:connect', (_, cfg) => providerManager.connect(cfg));
  ipcMain.handle('providers:disconnect', (_, id: string) => providerManager.disconnect(id));
  ipcMain.handle('providers:authenticate', (_, cfg) => providerManager.authenticate(cfg));
  ipcMain.handle('providers:test', (_, id: string) => providerManager.test(id));
  ipcMain.handle('providers:getStatus', (_, id: string) => providerManager.getStatus(id));
  ipcMain.handle('providers:getAgentConfig', () => providerManager.getAgentConfig());
  ipcMain.handle('providers:getVault', (_, id: string) => providerManager.getVaultBundle(id));
  ipcMain.handle('providers:getAllVaults', () => providerVault.getAllConnectedBundles());
  ipcMain.handle('providers:startProxy', (_, type: string, port: number) =>
    proxyServer.start(type as 'ocean' | 'omniroute' | 'litellm', port)
  );
  ipcMain.handle('providers:stopProxy', () => proxyServer.stop());
  ipcMain.handle('providers:getProxyStatus', () => proxyServer.getStatus());
  ipcMain.handle('providers:startOceanProxy', (_, port?: number) => oceanProxy.start({ port: port ?? 20128 }));
  ipcMain.handle('providers:stopOceanProxy', () => oceanProxy.stop());
  ipcMain.handle('providers:getOceanProxyStatus', () => oceanProxy.getStatus());

  // Ocean Gateway Engine
  ipcMain.handle('gateway:getStatus', () => gatewayManager.getStatus());
  ipcMain.handle('gateway:getConfig', () => gatewayManager.getConfig());
  ipcMain.handle('gateway:saveConfig', (_, config) => gatewayManager.saveConfig(config));
  ipcMain.handle('gateway:getCombos', () => gatewayManager.getCombos());
  ipcMain.handle('gateway:saveCombos', (_, combos) => gatewayManager.saveCombos(combos));
  ipcMain.handle('gateway:getWorkflows', () => gatewayManager.getWorkflows());
  ipcMain.handle('gateway:saveWorkflows', (_, workflows) => gatewayManager.saveWorkflows(workflows));
  ipcMain.handle('gateway:createVirtualKey', (_, name: string, models?: string[]) =>
    gatewayManager.createVirtualKey(name, models)
  );
  ipcMain.handle('gateway:revokeVirtualKey', (_, id: string) => gatewayManager.revokeVirtualKey(id));
  ipcMain.handle('gateway:getVirtualKeys', () => gatewayManager.getVirtualKeys());
  ipcMain.handle('gateway:getUsage', (_, limit?: number) => gatewayManager.getUsage(limit));
  ipcMain.handle('gateway:getDecisions', (_, limit?: number) => gatewayManager.getDecisions(limit));
  ipcMain.handle('gateway:getIdeConfigs', (_, port?: number) =>
    gatewayManager.getIdeConfigs(port ?? oceanProxy.getStatus().port)
  );

  ipcMain.handle('recording:getHardwareInfo', () => recordingService.getHardwareInfo());
  ipcMain.handle('recording:getSources', () => recordingService.getSources());
  ipcMain.handle('recording:start', (_, config, sourceId?: string) => recordingService.start(config, sourceId));
  ipcMain.handle('recording:pause', () => recordingService.pause());
  ipcMain.handle('recording:resume', () => recordingService.resume());
  ipcMain.handle('recording:stop', () => recordingService.stop());
  ipcMain.handle('recording:cancel', () => recordingService.cancel());
  ipcMain.handle('recording:getStatus', () => recordingService.getStatus());
}

function getMimeType(filename: string): string {
  const ext = path.extname(filename).toLowerCase();
  const map: Record<string, string> = {
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.gif': 'image/gif',
    '.webp': 'image/webp',
    '.svg': 'image/svg+xml',
    '.pdf': 'application/pdf',
    '.txt': 'text/plain',
    '.json': 'application/json',
    '.js': 'text/javascript',
    '.ts': 'text/typescript',
    '.py': 'text/x-python',
  };
  return map[ext] || 'application/octet-stream';
}
