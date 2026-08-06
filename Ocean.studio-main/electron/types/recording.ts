export type RecordingResolution = '4k' | '2k' | '1080p' | '720p';
export type RecordingFps = 30 | 60 | 90 | 120 | 144 | 165;
export type RecordingBitrateMbps = 30 | 60 | 80 | 100 | 150;
export type RecordingCaptureMode = 'fullscreen' | 'window' | 'region';
export type RecordingStatus = 'idle' | 'recording' | 'paused' | 'saving' | 'error';
export type RecordingAudioSource = 'system' | 'mic' | 'both' | 'none';
export type RecordingOutputFormat = 'webm' | 'mp4' | 'mkv';
export type RecordingCodec = 'h264' | 'hevc' | 'vp9';

export interface RecordingConfig {
  resolution: RecordingResolution;
  fps: RecordingFps;
  bitrateMbps: RecordingBitrateMbps;
  captureMode: RecordingCaptureMode;
  sourceId?: string;
  ultraRender: boolean;
  hardwareBypass: boolean;
  saveToGallery: boolean;
  notificationControls: boolean;
  backgroundMode: boolean;
  region?: { x: number; y: number; width: number; height: number };
  audioSource: RecordingAudioSource;
  outputFormat: RecordingOutputFormat;
  codec: RecordingCodec;
  cursorHighlight: boolean;
  clickRipple: boolean;
  systemAudio: boolean;
  micGain: number;
  startDelaySec: number;
  autoStopMinutes: number;
  outputFolder: string;
  filenameTemplate: string;
  minimizeToTray: boolean;
  keepAliveInBackground: boolean;
  showCountdown: boolean;
  includeAgentPanel: boolean;
}

export interface RecordingHardwareInfo {
  platform: 'electron' | 'android' | 'web';
  gpuName: string;
  gpuBypass: boolean;
  encoder: string;
  maxResolution: RecordingResolution;
  maxFps: RecordingFps;
  displays: { id: string; width: number; height: number; scaleFactor: number }[];
  androidFeatures?: string[];
  electronFeatures?: string[];
}

export interface RecordingSource {
  id: string;
  name: string;
  type: 'screen' | 'window';
  thumbnail?: string;
}

export const RESOLUTION_DIMS: Record<RecordingResolution, { width: number; height: number }> = {
  '4k': { width: 3840, height: 2160 },
  '2k': { width: 2560, height: 1440 },
  '1080p': { width: 1920, height: 1080 },
  '720p': { width: 1280, height: 720 },
};

export const DEFAULT_RECORDING_CONFIG: RecordingConfig = {
  resolution: '1080p',
  fps: 60,
  bitrateMbps: 80,
  captureMode: 'fullscreen',
  ultraRender: true,
  hardwareBypass: true,
  saveToGallery: true,
  notificationControls: true,
  backgroundMode: true,
  audioSource: 'both',
  outputFormat: 'webm',
  codec: 'vp9',
  cursorHighlight: true,
  clickRipple: false,
  systemAudio: true,
  micGain: 100,
  startDelaySec: 0,
  autoStopMinutes: 0,
  outputFolder: '',
  filenameTemplate: 'recording-{date}-{resolution}-{fps}fps',
  minimizeToTray: true,
  keepAliveInBackground: true,
  showCountdown: true,
  includeAgentPanel: false,
  region: { x: 0, y: 0, width: 1920, height: 1080 },
};
