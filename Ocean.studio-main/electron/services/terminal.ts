import { execSync } from 'child_process';
import { EventEmitter } from 'events';
import * as pty from 'node-pty';
import os from 'os';
import { existsSync } from 'fs';
import type { CloudShellService } from './cloud-shell.js';

export type TerminalType = 'shell' | 'cloud' | 'native';

interface TerminalSession {
  pty: pty.IPty;
  type: TerminalType;
  cwd: string;
}

export class TerminalManager extends EventEmitter {
  private sessions = new Map<string, TerminalSession>();
  private agentTerminalId = 'ocean-agent-terminal';

  constructor(private cloudShell?: CloudShellService) {
    super();
  }

  getAgentTerminalId() {
    return this.agentTerminalId;
  }

  create(options: { id: string; type: TerminalType; cwd?: string }) {
    const { id, type, cwd } = options;
    if (this.sessions.has(id)) this.kill(id);

    const { shell, args, cwd: workingDir } = this.resolveShell(type, cwd);

    const ptyProcess = pty.spawn(shell, args, {
      name: 'xterm-256color',
      cols: 120,
      rows: 30,
      cwd: workingDir,
      env: {
        ...process.env,
        TERM: 'xterm-256color',
        OCEAN_TERMINAL_TYPE: type,
      } as Record<string, string>,
    });

    ptyProcess.onData((data) => {
      this.emit('data', id, data);
      const session = this.sessions.get(id);
      this.detectPortFromOutput(data, session?.type ?? 'shell', id);
      if (session?.type === 'cloud' && this.cloudShell) {
        this.cloudShell.ingestTerminalOutput(data);
      }
    });

    ptyProcess.onExit(({ exitCode }) => {
      this.emit('exit', id, exitCode);
      this.sessions.delete(id);
    });

    this.sessions.set(id, { pty: ptyProcess, type, cwd: workingDir });
  }

  /**
   * Ensures the agent has an active terminal of the preferred type.
   */
  ensureAgentTerminal(type: TerminalType, cwd?: string) {
    if (!this.sessions.has(this.agentTerminalId)) {
      this.create({ id: this.agentTerminalId, type, cwd });
    }
    return this.agentTerminalId;
  }

  /**
   * Run a command in the agent terminal and return when prompt returns (best-effort).
   */
  runAgentCommand(command: string, type: TerminalType, cwd?: string): string {
    const id = this.ensureAgentTerminal(type, cwd);
    const trimmed = command.trim();
    if (!trimmed.endsWith('\n')) {
      this.write(id, trimmed + '\n');
    } else {
      this.write(id, trimmed);
    }
    return id;
  }

  private resolveShell(type: TerminalType, cwd?: string): { shell: string; args: string[]; cwd: string } {
    const workingDir = cwd || os.homedir();

    if (type === 'cloud') {
      // Use gcloud cloud-shell ssh when available; falls back to bash with cloud marker
      if (commandExists('gcloud')) {
        return {
          shell: 'gcloud',
          args: ['cloud-shell', 'ssh'],
          cwd: workingDir,
        };
      }
      // Cloud shell via SSH will be connected after OAuth activation
      return {
        shell: os.platform() === 'win32' ? 'powershell.exe' : process.env.SHELL || '/bin/bash',
        args: os.platform() === 'win32' ? ['-NoLogo'] : [],
        cwd: workingDir,
      };
    }

    if (type === 'native') {
      // Mobile/APK: proot-distro or termux-compatible prefix when available
      const prootBash = '/data/data/com.termux/files/usr/bin/bash';
      if (existsSync(prootBash)) {
        return { shell: prootBash, args: [], cwd: workingDir };
      }
      // Desktop dev fallback for native type testing
      return this.resolveLocalShell(workingDir);
    }

    return this.resolveLocalShell(workingDir);
  }

  private resolveLocalShell(workingDir: string): { shell: string; args: string[]; cwd: string } {
    // Prefer WSL on Windows if user has Linux tooling needs
    if (os.platform() === 'win32') {
      if (commandExists('wsl.exe')) {
        return { shell: 'wsl.exe', args: ['-e', 'bash', '-l'], cwd: workingDir };
      }
      return { shell: 'powershell.exe', args: ['-NoLogo'], cwd: workingDir };
    }
    return {
      shell: process.env.SHELL || '/bin/bash',
      args: [],
      cwd: workingDir,
    };
  }

  write(id: string, data: string) {
    this.sessions.get(id)?.pty.write(data);
  }

  resize(id: string, cols: number, rows: number) {
    this.sessions.get(id)?.pty.resize(cols, rows);
  }

  kill(id: string) {
    const session = this.sessions.get(id);
    if (session) {
      session.pty.kill();
      this.sessions.delete(id);
    }
  }

  restart(id: string) {
    const session = this.sessions.get(id);
    if (session) {
      const { type, cwd } = session;
      this.kill(id);
      this.create({ id, type, cwd });
    }
  }

  clear(id: string) {
    this.write(id, os.platform() === 'win32' ? 'cls\r' : 'clear\r');
  }

  disposeAll() {
    for (const id of this.sessions.keys()) this.kill(id);
  }

  getSessionType(id: string): TerminalType | null {
    return this.sessions.get(id)?.type ?? null;
  }

  private detectPortFromOutput(data: string, terminalType: TerminalType, terminalId: string) {
    const patterns = [
      /localhost:(\d{4,5})/gi,
      /127\.0\.0\.1:(\d{4,5})/gi,
      /port\s+(\d{4,5})/gi,
      /listening.*?(\d{4,5})/gi,
      /http:\/\/localhost:(\d{4,5})/gi,
      /0\.0\.0\.0:(\d{4,5})/gi,
      /https:\/\/(\d{4,5})-[a-z0-9.-]+\.cloudshell\.dev/gi,
    ];
    for (const pattern of patterns) {
      let match;
      while ((match = pattern.exec(data)) !== null) {
        const port = parseInt(match[1], 10);
        if (port > 1024 && port < 65536) {
          this.emit('portDetected', port, `http://localhost:${port}`, terminalType, terminalId);
        }
      }
    }
  }
}

function commandExists(cmd: string): boolean {
  const which = os.platform() === 'win32' ? 'where' : 'which';
  try {
    execSync(`${which} ${cmd}`, { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}
