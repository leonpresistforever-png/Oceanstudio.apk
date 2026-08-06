import { EventEmitter } from 'events';
import { spawn, ChildProcess } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';
import type { TerminalManager } from './terminal.js';
import type { TerminalType } from './terminal.js';
import type { FileSystemService } from './filesystem.js';
import type { PortPreviewService } from './port-preview.js';
import type { McpManager } from './mcp-manager.js';
import type { GitHubService } from './github.js';
import { resolvePreferredTerminal, type PlatformContext } from './terminal-router.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export type AgentMode = 'review' | 'auto' | 'bypass';

export interface AgentEvent {
  type: 'thought' | 'action' | 'command' | 'todo' | 'result' | 'error' | 'status';
  id: string;
  timestamp: number;
  summary: string;
  detail?: string;
  duration?: number;
  status?: 'running' | 'completed' | 'failed' | 'paused';
  metadata?: Record<string, unknown>;
}

export class AgentService extends EventEmitter {
  private status: 'idle' | 'running' | 'paused' = 'idle';
  private currentProcess: ChildProcess | null = null;
  private mode: AgentMode = 'review';
  private processingQueue = false;
  private messageQueue: { message: string; context: unknown; resolve: (text: string) => void; reject: (e: Error) => void }[] = [];
  private platformContext: PlatformContext = {
    platform: process.platform,
    isElectron: true,
    isDesktop: true,
    isMobile: false,
    isWeb: false,
    hasNativeShell: true,
    shellLabel: process.platform === 'win32' ? 'PowerShell' : 'Bash',
  };

  constructor(
    private terminal: TerminalManager,
    private fs: FileSystemService,
    private preview: PortPreviewService,
    private mcp?: McpManager,
    private github?: GitHubService
  ) {
    super();
  }

  setPlatformContext(ctx: PlatformContext) {
    this.platformContext = ctx;
  }

  setMode(mode: AgentMode) {
    this.mode = mode;
  }

  getStatus() {
    return this.status;
  }

  getPreferredTerminalType(): TerminalType {
    return resolvePreferredTerminal(this.platformContext);
  }

  pause() {
    if (this.currentProcess) {
      this.currentProcess.kill('SIGTERM');
      this.currentProcess = null;
    }
    this.messageQueue = [];
    this.processingQueue = false;
    this.status = 'paused';
    this.emitEvent({
      type: 'status',
      id: crypto.randomUUID(),
      timestamp: Date.now(),
      summary: 'Agent session paused',
      status: 'paused',
    });
  }

  async sendMessage(message: string, context: unknown): Promise<string> {
    return new Promise((resolve, reject) => {
      this.messageQueue.push({ message, context, resolve, reject });
      void this.processQueue();
    });
  }

  private async processQueue(): Promise<void> {
    if (this.processingQueue) return;
    this.processingQueue = true;

    while (this.messageQueue.length > 0) {
      const item = this.messageQueue.shift()!;
      try {
        const response = await this.executeSend(item.message, item.context);
        item.resolve(response);
      } catch (err) {
        item.reject(err instanceof Error ? err : new Error('Agent send failed'));
      }
    }

    this.processingQueue = false;
  }

  private async executeSend(message: string, context: unknown): Promise<string> {
    if (this.status === 'running') {
      // Wait briefly for prior send to finish (queue should prevent this)
      await this.delay(100);
    }
    this.status = 'running';

    const ctx = context as Record<string, unknown>;
    const phase = ctx?._multiAgentPhase as string | undefined;
    const isSubAgentPass = phase === 'parallel-worker' || phase === 'collaborative';
    const memberCtx = ctx?.multiAgentMember as { memberName?: string; role?: string } | undefined;

    const agentMode = (ctx?.agentMode as AgentMode) ?? this.mode;
    this.mode = agentMode;

    const terminalType = (ctx?.terminalType as TerminalType) || this.getPreferredTerminalType();
    const workspacePath = ctx?.workspacePath as string | undefined;
    const mcpFromCtx = ctx?.mcpServers as Record<string, unknown> | undefined;
    const mcpConfig = mcpFromCtx && Object.keys(mcpFromCtx).length > 0
      ? { mcpServers: mcpFromCtx, connectedCount: (ctx?.mcpMeta as { connectedCount?: number })?.connectedCount }
      : this.mcp ? await this.mcp.getAgentConfig() : {};
    const ghStatus = this.github?.getStatus();

    const thoughtId = crypto.randomUUID();
    const startTime = Date.now();

    if (!isSubAgentPass) {
      this.emitEvent({
        type: 'thought',
        id: thoughtId,
        timestamp: startTime,
        summary: 'Planning next moves',
        status: 'running',
      });
      await this.delay(600);
    } else {
      await this.delay(150);
    }

    this.emitEvent({
      type: 'thought',
      id: thoughtId,
      timestamp: Date.now(),
      summary: isSubAgentPass
        ? `${memberCtx?.memberName ?? 'Sub-agent'} (${phase})`
        : `Thought for ${Math.max(1, Math.round((Date.now() - startTime) / 1000))}s`,
      detail: this.generateThoughtDetail(message, ctx, terminalType),
      duration: Date.now() - startTime,
      status: 'completed',
      metadata: isSubAgentPass ? { memberId: memberCtx, phase } : undefined,
    });

    const actionId = crypto.randomUUID();
    this.emitEvent({
      type: 'action',
      id: actionId,
      timestamp: Date.now(),
      summary: isSubAgentPass
        ? `${memberCtx?.memberName ?? 'Agent'}: ${message.slice(0, 60)}...`
        : `Processing: ${message.slice(0, 80)}${message.length > 80 ? '...' : ''}`,
      status: 'running',
      metadata: { terminalType, phase },
    });

    const modelConfig = ctx?.modelConfig as { agentTimeoutSec?: number; antiTimeout?: boolean } | undefined;
    const antiTimeout = modelConfig?.antiTimeout ?? false;
    const timeoutSec = antiTimeout ? 0 : (modelConfig?.agentTimeoutSec ?? 120);

    try {
      if (!isSubAgentPass) {
        const agentTermId = this.terminal.ensureAgentTerminal(terminalType, workspacePath);
        this.emitEvent({
          type: 'command',
          id: crypto.randomUUID(),
          timestamp: Date.now(),
          summary: `Agent terminal ready (${terminalType})`,
          detail: `Session: ${agentTermId}\nPlatform: ${this.platformContext.platform}`,
          status: 'completed',
        });
      }

      await this.runAgentScript(message, {
        ...ctx,
        terminalType,
        agentTerminalId: 'ocean-agent-terminal',
        mcpServers: mcpConfig,
        github: ghStatus,
      }, { timeoutSec, antiTimeout });

      const responseText = this.lastScriptResponse || message.slice(0, 500);

      // Sub-agent passes don't auto-run shell commands — only head/user messages do
      if (!isSubAgentPass && this.looksLikeCommand(message)) {
        const safe = this.checkCommandSafety(message);
        const agentMode = (ctx?.agentMode as AgentMode) ?? this.mode;
        const effectiveMode = agentMode === 'bypass' ? 'bypass' : agentMode;

        if (safe.requiresApproval && effectiveMode === 'review') {
          this.emitEvent({
            type: 'command',
            id: crypto.randomUUID(),
            timestamp: Date.now(),
            summary: `Awaiting approval: ${message.slice(0, 60)}`,
            detail: safe.reason,
            status: 'running',
            metadata: { requiresApproval: true, command: message },
          });
        } else {
          this.terminal.runAgentCommand(message, terminalType, workspacePath);
          this.emitEvent({
            type: 'command',
            id: crypto.randomUUID(),
            timestamp: Date.now(),
            summary: `Ran command in ${terminalType} terminal`,
            detail: `$ ${message}`,
            status: 'completed',
          });
        }
      }

      this.emitEvent({
        type: 'result',
        id: actionId,
        timestamp: Date.now(),
        summary: responseText.slice(0, 120) + (responseText.length > 120 ? '...' : ''),
        detail: responseText,
        status: 'completed',
        metadata: { responseText },
      });

      return responseText;
    } catch (err) {
      this.emitEvent({
        type: 'error',
        id: actionId,
        timestamp: Date.now(),
        summary: `Error: ${err instanceof Error ? err.message : 'Unknown error'}`,
        status: 'failed',
      });
      throw err instanceof Error ? err : new Error('Agent execution failed');
    } finally {
      this.status = 'idle';
    }
  }

  private lastScriptResponse = '';

  private looksLikeCommand(message: string): boolean {
    const trimmed = message.trim();
    // Any line starting with $ or common shell patterns is a command
    if (/^\$/.test(trimmed)) return true;
    // Broad match — terminals support ALL commands, not a whitelist
    const shellPattern = /^[\w./~-]+(\s+|$)/;
    return shellPattern.test(trimmed) && trimmed.length < 500;
  }

  private checkCommandSafety(command: string): { requiresApproval: boolean; reason: string } {
    // Only flag truly catastrophic operations — everything else runs freely
    const catastrophic = /rm\s+(-[a-zA-Z]*f[a-zA-Z]*\s+.*\/|.*\s+\/(\s|$))|sudo\s+rm\s+|mkfs\.|dd\s+if=.*of=\/dev\/|:\(\)\{.*\};/i;
    if (catastrophic.test(command)) {
      return { requiresApproval: true, reason: 'Potentially destructive system command' };
    }
    return { requiresApproval: false, reason: '' };
  }

  private async runAgentScript(
    message: string,
    context: Record<string, unknown>,
    opts: { timeoutSec: number; antiTimeout: boolean } = { timeoutSec: 120, antiTimeout: false }
  ) {
    const scriptPath = path.join(__dirname, '../../agent/agent.py');
    const mode = (context.agentMode as AgentMode) ?? this.mode;
    const payload = JSON.stringify({
      message,
      context: {
        ...context,
        agentTimeoutSec: opts.timeoutSec,
        antiTimeout: opts.antiTimeout,
      },
      mode,
    });
    this.lastScriptResponse = '';

    return new Promise<void>((resolve, reject) => {
      this.currentProcess = spawn('python3', [scriptPath], {
        env: { ...process.env, OCEAN_AGENT_PAYLOAD: payload },
      });

      let stdout = '';
      let stderr = '';
      let timeoutId: ReturnType<typeof setTimeout> | null = null;
      let extensionId: ReturnType<typeof setInterval> | null = null;

      const clearTimers = () => {
        if (timeoutId) clearTimeout(timeoutId);
        if (extensionId) clearInterval(extensionId);
        timeoutId = null;
        extensionId = null;
      };

      if (opts.antiTimeout) {
        extensionId = setInterval(() => {
          this.emitEvent({
            type: 'status',
            id: crypto.randomUUID(),
            timestamp: Date.now(),
            summary: 'Anti-timeout: extending session until workflow completes',
            status: 'running',
          });
        }, 600_000);
      } else if (opts.timeoutSec > 0) {
        timeoutId = setTimeout(() => {
          if (this.currentProcess) {
            this.currentProcess.kill('SIGTERM');
            this.currentProcess = null;
          }
          clearTimers();
          reject(new Error(`Agent timeout after ${opts.timeoutSec}s`));
        }, opts.timeoutSec * 1000);
      }

      this.currentProcess.stdout?.on('data', (data: Buffer) => {
        stdout += data.toString();
        const lines = stdout.split('\n');
        stdout = lines.pop() || '';
        for (const line of lines) {
          if (line.startsWith('OCEAN_EVENT:')) {
            try {
              this.emitEvent(JSON.parse(line.slice(12)));
            } catch { /* ignore */ }
          } else if (line.startsWith('OCEAN_RESPONSE:')) {
            try {
              const parsed = JSON.parse(line.slice(15));
              this.lastScriptResponse = parsed.content ?? String(parsed);
            } catch {
              this.lastScriptResponse = line.slice(15);
            }
          }
        }
      });

      this.currentProcess.stderr?.on('data', (data: Buffer) => {
        stderr += data.toString();
      });

      this.currentProcess.on('close', (code) => {
        clearTimers();
        this.currentProcess = null;
        if (code === 0) resolve();
        else reject(new Error(stderr || `Agent exited with code ${code}`));
      });
    });
  }

  private generateThoughtDetail(message: string, ctx: Record<string, unknown> | null, terminalType: TerminalType): string {
    const parts = [`User request: ${message}`];
    if (ctx?.workspacePath) parts.push(`Workspace: ${ctx.workspacePath}`);
    if (ctx?.activeFile) parts.push(`Active file: ${ctx.activeFile}`);
    if (ctx?.agentMode) parts.push(`Mode: ${ctx.agentMode}`);
    const modelConfig = ctx?.modelConfig as Record<string, unknown> | undefined;
    if (modelConfig?.modelId) parts.push(`Model: ${modelConfig.modelId} (temp=${modelConfig.temperature}, thinking=${modelConfig.thinkingLevel})`);
    else if (modelConfig?.selectedModelId) parts.push(`Model: ${modelConfig.selectedModelId}`);
    const mcpMeta = ctx?.mcpMeta as { connectedCount?: number } | undefined;
    if (mcpMeta?.connectedCount) parts.push(`MCP connectors: ${mcpMeta.connectedCount}`);
    const pluginTools = ctx?.pluginTools as unknown[] | undefined;
    if (pluginTools?.length) parts.push(`Plugin tools: ${pluginTools.length}`);
    const providerMeta = ctx?.providerMeta as { connectedCount?: number; activeProviderIds?: string[] } | undefined;
    if (providerMeta?.connectedCount) {
      parts.push(`Providers connected: ${providerMeta.connectedCount}`);
      if (providerMeta.activeProviderIds?.length) {
        parts.push(`Active in chat: ${providerMeta.activeProviderIds.join(', ')}`);
      }
    }
    const providerCtx = ctx?.providerContext as { proxyStatus?: { running: boolean; url: string } } | undefined;
    if (providerCtx?.proxyStatus?.running) {
      parts.push(`Ocean proxy: ${providerCtx.proxyStatus.url}`);
    }
    const multiAgent = ctx?.multiAgent as { enabled?: boolean; comboName?: string; mode?: string; members?: unknown[] } | undefined;
    if (multiAgent?.enabled) {
      parts.push(`Multi-agent team: ${multiAgent.comboName} (${multiAgent.mode})`);
      parts.push(`Team size: ${(multiAgent.members as unknown[])?.length ?? 0} agents`);
    }
    const member = ctx?.multiAgentMember as { memberName?: string; role?: string; providerId?: string; modelId?: string } | undefined;
    if (member?.memberName) {
      parts.push(`Agent: ${member.memberName} (${member.role}) — ${member.providerId}/${member.modelId}`);
    }
    const phase = ctx?._multiAgentPhase as string | undefined;
    if (phase) parts.push(`Workflow phase: ${phase}`);
    parts.push(`Terminal backend: ${terminalType}`);
    parts.push(`Platform: ${this.platformContext.platform} (electron=${this.platformContext.isElectron})`);
    if (terminalType === 'shell') {
      parts.push('Using real host shell — all installed binaries available.');
    } else if (terminalType === 'cloud') {
      parts.push('Using Google Cloud Shell — Debian VM with apt, pip, gcloud.');
    } else {
      parts.push('Using native mobile Linux environment.');
    }
    if (this.mode === 'review') parts.push('Review mode: only catastrophic commands need approval.');
    else parts.push('All terminal commands run without restriction.');
    return parts.join('\n');
  }

  private emitEvent(event: AgentEvent) {
    this.emit('event', event);
  }

  private delay(ms: number) {
    return new Promise((r) => setTimeout(r, ms));
  }
}
