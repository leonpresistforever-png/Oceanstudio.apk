import { EventEmitter } from 'events';

export type PreviewPortSource = 'local' | 'cloudshell';

export interface ActivePort {
  port: number;
  url: string;
  label?: string;
  source: PreviewPortSource;
  openUrl?: string;
  detectedAt: number;
}

export class PortPreviewService extends EventEmitter {
  private ports = new Map<number, ActivePort>();

  registerPort(port: number, label?: string, opts?: { url?: string; source?: PreviewPortSource; openUrl?: string }) {
    const source = opts?.source ?? 'local';
    const url = opts?.url ?? (source === 'local' ? `http://localhost:${port}` : opts?.url ?? `http://localhost:${port}`);
    const entry: ActivePort = {
      port,
      url,
      label: label ?? source,
      source,
      openUrl: opts?.openUrl ?? url,
      detectedAt: Date.now(),
    };
    this.ports.set(port, entry);
    this.emit('portDetected', port, url, entry);
  }

  registerCloudShellPort(port: number, url: string, openUrl: string, label = 'Cloud Shell') {
    this.registerPort(port, label, { url, source: 'cloudshell', openUrl });
  }

  unregisterPort(port: number) {
    this.ports.delete(port);
  }

  getActivePorts(): ActivePort[] {
    return Array.from(this.ports.values()).sort((a, b) => b.detectedAt - a.detectedAt);
  }
}
