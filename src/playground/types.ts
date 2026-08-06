export type PlaygroundToolCategory =
  | 'agents'
  | 'research'
  | 'media-image'
  | 'media-video'
  | 'media-3d'
  | 'audio-tts'
  | 'audio-music'
  | 'upscale'
  | 'editor'
  | 'mcp'
  | 'plugin'
  | 'skills'
  | 'schedule'
  | 'active-bot'
  | 'recording'
  | 'narrator';

export type PlaygroundToolKind = 'mcp' | 'plugin' | 'provider' | 'builtin' | 'opensource';

export type PlaygroundPlatform = 'electron' | 'android' | 'web' | 'cloud';

export interface PlaygroundTool {
  id: string;
  name: string;
  description: string;
  category: PlaygroundToolCategory;
  kind: PlaygroundToolKind;
  icon: string;
  provider?: string;
  authType?: 'oauth' | 'api_key' | 'mcp' | 'none';
  tags: string[];
  enabled: boolean;
  /** Platforms where this tool runs (electron=local EXE, android=APK, cloud=offload) */
  platforms?: PlaygroundPlatform[];
  /** Offers a free tier */
  freeTier?: boolean;
  /** Environment variables / API keys required */
  envRequirements?: string;
  /** Playground features this MCP/plugin supports */
  featureTags?: PlaygroundToolCategory[];
  /** User can install custom variant */
  customInstall?: boolean;
  /** Install docs URL or path */
  installGuide?: string;
}

export type ScheduledTaskStatus = 'pending' | 'running' | 'completed' | 'failed';

export interface PlaygroundScheduledTask {
  id: string;
  title: string;
  prompt: string;
  toolId?: string;
  runAt: number;
  repeat?: 'once' | 'hourly' | 'daily';
  status: ScheduledTaskStatus;
  lastRunAt?: number;
  result?: string;
  createdAt: number;
}

export interface PlaygroundMediaJob {
  id: string;
  type: 'image' | 'video' | 'upscale-image' | 'upscale-video' | 'edit-image' | 'text-to-3d' | '2d-to-3d';
  prompt: string;
  provider: string;
  status: 'queued' | 'running' | 'done' | 'error';
  outputUrl?: string;
  createdAt: number;
}

export interface MusicStudioConfig {
  genre: string;
  mood: string;
  tempo: number;
  voiceGender: 'male' | 'female' | 'neutral';
  voiceTone: string;
  lyrics: string;
  drumDesc: string;
  guitarDesc: string;
  pianoDesc: string;
  bassDesc: string;
  sfxDesc: string;
  beatUploadName?: string;
  provider: 'google-lyria' | 'elevenlabs' | 'suno' | 'auto';
}

export interface PlaygroundAgentTask {
  id: string;
  title: string;
  prompt: string;
  scope: 'filesystem' | 'apps' | 'web' | 'workspace' | 'custom';
  status: ScheduledTaskStatus;
  result?: string;
  createdAt: number;
}

export const DEFAULT_MUSIC_CONFIG: MusicStudioConfig = {
  genre: 'electronic',
  mood: 'energetic',
  tempo: 120,
  voiceGender: 'neutral',
  voiceTone: 'warm',
  lyrics: '',
  drumDesc: 'tight kick, crisp hi-hats',
  guitarDesc: '',
  pianoDesc: 'ambient chords',
  bassDesc: 'sub bass',
  sfxDesc: '',
  provider: 'auto',
};

export const VOICE_TONES = [
  'warm', 'bright', 'deep', 'airy', 'raspy', 'smooth', 'crisp', 'soft',
  'powerful', 'whisper', 'narrator', 'singer', 'robotic', 'child', 'elder',
  'british', 'american', 'australian', 'indian', 'spanish',
] as const;

export const MUSIC_GENRES = [
  'electronic', 'hip-hop', 'pop', 'rock', 'jazz', 'classical', 'ambient',
  'lo-fi', 'cinematic', 'trap', 'house', 'techno', 'r&b', 'folk', 'metal',
] as const;

export type ActiveBotSessionStatus = 'idle' | 'running' | 'paused' | 'stopped';

export type ActiveBotMood = 'happy' | 'neutral' | 'tired' | 'stressed' | 'focused' | 'unknown';

export interface ActiveBotLogEntry {
  id: string;
  timestamp: number;
  type: 'action' | 'greeting' | 'joke' | 'mood' | 'game' | 'system' | 'warning';
  message: string;
}

export interface ActiveBotSession {
  id: string;
  status: ActiveBotSessionStatus;
  platform: 'electron' | 'android' | 'web';
  startedAt: number;
  lastHeartbeat: number;
  riskAccepted: boolean;
  accessibilityGranted: boolean;
  gameMode: boolean;
  gameTitle?: string;
  currentMood: ActiveBotMood;
  enabledCapabilities: string[];
  activityLog: ActiveBotLogEntry[];
  taskQueue: string[];
}
