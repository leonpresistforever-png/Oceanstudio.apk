import { EventEmitter } from 'events';
import type { ProviderManager } from '../provider-manager.js';
import { GatewayStore } from './gateway-store.js';
import { GatewayEngine, generateIdeConfigs } from './gateway-engine.js';
import type { GatewayConfig, ModelCombo, StrategicWorkflow } from './types.js';

export class GatewayManager extends EventEmitter {
  private store: GatewayStore;
  private engine: GatewayEngine;

  constructor(
    userDataPath: string,
    private providerManager: ProviderManager
  ) {
    super();
    this.store = new GatewayStore(userDataPath);
    this.engine = new GatewayEngine({
      store: this.store,
      providerManager,
      getConnections: () => providerManager.list(),
    });
  }

  getEngine(): GatewayEngine {
    return this.engine;
  }

  getStore(): GatewayStore {
    return this.store;
  }

  async getStatus() {
    const stats = await this.store.getStats();
    const config = await this.store.getConfig();
    const combos = await this.store.getCombos();
    const workflows = await this.store.getWorkflows();
    const virtualKeys = await this.store.getVirtualKeys();
    return { stats, config, combos, workflows, virtualKeys };
  }

  async getConfig(): Promise<GatewayConfig> {
    return this.store.getConfig();
  }

  async saveConfig(config: GatewayConfig): Promise<void> {
    await this.store.saveConfig(config);
    this.emit('config', config);
  }

  async getCombos(): Promise<ModelCombo[]> {
    return this.store.getCombos();
  }

  async saveCombos(combos: ModelCombo[]): Promise<void> {
    await this.store.saveCombos(combos);
    this.emit('combos', combos);
  }

  async getWorkflows(): Promise<StrategicWorkflow[]> {
    return this.store.getWorkflows();
  }

  async saveWorkflows(workflows: StrategicWorkflow[]): Promise<void> {
    await this.store.saveWorkflows(workflows);
    this.emit('workflows', workflows);
  }

  async createVirtualKey(name: string, allowedModels?: string[]) {
    return this.store.createVirtualKey(name, allowedModels);
  }

  async revokeVirtualKey(id: string): Promise<void> {
    await this.store.revokeVirtualKey(id);
  }

  async getVirtualKeys() {
    return this.store.getVirtualKeys();
  }

  async getUsage(limit?: number) {
    return this.store.getUsage(limit);
  }

  async getDecisions(limit?: number) {
    return this.store.getDecisions(limit);
  }

  getIdeConfigs(port: number) {
    return generateIdeConfigs(port);
  }
}
