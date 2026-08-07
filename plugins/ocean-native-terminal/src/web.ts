import { WebPlugin } from '@capacitor/core';
import type { OceanNativeTerminalPlugin, CreateOptions, WriteOptions, ResizeOptions, SessionOptions, SetupStatus } from './definitions';

export class OceanNativeTerminalWeb extends WebPlugin implements OceanNativeTerminalPlugin {
  async setup(): Promise<SetupStatus> {
    return {
      ready: false,
      prefix: '',
      shell: '',
      message: 'Native terminal requires the Ocean.studio Android APK',
    };
  }

  async getSetupStatus(): Promise<SetupStatus> {
    return this.setup();
  }

  async getCapabilities() {
    return {
      pty: false,
      fullBootstrap: false,
      ready: false,
      prefix: '',
      shell: '',
    };
  }

  async getHomePath(): Promise<{ path: string }> {
    return { path: '/workspace' };
  }

  async readDir(): Promise<{ items: never[] }> {
    return { items: [] };
  }

  async readFile(): Promise<{ content: string }> {
    return { content: '' };
  }

  async writeFile(): Promise<void> {}

  async create(_options: CreateOptions): Promise<void> {
    throw new Error('Native terminal is only available in the Android APK');
  }

  async write(_options: WriteOptions): Promise<void> {}
  async resize(_options: ResizeOptions): Promise<void> {}
  async kill(_options: SessionOptions): Promise<void> {}
  async restart(_options: SessionOptions): Promise<void> {}
  async clear(_options: SessionOptions): Promise<void> {}
}
