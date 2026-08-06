import { spawn, type ChildProcess } from 'child_process';
import { EventEmitter } from 'events';
import type { OceanProxyService } from './ocean-proxy.js';

export interface ProxyStatus {
  running: boolean;
  port: number;
  url: string;
  lanUrl?: string;
  pid?: number;
  type: 'ocean' | 'omniroute' | 'litellm' | 'custom' | 'none';
  message: string;
}

/** Local proxy — Ocean native (preferred) or OmniRoute/LiteLLM via npx */
export class ProxyServerService extends EventEmitter {
  private process: ChildProcess | null = null;
  private status: ProxyStatus = {
    running: false, port: 20128, url: 'http://localhost:20128', type: 'none', message: 'Proxy not running',
  };

  constructor(private oceanProxy?: OceanProxyService) {
    super();
  }

  getStatus(): ProxyStatus {
    if (this.oceanProxy?.getStatus().running) {
      const s = this.oceanProxy.getStatus();
      return {
        running: true,
        port: s.port,
        url: s.url,
        lanUrl: s.lanUrl,
        type: 'ocean',
        message: s.message,
      };
    }
    return { ...this.status };
  }

  async start(type: 'ocean' | 'omniroute' | 'litellm' | 'custom' = 'ocean', port = 20128, customCommand?: string): Promise<ProxyStatus> {
    // Ocean native proxy — OAuth callbacks + API gateway, works on LAN for mobile
    if (type === 'ocean' && this.oceanProxy) {
      const s = await this.oceanProxy.start({ port, bindHost: '0.0.0.0' });
      this.status = {
        running: true,
        port: s.port,
        url: s.url,
        lanUrl: s.lanUrl,
        type: 'ocean',
        message: s.message,
      };
      this.emit('ready', this.status);
      return this.getStatus();
    }

    if (this.process) await this.stop();

    const commands: Record<string, string> = {
      omniroute: `npx -y omniroute@latest --port ${port}`,
      litellm: `litellm --port ${port}`,
      custom: customCommand ?? `echo "Set custom proxy command"`,
    };

    const cmd = commands[type === 'ocean' ? 'omniroute' : type];
    const isWin = process.platform === 'win32';
    this.process = spawn(isWin ? 'cmd' : 'sh', isWin ? ['/c', cmd] : ['-c', cmd], {
      detached: false,
      stdio: 'pipe',
      env: { ...process.env, PORT: String(port) },
    });

    this.status = {
      running: true,
      port,
      url: `http://localhost:${port}`,
      pid: this.process.pid,
      type: type === 'ocean' ? 'omniroute' : type,
      message: `Starting ${type} proxy on port ${port}...`,
    };

    this.process.stdout?.on('data', (d: Buffer) => {
      const text = d.toString();
      if (text.includes('listening') || text.includes('ready') || text.includes(String(port))) {
        this.status.message = `${type} proxy ready at ${this.status.url}`;
        this.emit('ready', this.status);
      }
    });

    this.process.stderr?.on('data', (d: Buffer) => {
      this.status.message = d.toString().slice(0, 200);
    });

    this.process.on('exit', (code) => {
      this.status.running = false;
      this.status.message = `Proxy exited (code ${code})`;
      this.process = null;
      this.emit('exit', code);
    });

    setTimeout(() => {
      if (this.status.running) {
        this.status.message = `${type} proxy at ${this.status.url}`;
        this.emit('ready', this.status);
      }
    }, 5000);

    return this.getStatus();
  }

  async stop(): Promise<ProxyStatus> {
    if (this.oceanProxy?.getStatus().running) {
      await this.oceanProxy.stop();
    }
    if (this.process) {
      this.process.kill('SIGTERM');
      this.process = null;
    }
    this.status = {
      running: false, port: this.status.port, url: this.status.url,
      type: 'none', message: 'Proxy stopped',
    };
    this.emit('stopped');
    return this.getStatus();
  }
}
