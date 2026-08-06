import path from 'path';
import fs from 'fs/promises';
import { existsSync } from 'fs';
import crypto from 'crypto';
import type {
  AutoComboDecision,
  GatewayConfig,
  ModelCombo,
  StrategicWorkflow,
  UsageRecord,
  VirtualKey,
  ConnectionHealth,
} from './types.js';
import { DEFAULT_GATEWAY_CONFIG } from './types.js';
import { DEFAULT_COMBOS } from './combo-router.js';

export class GatewayStore {
  private basePath: string;

  constructor(userDataPath: string) {
    this.basePath = path.join(userDataPath, 'gateway');
  }

  private async ensure(): Promise<void> {
    await fs.mkdir(this.basePath, { recursive: true });
  }

  private file(name: string): string {
    return path.join(this.basePath, name);
  }

  private async readJson<T>(name: string, fallback: T): Promise<T> {
    const f = this.file(name);
    if (!existsSync(f)) return fallback;
    try {
      return JSON.parse(await fs.readFile(f, 'utf-8')) as T;
    } catch {
      return fallback;
    }
  }

  private async writeJson(name: string, data: unknown): Promise<void> {
    await this.ensure();
    await fs.writeFile(this.file(name), JSON.stringify(data, null, 2));
  }

  async getConfig(): Promise<GatewayConfig> {
    return this.readJson('config.json', DEFAULT_GATEWAY_CONFIG);
  }

  async saveConfig(config: GatewayConfig): Promise<void> {
    await this.writeJson('config.json', config);
  }

  async getCombos(): Promise<ModelCombo[]> {
    const combos = await this.readJson<ModelCombo[]>('combos.json', []);
    return combos.length ? combos : DEFAULT_COMBOS;
  }

  async saveCombos(combos: ModelCombo[]): Promise<void> {
    await this.writeJson('combos.json', combos);
  }

  async getVirtualKeys(): Promise<VirtualKey[]> {
    return this.readJson('virtual-keys.json', []);
  }

  async createVirtualKey(name: string, allowedModels: string[] = ['*']): Promise<{ key: string; record: VirtualKey }> {
    const rawKey = `osk_${crypto.randomBytes(24).toString('hex')}`;
    const record: VirtualKey = {
      id: crypto.randomUUID(),
      name,
      keyHash: crypto.createHash('sha256').update(rawKey).digest('hex'),
      keyPrefix: rawKey.slice(0, 12),
      allowedModels,
      allowedCombos: ['*'],
      isActive: true,
      createdAt: Date.now(),
    };
    const keys = await this.getVirtualKeys();
    keys.push(record);
    await this.writeJson('virtual-keys.json', keys);
    return { key: rawKey, record };
  }

  async validateVirtualKey(rawKey: string): Promise<VirtualKey | null> {
    const hash = crypto.createHash('sha256').update(rawKey).digest('hex');
    const keys = await this.getVirtualKeys();
    return keys.find((k) => k.keyHash === hash && k.isActive) ?? null;
  }

  async revokeVirtualKey(id: string): Promise<void> {
    const keys = await this.getVirtualKeys();
    await this.writeJson('virtual-keys.json', keys.filter((k) => k.id !== id));
  }

  async recordUsage(record: UsageRecord): Promise<void> {
    const usage = await this.readJson<UsageRecord[]>('usage.json', []);
    usage.unshift(record);
    await this.writeJson('usage.json', usage.slice(0, 5000));
  }

  async getUsage(limit = 100): Promise<UsageRecord[]> {
    const usage = await this.readJson<UsageRecord[]>('usage.json', []);
    return usage.slice(0, limit);
  }

  async recordDecision(decision: AutoComboDecision): Promise<void> {
    const decisions = await this.readJson<AutoComboDecision[]>('decisions.json', []);
    decisions.unshift(decision);
    await this.writeJson('decisions.json', decisions.slice(0, 1000));
  }

  async getDecisions(limit = 50): Promise<AutoComboDecision[]> {
    const d = await this.readJson<AutoComboDecision[]>('decisions.json', []);
    return d.slice(0, limit);
  }

  async getWorkflows(): Promise<StrategicWorkflow[]> {
    return this.readJson('workflows.json', DEFAULT_WORKFLOWS);
  }

  async saveWorkflows(workflows: StrategicWorkflow[]): Promise<void> {
    await this.writeJson('workflows.json', workflows);
  }

  async getHealth(): Promise<ConnectionHealth[]> {
    return this.readJson('health.json', []);
  }

  async updateHealth(connectionId: string, patch: Partial<ConnectionHealth>): Promise<void> {
    const health = await this.getHealth();
    const idx = health.findIndex((h) => h.connectionId === connectionId);
    const existing = idx >= 0 ? health[idx] : {
      connectionId,
      providerId: connectionId,
      latencyMs: 500,
      successRate: 0.95,
      quotaHeadroom: 0.8,
      costPer1kTokens: 0.01,
      lastUsed: 0,
    };
    const merged = { ...existing, ...patch, lastUsed: Date.now() };
    if (idx >= 0) health[idx] = merged;
    else health.push(merged);
    await this.writeJson('health.json', health);
  }

  async getStats(): Promise<{
    totalRequests: number;
    successRate: number;
    avgLatencyMs: number;
    totalCostUsd: number;
  }> {
    const usage = await this.getUsage(5000);
    if (!usage.length) {
      return { totalRequests: 0, successRate: 1, avgLatencyMs: 0, totalCostUsd: 0 };
    }
    const successes = usage.filter((u) => u.success).length;
    return {
      totalRequests: usage.length,
      successRate: successes / usage.length,
      avgLatencyMs: usage.reduce((s, u) => s + u.latencyMs, 0) / usage.length,
      totalCostUsd: usage.reduce((s, u) => s + u.totalCostUsd, 0),
    };
  }
}

export const DEFAULT_WORKFLOWS: StrategicWorkflow[] = [
  {
    id: 'wf-ide-cursor',
    name: 'Cursor IDE Bridge',
    description: 'Normalize Cursor payloads, compress context, auto-route with fallback',
    triggers: ['ide', 'chat'],
    enabled: true,
    createdAt: Date.now(),
    steps: [
      { id: 's1', type: 'transform', config: { bridge: 'cursor' } },
      { id: 's2', type: 'compress', config: { caveman: true, rtk: true } },
      { id: 's3', type: 'route', config: { comboId: 'ocean-coding' } },
      { id: 's4', type: 'fallback', config: { maxAttempts: 3 } },
    ],
  },
  {
    id: 'wf-agent-heavy',
    name: 'Agent Long-Run',
    description: 'Anti-timeout routing with cost-aware fallback for multi-hour agent sessions',
    triggers: ['agent'],
    enabled: true,
    createdAt: Date.now(),
    steps: [
      { id: 's1', type: 'compress', config: { maxCharsPerMessage: 12000 } },
      { id: 's2', type: 'route', config: { comboId: 'ocean-smart' } },
      { id: 's3', type: 'fallback', config: { maxAttempts: 5 } },
    ],
  },
  {
    id: 'wf-playground-media',
    name: 'Playground Media Pipeline',
    description: 'Route image/video/3D playground jobs through gateway with webhook notify',
    triggers: ['playground'],
    enabled: true,
    createdAt: Date.now(),
    steps: [
      { id: 's1', type: 'route', config: { comboId: 'ocean-cost' } },
      { id: 's2', type: 'webhook', config: { event: 'playground.job.complete' } },
    ],
  },
];
