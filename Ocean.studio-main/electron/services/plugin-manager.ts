import { EventEmitter } from 'events';
import { spawn, type ChildProcess } from 'child_process';
import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import type { InstalledPlugin, OceanPluginManifest } from '../types/plugins.js';

interface RunningMcp {
  process: ChildProcess;
  manifest: OceanPluginManifest;
}

export class PluginManager extends EventEmitter {
  private plugins: Map<string, InstalledPlugin> = new Map();
  private running: Map<string, RunningMcp> = new Map();
  private storagePath: string;

  constructor(userDataPath: string) {
    super();
    this.storagePath = path.join(userDataPath, 'plugins.json');
  }

  async load(): Promise<void> {
    try {
      if (existsSync(this.storagePath)) {
        const raw = await fs.readFile(this.storagePath, 'utf-8');
        const list = JSON.parse(raw) as InstalledPlugin[];
        for (const p of list) this.plugins.set(p.manifest.id, p);
      }
    } catch { /* fresh install */ }
  }

  private async save(): Promise<void> {
    await fs.writeFile(this.storagePath, JSON.stringify([...this.plugins.values()], null, 2));
  }

  list(): InstalledPlugin[] {
    return [...this.plugins.values()];
  }

  async install(manifest: OceanPluginManifest, config?: Record<string, unknown>): Promise<InstalledPlugin> {
    const entry: InstalledPlugin = {
      manifest,
      enabled: true,
      installedAt: Date.now(),
      config: config ?? manifest.config ?? {},
      status: 'idle',
      localPath: path.join(this.storagePath, '..', 'plugin-data', manifest.id),
    };
    this.plugins.set(manifest.id, entry);
    await fs.mkdir(entry.localPath!, { recursive: true });
    await this.save();
    this.emit('installed', entry);
    return entry;
  }

  async uninstall(id: string): Promise<void> {
    await this.stop(id);
    this.plugins.delete(id);
    await this.save();
    this.emit('uninstalled', id);
  }

  async start(id: string): Promise<void> {
    const plugin = this.plugins.get(id);
    if (!plugin?.enabled) throw new Error(`Plugin not found or disabled: ${id}`);
    if (this.running.has(id)) return;

    const mcp = plugin.manifest.mcp;
    if (!mcp?.command) {
      plugin.status = 'running';
      await this.save();
      return;
    }

    const env = { ...process.env, ...mcp.env, ...plugin.config } as NodeJS.ProcessEnv;
    const args = [...(mcp.args ?? [])];
    const child = spawn(mcp.command, args, {
      cwd: mcp.cwd ?? plugin.localPath,
      env,
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: process.platform === 'win32',
    });

    child.stdout?.on('data', (d: Buffer) => {
      this.emit('plugin:output', id, d.toString());
    });
    child.stderr?.on('data', (d: Buffer) => {
      this.emit('plugin:error', id, d.toString());
    });
    child.on('exit', (code) => {
      this.running.delete(id);
      plugin.status = code === 0 ? 'idle' : 'error';
      plugin.error = code !== 0 ? `Exited with code ${code}` : undefined;
      this.save();
      this.emit('plugin:exit', id, code);
    });

    this.running.set(id, { process: child, manifest: plugin.manifest });
    plugin.status = 'running';
    await this.save();
    this.emit('plugin:started', id);
  }

  async stop(id: string): Promise<void> {
    const running = this.running.get(id);
    if (running) {
      running.process.kill();
      this.running.delete(id);
    }
    const plugin = this.plugins.get(id);
    if (plugin) {
      plugin.status = 'idle';
      await this.save();
    }
  }

  async setEnabled(id: string, enabled: boolean): Promise<void> {
    const plugin = this.plugins.get(id);
    if (!plugin) return;
    plugin.enabled = enabled;
    if (!enabled) await this.stop(id);
    await this.save();
  }

  async updateConfig(id: string, config: Record<string, unknown>): Promise<void> {
    const plugin = this.plugins.get(id);
    if (!plugin) return;
    plugin.config = config;
    await this.save();
    if (plugin.status === 'running') {
      await this.stop(id);
      await this.start(id);
    }
  }

  getToolsForAgent(): { pluginId: string; name: string; description: string }[] {
    const tools: { pluginId: string; name: string; description: string }[] = [];
    for (const p of this.plugins.values()) {
      if (!p.enabled) continue;
      for (const t of p.manifest.tools ?? []) {
        tools.push({ pluginId: p.manifest.id, name: t.name, description: t.description });
      }
    }
    return tools;
  }

  disposeAll(): void {
    for (const id of this.running.keys()) {
      this.running.get(id)?.process.kill();
    }
    this.running.clear();
  }
}
