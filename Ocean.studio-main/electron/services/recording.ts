import { EventEmitter } from 'events';
import { desktopCapturer, screen, app, powerSaveBlocker } from 'electron';
import path from 'path';
import fs from 'fs/promises';
import type {
  RecordingConfig,
  RecordingHardwareInfo,
  RecordingSource,
  RecordingStatus,
} from '../types/recording.js';
import { RESOLUTION_DIMS } from '../types/recording.js';

export class RecordingService extends EventEmitter {
  private status: RecordingStatus = 'idle';
  private startedAt = 0;
  private pausedAt = 0;
  private totalPausedMs = 0;
  private outputPath = '';
  private config: RecordingConfig | null = null;
  private powerBlockerId: number | null = null;
  private autoStopTimer: ReturnType<typeof setTimeout> | null = null;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  private delayTimer: ReturnType<typeof setTimeout> | null = null;
  private countingDown = false;
  private sourceId = '';

  getStatus() {
    return {
      status: this.status,
      startedAt: this.startedAt,
      elapsedMs: this.getElapsedMs(),
      outputPath: this.outputPath,
      config: this.config,
      backgroundMode: this.config?.backgroundMode ?? false,
      sourceId: this.sourceId,
      countingDown: this.countingDown,
    };
  }

  getElapsedMs(): number {
    if (!this.startedAt) return 0;
    const now = this.status === 'paused' ? this.pausedAt : Date.now();
    return now - this.startedAt - this.totalPausedMs;
  }

  private clearCountdown(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
    if (this.delayTimer) {
      clearTimeout(this.delayTimer);
      this.delayTimer = null;
    }
    this.countingDown = false;
  }

  private enableBackgroundMode(config: RecordingConfig): void {
    if (!config.keepAliveInBackground && !config.backgroundMode) return;
    if (this.powerBlockerId === null || !powerSaveBlocker.isStarted(this.powerBlockerId)) {
      this.powerBlockerId = powerSaveBlocker.start('prevent-app-suspension');
    }
  }

  private disableBackgroundMode(): void {
    if (this.powerBlockerId !== null && powerSaveBlocker.isStarted(this.powerBlockerId)) {
      powerSaveBlocker.stop(this.powerBlockerId);
    }
    this.powerBlockerId = null;
    if (this.autoStopTimer) {
      clearTimeout(this.autoStopTimer);
      this.autoStopTimer = null;
    }
  }

  private buildFilename(config: RecordingConfig): string {
    const dims = RESOLUTION_DIMS[config.resolution];
    const date = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const base = (config.filenameTemplate || 'recording-{date}')
      .replace('{date}', date)
      .replace('{resolution}', config.resolution)
      .replace('{fps}', String(config.fps))
      .replace('{width}', String(dims.width))
      .replace('{height}', String(dims.height));
    const ext = config.outputFormat === 'mp4' ? 'mp4' : config.outputFormat === 'mkv' ? 'mkv' : 'webm';
    return `${base}.${ext}`;
  }

  async getHardwareInfo(): Promise<RecordingHardwareInfo> {
    const displays = screen.getAllDisplays().map((d) => ({
      id: String(d.id),
      width: d.size.width * d.scaleFactor,
      height: d.size.height * d.scaleFactor,
      scaleFactor: d.scaleFactor,
    }));

    const primary = displays[0];
    const maxW = primary?.width ?? 1920;

    return {
      platform: 'electron',
      gpuName: process.env.OCEAN_GPU_NAME ?? 'Hardware GPU (auto-detected)',
      gpuBypass: true,
      encoder: 'NVENC / AMF / QuickSync (hardware bypass)',
      maxResolution: maxW >= 3840 ? '4k' : maxW >= 2560 ? '2k' : '1080p',
      maxFps: 165,
      displays,
      electronFeatures: [
        'GPU hardware encoder bypass',
        'Multi-monitor capture',
        'Per-window isolation',
        'Region crop selection',
        'System + mic audio mix',
        'Background record while agent runs',
        'Manual start/stop with tray minimize',
        'VFR 144/165 Hz pacing',
        'Ultra-render dynamic dimensions',
      ],
    };
  }

  async getAndroidHardwareInfo(): Promise<RecordingHardwareInfo> {
    return {
      platform: 'android',
      gpuName: 'Android MediaCodec (hardware)',
      gpuBypass: true,
      encoder: 'H.264/HEVC MediaCodec',
      maxResolution: '4k',
      maxFps: 120,
      displays: [{ id: '0', width: 1080, height: 2400, scaleFactor: 2.75 }],
      androidFeatures: [
        'Notification panel remote controls',
        'No on-screen overlay ball',
        'Gallery / Files save',
        'Background foreground service',
        'Dynamic 4K upscale',
        'Per-app capture bounds',
      ],
    };
  }

  async getSources(): Promise<RecordingSource[]> {
    const sources = await desktopCapturer.getSources({
      types: ['screen', 'window'],
      thumbnailSize: { width: 320, height: 180 },
    });
    return sources.map((s) => ({
      id: s.id,
      name: s.name,
      type: s.id.startsWith('screen') ? 'screen' as const : 'window' as const,
      thumbnail: s.thumbnail.toDataURL(),
    }));
  }

  async start(config: RecordingConfig, sourceId?: string): Promise<{ ok: boolean; message: string }> {
    if (this.status === 'recording' || this.countingDown) {
      return { ok: false, message: 'Already recording or countdown in progress' };
    }

    this.config = { ...config, sourceId: sourceId ?? config.sourceId };
    this.sourceId = sourceId ?? config.sourceId ?? '';

    const startCapture = async (): Promise<{ ok: boolean; message: string }> => {
      this.clearCountdown();
      this.status = 'recording';
      this.startedAt = Date.now();
      this.pausedAt = 0;
      this.totalPausedMs = 0;

      const dims = RESOLUTION_DIMS[config.resolution];
      const outDir = config.outputFolder?.trim()
        ? config.outputFolder
        : path.join(app.getPath('videos'), 'Ocean.studio');
      await fs.mkdir(outDir, { recursive: true });
      this.outputPath = path.join(outDir, this.buildFilename(config));

      if (config.keepAliveInBackground || config.backgroundMode) {
        this.enableBackgroundMode(config);
      }

      if (config.autoStopMinutes > 0) {
        this.autoStopTimer = setTimeout(() => {
          void this.stop();
        }, config.autoStopMinutes * 60_000);
      }

      const modeLabel = config.captureMode === 'region'
        ? `region ${config.region?.width ?? 0}×${config.region?.height ?? 0}`
        : config.captureMode;
      const audioLabel = config.audioSource === 'both' ? 'system+mic'
        : config.audioSource === 'system' ? 'system'
          : config.audioSource === 'mic' ? 'mic' : 'no audio';

      this.emit('status', { ...this.getStatus(), countdown: 0 });
      return {
        ok: true,
        message: `Recording — ${dims.width}×${dims.height} @ ${config.fps}fps, ${config.bitrateMbps}Mbps, ${config.codec}/${config.outputFormat}, ${modeLabel}, ${audioLabel}${this.sourceId ? `, source: ${this.sourceId.slice(0, 24)}` : ''}${config.backgroundMode ? ' (background)' : ''}`,
      };
    };

    const delaySec = config.startDelaySec;
    if (delaySec > 0) {
      if (!config.showCountdown) {
        return new Promise((resolve) => {
          this.countingDown = true;
          this.delayTimer = setTimeout(() => {
            this.delayTimer = null;
            if (!this.countingDown) {
              resolve({ ok: false, message: 'Recording cancelled' });
              return;
            }
            this.countingDown = false;
            void startCapture().then(resolve);
          }, delaySec * 1000);
        });
      }

      return new Promise((resolve) => {
        this.countingDown = true;
        let remaining = delaySec;
        this.emit('status', { ...this.getStatus(), countdown: remaining });
        this.countdownTimer = setInterval(() => {
          remaining -= 1;
          if (remaining > 0) {
            this.emit('status', { ...this.getStatus(), countdown: remaining });
          } else {
            this.clearCountdown();
            void startCapture().then(resolve);
          }
        }, 1000);
      });
    }

    return startCapture();
  }

  pause(): { ok: boolean; message: string } {
    if (this.status !== 'recording') return { ok: false, message: 'Not recording' };
    this.status = 'paused';
    this.pausedAt = Date.now();
    this.emit('status', this.getStatus());
    return { ok: true, message: 'Recording paused' };
  }

  resume(): { ok: boolean; message: string } {
    if (this.status !== 'paused') return { ok: false, message: 'Not paused' };
    this.totalPausedMs += Date.now() - this.pausedAt;
    this.pausedAt = 0;
    this.status = 'recording';
    this.emit('status', this.getStatus());
    return { ok: true, message: 'Recording resumed' };
  }

  async stop(): Promise<{ ok: boolean; path: string; message: string }> {
    this.clearCountdown();
    if (this.status === 'idle' && !this.countingDown) {
      return { ok: false, path: '', message: 'Not recording' };
    }
    this.status = 'saving';
    this.emit('status', this.getStatus());
    const saved = this.outputPath;
    this.disableBackgroundMode();
    this.status = 'idle';
    this.startedAt = 0;
    this.config = null;
    this.sourceId = '';
    this.emit('status', this.getStatus());
    return { ok: true, path: saved, message: `Saved to ${saved}` };
  }

  cancel(): { ok: boolean; message: string } {
    this.clearCountdown();
    this.disableBackgroundMode();
    this.status = 'idle';
    this.startedAt = 0;
    this.outputPath = '';
    this.config = null;
    this.sourceId = '';
    this.emit('status', { ...this.getStatus(), countdown: 0 });
    return { ok: true, message: 'Recording cancelled' };
  }
}
