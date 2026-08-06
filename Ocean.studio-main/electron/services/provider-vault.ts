import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';

export interface ProviderVaultData {
  providerId: string;
  tokens?: Record<string, unknown>;
  config?: Record<string, string>;
  tools?: { name: string; description: string; parameters?: Record<string, unknown> }[];
  skills?: string;
  systemFiles?: Record<string, string>;
  syncedModels?: string[];
  accountLabel?: string;
  avatarUrl?: string;
  pipeline?: {
    requestFormat: string;
    responseFormat: string;
    chatEndpoint?: string;
    modelsEndpoint?: string;
  };
  updatedAt: string;
}

/** Per-provider isolated storage — tokens, tools, skills, system files */
export class ProviderVault {
  private basePath: string;

  constructor(userDataPath: string) {
    this.basePath = path.join(userDataPath, 'providers');
  }

  private providerDir(providerId: string): string {
    return path.join(this.basePath, providerId.replace(/\./g, '_'));
  }

  async ensureDir(providerId: string): Promise<string> {
    const dir = this.providerDir(providerId);
    await fs.mkdir(dir, { recursive: true });
    return dir;
  }

  async save(providerId: string, data: Partial<ProviderVaultData>): Promise<void> {
    const dir = await this.ensureDir(providerId);
    const existing = await this.load(providerId);

    const merged: ProviderVaultData = {
      ...existing,
      ...data,
      providerId,
      updatedAt: new Date().toISOString(),
    };

    if (data.tokens) {
      await fs.writeFile(path.join(dir, 'tokens.json'), JSON.stringify(data.tokens, null, 2), { mode: 0o600 });
    }
    if (data.config) {
      await fs.writeFile(path.join(dir, 'config.json'), JSON.stringify(data.config, null, 2));
    }
    if (data.tools) {
      await fs.writeFile(path.join(dir, 'tools.json'), JSON.stringify(data.tools, null, 2));
    }
    if (data.skills !== undefined) {
      await fs.writeFile(path.join(dir, 'skills.md'), data.skills);
    }
    if (data.systemFiles) {
      await fs.writeFile(path.join(dir, 'system-files.json'), JSON.stringify(data.systemFiles, null, 2));
    }
    if (data.syncedModels) {
      await fs.writeFile(path.join(dir, 'models.json'), JSON.stringify(data.syncedModels, null, 2));
    }

    await fs.writeFile(path.join(dir, 'manifest.json'), JSON.stringify(merged, null, 2));
  }

  async load(providerId: string): Promise<ProviderVaultData> {
    const dir = this.providerDir(providerId);
    const base: ProviderVaultData = { providerId, updatedAt: new Date().toISOString() };

    if (!existsSync(dir)) return base;

    try {
      if (existsSync(path.join(dir, 'manifest.json'))) {
        Object.assign(base, JSON.parse(await fs.readFile(path.join(dir, 'manifest.json'), 'utf-8')));
      }
      if (existsSync(path.join(dir, 'tokens.json'))) {
        base.tokens = JSON.parse(await fs.readFile(path.join(dir, 'tokens.json'), 'utf-8'));
      }
      if (existsSync(path.join(dir, 'config.json'))) {
        base.config = JSON.parse(await fs.readFile(path.join(dir, 'config.json'), 'utf-8'));
      }
      if (existsSync(path.join(dir, 'tools.json'))) {
        base.tools = JSON.parse(await fs.readFile(path.join(dir, 'tools.json'), 'utf-8'));
      }
      if (existsSync(path.join(dir, 'skills.md'))) {
        base.skills = await fs.readFile(path.join(dir, 'skills.md'), 'utf-8');
      }
      if (existsSync(path.join(dir, 'system-files.json'))) {
        base.systemFiles = JSON.parse(await fs.readFile(path.join(dir, 'system-files.json'), 'utf-8'));
      }
      if (existsSync(path.join(dir, 'models.json'))) {
        base.syncedModels = JSON.parse(await fs.readFile(path.join(dir, 'models.json'), 'utf-8'));
      }
    } catch { /* partial load ok */ }

    return base;
  }

  async remove(providerId: string): Promise<void> {
    const dir = this.providerDir(providerId);
    if (existsSync(dir)) {
      await fs.rm(dir, { recursive: true, force: true });
    }
  }

  async list(): Promise<string[]> {
    if (!existsSync(this.basePath)) return [];
    const entries = await fs.readdir(this.basePath, { withFileTypes: true });
    return entries.filter((e) => e.isDirectory()).map((e) => e.name.replace(/_/g, '.'));
  }

  async getAgentBundle(providerId: string): Promise<Record<string, unknown>> {
    const vault = await this.load(providerId);
    return {
      providerId,
      models: vault.syncedModels ?? [],
      tools: vault.tools ?? [],
      skills: vault.skills ?? '',
      systemFiles: vault.systemFiles ?? {},
      pipeline: vault.pipeline,
      accountLabel: vault.accountLabel,
    };
  }

  async getAllConnectedBundles(): Promise<Record<string, unknown>> {
    const ids = await this.list();
    const bundles: Record<string, unknown> = {};
    for (const id of ids) {
      bundles[id] = await this.getAgentBundle(id);
    }
    return bundles;
  }
}
