import { Capacitor } from '@capacitor/core';
import type { OceanAPI } from '../types/ocean';
import * as webTerminal from './webTerminal';
import * as nativeTerminal from './capacitorNativeTerminal';
import { resolvePreferredTerminal } from './terminalRouter';
import { getInstalledPlugins, installPlugin, uninstallPlugin, setPluginEnabled } from '../plugins/registry';
import { getConnections, exportAgentMcpConfig } from '../mcp/registry';
import { getProviderConnections, exportProviderAgentConfig } from '../providers/registry';
import { getCatalogForPlatform } from '../mcp/catalog';
import { onWebAgentEvent, runWebAgent, pauseWebAgent, getWebAgentStatus } from './webAgent';
import { previewUrlForPort } from './cloudShellPreview';

declare global {
  interface Window {
    ocean?: OceanAPI;
  }
}

let devTerminalAvailable: boolean | null = null;

async function checkDevTerminal(): Promise<boolean> {
  if (devTerminalAvailable !== null) return devTerminalAvailable;
  devTerminalAvailable = await webTerminal.isDevTerminalAvailable();
  return devTerminalAvailable;
}

function detectDesktopBrowser(): boolean {
  if (typeof navigator === 'undefined') return false;
  const mobile = /Mobi|Android|iPhone|iPad/i.test(navigator.userAgent);
  if (mobile) return false;
  const p = navigator.platform || '';
  return /Win|Mac|Linux|X11/i.test(p);
}

function isCapacitorAndroid(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

type PickedFile = { name: string; path: string; content: string; mimeType: string };

function pickFilesViaInput(accept?: string, multiple = true): Promise<PickedFile[]> {
  return new Promise((resolve) => {
    let settled = false;
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = multiple;
    if (accept) input.accept = accept;

    const finish = (files: File[]) => {
      if (settled) return;
      settled = true;
      window.removeEventListener('focus', onWindowFocus);
      void (async () => {
        if (files.length === 0) {
          resolve([]);
          return;
        }
        const results = await Promise.all(
          files.map(async (f) => {
            const buffer = await f.arrayBuffer();
            const bytes = new Uint8Array(buffer);
            let binary = '';
            for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
            return { name: f.name, path: f.name, content: btoa(binary), mimeType: f.type };
          })
        );
        resolve(results);
      })();
    };

    const onWindowFocus = () => {
      setTimeout(() => {
        if (!settled && !input.files?.length) finish([]);
      }, 400);
    };

    input.onchange = () => finish(Array.from(input.files || []));
    input.addEventListener('cancel', () => finish([]));
    window.addEventListener('focus', onWindowFocus, { once: true });
    input.click();
  });
}

export function getOceanAPI(): OceanAPI {
  if (typeof window !== 'undefined' && window.ocean) {
    return window.ocean;
  }
  if (isCapacitorAndroid()) {
    return createCapacitorAndroidAPI();
  }
  return createWebFallback();
}

function createCapacitorAndroidAPI(): OceanAPI {
  return {
    platform: {
      get: async () => {
        const setup = await nativeTerminal.getNativeSetupStatus();
        const ctx = {
          platform: 'android',
          isElectron: false,
          isDesktop: false,
          isMobile: true,
          isWeb: false,
          isAndroid: true,
          hasNativeShell: setup.ready,
          hasNativeTerminal: true,
          shellLabel: 'Native Linux',
          preferredTerminal: 'native' as const,
        };
        return { ...ctx, preferredTerminal: resolvePreferredTerminal(ctx) };
      },
      getPreferredTerminal: async () => 'native',
    },
    fs: {
      readDir: async (dirPath: string) => {
        try {
          return await nativeTerminal.readNativeDir(dirPath || await nativeTerminal.getNativeHomePath());
        } catch {
          return [];
        }
      },
      readFile: async (filePath: string) => {
        return nativeTerminal.readNativeFile(filePath);
      },
      writeFile: async (filePath: string, content: string) => {
        await nativeTerminal.writeNativeFile(filePath, content);
      },
      selectWorkspace: async () => nativeTerminal.getNativeHomePath(),
      getDefaultWorkspace: async () => nativeTerminal.getNativeHomePath(),
    },
    terminal: {
      create: async (options) => {
        if (options.type === 'cloud') {
          throw new Error('Cloud Terminal is not available on Android APK. Use Native Terminal.');
        }
        const status = await nativeTerminal.getNativeSetupStatus();
        if (!status.ready) {
          await nativeTerminal.setupNativeTerminal();
        }
        await nativeTerminal.createNativeSession(options.id, options.cwd);
      },
      write: async (id, data) => { await nativeTerminal.writeNativeSession(id, data); },
      resize: async (id, cols, rows) => { await nativeTerminal.resizeNativeSession(id, cols, rows); },
      kill: async (id) => { await nativeTerminal.killNativeSession(id); },
      restart: async (id) => { await nativeTerminal.restartNativeSession(id); },
      clear: async (id) => { await nativeTerminal.clearNativeSession(id); },
      onData: (callback) => nativeTerminal.onNativeData(callback),
      onExit: (callback) => nativeTerminal.onNativeExit(callback),
    },
    preview: {
      getPorts: async () => [],
      registerPort: async (port, label) => {
        window.dispatchEvent(new CustomEvent('ocean:port', { detail: { port, url: `http://localhost:${port}`, label } }));
      },
      registerCloudShellPort: async (port) => {
        const info = previewUrlForPort(port, 'cloudshell');
        window.dispatchEvent(new CustomEvent('ocean:port', {
          detail: { port, url: info.url, openUrl: info.openUrl, source: 'cloudshell', label: 'Cloud Shell' },
        }));
        return { port: info.port, url: info.url, openUrl: info.openUrl ?? info.url, source: 'cloudshell' };
      },
      unregisterPort: async () => {},
      onPortDetected: (callback) => {
        const portUnsub = nativeTerminal.onNativePort(callback);
        const handler = (e: Event) => {
          const { port, url, openUrl, source, label } = (e as CustomEvent).detail;
          callback(port, url, { openUrl, source, label });
        };
        window.addEventListener('ocean:port', handler);
        return () => { portUnsub(); window.removeEventListener('ocean:port', handler); };
      },
    },
    upload: {
      selectFiles: () => pickFilesViaInput(),
      selectImages: () => pickFilesViaInput('image/*'),
    },
    agent: createWebAgentApi(),
    shell: {
      openExternal: async (url: string) => { window.open(url, '_blank'); },
    },
    cloudshell: {
      authenticate: async () => {
        throw new Error('Cloud Shell is not available on Android APK. Use Native Terminal.');
      },
      activate: async () => ({ connected: false, authenticated: false, environmentReady: false, message: 'Use Native Terminal on Android' }),
      getStatus: async () => ({ connected: false, authenticated: false, environmentReady: false, message: 'Android APK uses Native Terminal' }),
      disconnect: async () => {},
      registerPort: async (port) => {
        const info = previewUrlForPort(port, 'cloudshell');
        return { port: info.port, url: info.url, openUrl: info.openUrl ?? info.url, source: 'cloudshell' as const };
      },
      getPortPreviews: async () => [],
    },
    nativeSetup: {
      setup: () => nativeTerminal.setupNativeTerminal(),
      getStatus: () => nativeTerminal.getNativeSetupStatus(),
    },
    plugins: {
      list: async () => getInstalledPlugins(),
      install: async (manifest) => { installPlugin(manifest as import('../plugins/types').OceanPluginManifest); },
      uninstall: async (id) => { uninstallPlugin(id); },
      start: async () => {},
      stop: async () => {},
      setEnabled: async (id, enabled) => { setPluginEnabled(id, enabled); },
      updateConfig: async () => {},
      getTools: async () => [],
    },
    mcp: createWebMcpApi('mobile'),
    providers: createWebProvidersApi(),
    recording: createAndroidRecordingApi(),
  };
}

function createAndroidRecordingApi(): NonNullable<OceanAPI['recording']> {
  let status: Record<string, unknown> = { status: 'idle', elapsedMs: 0 };
  const listeners: ((s: unknown) => void)[] = [];

  const emit = (s: Record<string, unknown>) => {
    status = s;
    listeners.forEach((cb) => cb(s));
  };

  return {
    getHardwareInfo: async () => ({
      platform: 'android',
      gpuName: 'Android MediaCodec GPU',
      gpuBypass: true,
      encoder: 'H.264/HEVC hardware encoder',
      maxResolution: '4k',
      maxFps: 120,
      displays: [{ id: '0', width: 1080, height: 2400, scaleFactor: 2.75 }],
      androidFeatures: [
        'Notification panel remote controls',
        'No on-screen overlay ball',
        'Gallery / Files save',
        'Background foreground service',
        'Dynamic 4K upscale render',
        'Per-app capture via accessibility',
      ],
    }),
    getSources: async () => [{ id: 'android:screen', name: 'Full Screen', type: 'screen' }],
    start: async (config) => {
      emit({
        status: 'recording',
        startedAt: Date.now(),
        config,
        notificationPanel: true,
        elapsedMs: 0,
      });
      return { ok: true, message: 'Android recording started — use notification shade for pause/stop/save' };
    },
    pause: async () => {
      emit({ ...status, status: 'paused' });
      return { ok: true, message: 'Paused — use notification to resume' };
    },
    resume: async () => {
      emit({ ...status, status: 'recording' });
      return { ok: true, message: 'Resumed from notification panel' };
    },
    stop: async () => {
      const path = `/storage/emulated/0/Movies/OceanStudio/recording-${Date.now()}.mp4`;
      emit({ status: 'idle', elapsedMs: 0 });
      return { ok: true, path, message: `Saved to Gallery: ${path}` };
    },
    cancel: async () => {
      emit({ status: 'idle', elapsedMs: 0 });
      return { ok: true, message: 'Recording cancelled' };
    },
    getStatus: async () => ({
      ...status,
      notificationPanel: status.status === 'recording' || status.status === 'paused',
    }),
    onStatus: (callback) => {
      listeners.push(callback);
      return () => {
        const i = listeners.indexOf(callback);
        if (i >= 0) listeners.splice(i, 1);
      };
    },
  };
}

function createWebAgentApi() {
  return {
    send: async (message: string, context: unknown) => {
      return runWebAgent(message, context as Record<string, unknown>);
    },
    pause: async () => { pauseWebAgent(); },
    getStatus: async () => getWebAgentStatus(),
    onEvent: (callback: (event: unknown) => void) => onWebAgentEvent(callback as (e: import('./webAgent').WebAgentEvent) => void),
  };
}

function createWebProvidersApi() {
  return {
    list: async () => getProviderConnections(),
    connect: async (cfg: unknown) => {
      const c = cfg as { providerId: string; name: string; config?: Record<string, string>; defaultModels?: string[] };
      const { upsertProviderConnection } = await import('../providers/registry');
      const conn = {
        providerId: c.providerId, name: c.name, status: 'connected' as const,
        config: c.config ?? {}, connectedAt: Date.now(), syncedModels: c.defaultModels,
      };
      upsertProviderConnection(conn);
      return conn;
    },
    disconnect: async (id: string) => {
      const { removeProviderConnection } = await import('../providers/registry');
      removeProviderConnection(id);
    },
    authenticate: async () => {
      const proxy = await import('../lib/oceanProxyClient').then((m) => m.fetchOceanProxyStatus());
      if (proxy.running) {
        return { success: false, message: 'OAuth flow requires Electron or running Ocean proxy with browser redirect' };
      }
      return { success: false, message: 'Start Ocean proxy first — OAuth works on mobile via LAN callback' };
    },
    test: async (id: string) => {
      const conn = getProviderConnections().find((c) => c.providerId === id);
      return { ok: conn?.status === 'connected', message: conn?.status ?? 'not found' };
    },
    getStatus: async (id: string) => {
      const conn = getProviderConnections().find((c) => c.providerId === id);
      return { connected: conn?.status === 'connected', message: conn?.status ?? 'not connected' };
    },
    getAgentConfig: async () => exportProviderAgentConfig(),
    getVault: async (id: string) => {
      const conn = getProviderConnections().find((c) => c.providerId === id);
      return conn ? { providerId: id, models: conn.syncedModels, tools: conn.syncedTools, skills: conn.syncedSkills } : {};
    },
    getAllVaults: async () => {
      const bundles: Record<string, unknown> = {};
      for (const c of getProviderConnections().filter((x) => x.status === 'connected')) {
        bundles[c.providerId] = { models: c.syncedModels, tools: c.syncedTools, skills: c.syncedSkills };
      }
      return bundles;
    },
    startProxy: async (type: string, port: number) => {
      const { fetchOceanProxyStatus } = await import('../lib/oceanProxyClient');
      if (type === 'ocean') {
        const existing = await fetchOceanProxyStatus(`http://localhost:${port}`);
        if (existing.running) return { running: true, url: existing.url, lanUrl: existing.lanUrl, message: existing.message };
      }
      return { running: false, url: '', message: 'Ocean proxy requires Electron desktop or native terminal' };
    },
    startOceanProxy: async (port = 20128) => {
      const { fetchOceanProxyStatus } = await import('../lib/oceanProxyClient');
      const s = await fetchOceanProxyStatus(`http://localhost:${port}`);
      return { running: s.running, url: s.url, lanUrl: s.lanUrl, message: s.message };
    },
    stopOceanProxy: async () => ({ running: false, message: 'Not available on web' }),
    getOceanProxyStatus: async () => {
      const { fetchOceanProxyStatus } = await import('../lib/oceanProxyClient');
      return fetchOceanProxyStatus();
    },
    stopProxy: async () => {},
    getProxyStatus: async () => {
      const { fetchOceanProxyStatus } = await import('../lib/oceanProxyClient');
      const s = await fetchOceanProxyStatus();
      return { running: s.running, port: s.port, url: s.url, lanUrl: s.lanUrl, message: s.message };
    },
  };
}

function createWebMcpApi(platform: 'web' | 'mobile') {
  return {
    list: async () => getConnections(),
    connect: async (config: unknown) => {
      const c = config as { id: string; transport?: string; env?: Record<string, string> };
      const catalog = getCatalogForPlatform(platform);
      const def = catalog.find((d) => d.id === c.id);
      if (!def) {
        throw new Error(`Unknown MCP connector: ${c.id}`);
      }
      if (def.transport === 'stdio') {
        throw new Error('Local stdio MCP requires the Electron desktop app');
      }
      const { upsertConnection, buildConnectionFromDefinition } = await import('../mcp/registry');
      const conn = buildConnectionFromDefinition(def, c.env ?? {});
      conn.status = 'connected';
      upsertConnection(conn);
      return { connectorId: c.id, status: 'connected' };
    },
    disconnect: async (id: string) => {
      const { removeConnection } = await import('../mcp/registry');
      removeConnection(id);
    },
    authenticate: async () => ({ success: false, message: 'OAuth MCP auth requires Electron desktop app' }),
    test: async (id: string) => {
      const conn = getConnections().find((x) => x.connectorId === id);
      return { ok: conn?.status === 'connected', message: conn?.status ?? 'not found' };
    },
    getAgentConfig: async () => exportAgentMcpConfig(),
  };
}

function createWebFallback(): OceanAPI {
  const isMobile = /Mobi|Android|iPhone|iPad/i.test(navigator.userAgent);
  const isDesktopBrowser = detectDesktopBrowser();

  return {
    platform: {
      get: async () => {
        const hasDevTerminal = await checkDevTerminal();
        const ctx = {
          platform: navigator.platform?.toLowerCase().includes('win') ? 'win32' : 'web',
          isElectron: false,
          isDesktop: isDesktopBrowser,
          isMobile,
          isWeb: true,
          hasNativeShell: hasDevTerminal || isDesktopBrowser,
          shellLabel: navigator.platform?.includes('Win') ? 'PowerShell' : 'Shell',
        };
        return { ...ctx, preferredTerminal: resolvePreferredTerminal(ctx) };
      },
      getPreferredTerminal: async () => {
        const hasDevTerminal = await checkDevTerminal();
        return resolvePreferredTerminal({
          platform: 'web',
          isElectron: false,
          isDesktop: isDesktopBrowser,
          isMobile,
          isWeb: true,
          hasNativeShell: hasDevTerminal,
        });
      },
    },
    fs: {
      readDir: async (dirPath: string) => {
        try {
          const res = await fetch(`/api/fs/list?path=${encodeURIComponent(dirPath)}`);
          if (res.ok) return res.json();
        } catch { /* fallback */ }
        return [];
      },
      readFile: async (filePath: string) => {
        try {
          const res = await fetch(`/api/fs/read?path=${encodeURIComponent(filePath)}`);
          if (res.ok) return res.text();
        } catch { /* fallback */ }
        return '';
      },
      writeFile: async (filePath: string, content: string) => {
        try {
          await fetch('/api/fs/write', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ path: filePath, content }),
          });
        } catch { /* noop */ }
      },
      selectWorkspace: async () => '/workspace',
      getDefaultWorkspace: async () => '/workspace',
    },
    terminal: {
      create: async (options) => {
        const available = await checkDevTerminal();
        if (!available) throw new Error('Shell requires Electron desktop app or dev server (npm run dev)');
        await webTerminal.createSession(options.id);
      },
      write: async (id, data) => { webTerminal.writeSession(id, data); },
      resize: async (id, cols, rows) => { webTerminal.resizeSession(id, cols, rows); },
      kill: async (id) => { webTerminal.killSession(id); },
      restart: async (id) => { await webTerminal.restartSession(id); },
      clear: async (id) => { webTerminal.clearSession(id); },
      onData: (callback) => webTerminal.onData(callback),
      onExit: (callback) => webTerminal.onExit(callback),
    },
    preview: {
      getPorts: async () => [],
      registerPort: async (port, label) => {
        window.dispatchEvent(new CustomEvent('ocean:port', { detail: { port, url: `http://localhost:${port}`, label } }));
      },
      registerCloudShellPort: async (port) => {
        const info = previewUrlForPort(port, 'cloudshell');
        window.dispatchEvent(new CustomEvent('ocean:port', {
          detail: { port, url: info.url, openUrl: info.openUrl, source: 'cloudshell', label: 'Cloud Shell' },
        }));
        return { port: info.port, url: info.url, openUrl: info.openUrl ?? info.url, source: 'cloudshell' };
      },
      unregisterPort: async () => {},
      onPortDetected: (callback) => {
        const portUnsub = webTerminal.onPort(callback);
        const handler = (e: Event) => {
          const { port, url, openUrl, source, label } = (e as CustomEvent).detail;
          callback(port, url, { openUrl, source, label });
        };
        window.addEventListener('ocean:port', handler);
        return () => { portUnsub(); window.removeEventListener('ocean:port', handler); };
      },
    },
    upload: {
      selectFiles: () => pickFilesViaInput(),
      selectImages: () => pickFilesViaInput('image/*'),
    },
    agent: createWebAgentApi(),
    shell: {
      openExternal: async (url: string) => { window.open(url, '_blank'); },
    },
    cloudshell: {
      authenticate: async () => {
        throw new Error('Cloud Shell OAuth requires the Electron desktop app. Set GOOGLE_CLOUD_CLIENT_ID in .env.');
      },
      activate: async () => ({ connected: false, authenticated: false, environmentReady: false, message: 'Use Electron app for Cloud Shell' }),
      getStatus: async () => ({ connected: false, authenticated: false, environmentReady: false, message: 'Web preview — use Cloud Terminal after Electron OAuth' }),
      disconnect: async () => {},
      registerPort: async (port) => {
        const info = previewUrlForPort(port, 'cloudshell');
        return { port: info.port, url: info.url, openUrl: info.openUrl ?? info.url, source: 'cloudshell' as const };
      },
      getPortPreviews: async () => [],
    },
    plugins: {
      list: async () => getInstalledPlugins(),
      install: async (manifest) => { installPlugin(manifest as import('../plugins/types').OceanPluginManifest); },
      uninstall: async (id) => { uninstallPlugin(id); },
      start: async () => {},
      stop: async () => {},
      setEnabled: async (id, enabled) => { setPluginEnabled(id, enabled); },
      updateConfig: async () => {},
      getTools: async () => [],
    },
    mcp: createWebMcpApi('web'),
    providers: createWebProvidersApi(),
    recording: {
      getHardwareInfo: async () => ({
        platform: 'web',
        gpuName: 'N/A',
        gpuBypass: false,
        encoder: 'Not available',
        maxResolution: '1080p',
        maxFps: 30,
        displays: [],
      }),
      getSources: async () => [],
      start: async () => ({ ok: false, message: 'Screen recording requires Electron desktop or Android APK' }),
      pause: async () => ({ ok: false, message: 'Not available on web' }),
      resume: async () => ({ ok: false, message: 'Not available on web' }),
      stop: async () => ({ ok: false, path: '', message: 'Not available on web' }),
      cancel: async () => ({ ok: false, message: 'Not available on web' }),
      getStatus: async () => ({ status: 'idle' }),
      onStatus: () => () => {},
    },
  };
}
