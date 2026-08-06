export type ActiveBotPlatform = 'electron' | 'android' | 'web';

export interface ActiveBotCapability {
  id: string;
  label: string;
  description: string;
  icon: string;
  /** Which platforms support this capability */
  platforms: ActiveBotPlatform[];
  /** Requires explicit user opt-in beyond risk acceptance */
  requiresElevated?: boolean;
  hidden?: boolean;
}

export const ACTIVE_BOT_CAPABILITIES: ActiveBotCapability[] = [
  {
    id: 'screen-control',
    label: 'Live Screen Control',
    description: 'Interact with your screen in real time — click, type, navigate with minimal latency.',
    icon: '🖥️',
    platforms: ['electron', 'android'],
    requiresElevated: true,
  },
  {
    id: 'file-ops',
    label: 'File Manager',
    description: 'Arrange, move, rename, configure, compile, and decompile project files.',
    icon: '📁',
    platforms: ['electron', 'android', 'web'],
  },
  {
    id: 'app-install',
    label: 'Install & Configure Apps',
    description: 'Install applications and configure system settings on your behalf.',
    icon: '📦',
    platforms: ['electron', 'android'],
    requiresElevated: true,
  },
  {
    id: 'web-browse',
    label: 'Web & Article Analysis',
    description: 'Open websites, read articles, summarize content, and research topics.',
    icon: '🌐',
    platforms: ['electron', 'android', 'web'],
  },
  {
    id: 'social-upload',
    label: 'Social Media Upload',
    description: 'Upload videos and content to social platforms upon your request.',
    icon: '📤',
    platforms: ['electron', 'android'],
    requiresElevated: true,
  },
  {
    id: 'game-assist',
    label: 'Game Companion',
    description: 'Anti-AFK, live game interaction, and gameplay.md learning file.',
    icon: '🎮',
    platforms: ['electron', 'android'],
    requiresElevated: true,
    hidden: true,
  },
  {
    id: 'long-run',
    label: 'Continuous Operation',
    description: 'Runs non-stop for days until you say stop — no half sessions.',
    icon: '♾️',
    platforms: ['electron', 'android', 'web'],
  },
  {
    id: 'mood-partner',
    label: 'Daily Life Partner',
    description: 'Soft tone greetings, jokes, and mood check-ins throughout the day.',
    icon: '💬',
    platforms: ['electron', 'android', 'web'],
  },
  {
    id: 'terminal-shell',
    label: 'Terminal & Shell',
    description: 'Execute shell commands, scripts, and automation pipelines.',
    icon: '⌨️',
    platforms: ['electron', 'android'],
  },
  {
    id: 'browser-only',
    label: 'Browser Sandbox',
    description: 'Limited to in-tab actions — no OS-level control.',
    icon: '🔒',
    platforms: ['web'],
    hidden: true,
  },
  {
    id: 'accessibility',
    label: 'Android Accessibility',
    description: 'Requires Accessibility Service enabled for screen interaction on APK.',
    icon: '♿',
    platforms: ['android'],
    requiresElevated: true,
    hidden: true,
  },
];

export const ACTIVE_BOT_TOOLS = [
  { id: 'ab-screen', name: 'Screen Interaction', description: 'Live screen control with no latency', icon: '🖱️' },
  { id: 'ab-files', name: 'File Arrangement', description: 'Organize, compile, decompile files', icon: '🗂️' },
  { id: 'ab-install', name: 'App Installer', description: 'Install and configure applications', icon: '📲' },
  { id: 'ab-research', name: 'Article Analyzer', description: 'Open sites and analyze content', icon: '📰' },
  { id: 'ab-social', name: 'Social Uploader', description: 'Upload videos to social media', icon: '📱' },
  { id: 'ab-game', name: 'Game Companion', description: 'Anti-AFK and gameplay.md learning', icon: '🕹️' },
  { id: 'ab-mood', name: 'Mood Check', description: 'Greet, joke, and check your mood', icon: '😊' },
  { id: 'ab-schedule', name: 'Background Worker', description: 'Continuous multi-day operation', icon: '🔄' },
] as const;

export function detectActiveBotPlatform(info: {
  isElectron?: boolean;
  isAndroid?: boolean;
  isWeb?: boolean;
}): ActiveBotPlatform {
  if (info.isElectron) return 'electron';
  if (info.isAndroid) return 'android';
  return 'web';
}

export function getCapabilitiesForPlatform(platform: ActiveBotPlatform, showHidden = false): ActiveBotCapability[] {
  return ACTIVE_BOT_CAPABILITIES.filter((c) => {
    if (!c.platforms.includes(platform)) return false;
    if (c.hidden && !showHidden) return false;
    return true;
  });
}

export function getPlatformLabel(platform: ActiveBotPlatform): string {
  switch (platform) {
    case 'electron': return 'Desktop (EXE)';
    case 'android': return 'Android (APK)';
    case 'web': return 'Web Browser';
  }
}

export function getPlatformDescription(platform: ActiveBotPlatform): string {
  switch (platform) {
    case 'electron':
      return 'Full device control — screen interaction, app install, file ops, games, social upload, and multi-day sessions.';
    case 'android':
      return 'Full mobile control via Accessibility Service. Enable Accessibility in Android Settings for screen interaction.';
    case 'web':
      return 'Sandboxed browser mode. File workspace, web research, and agent tasks only — no OS-level screen control.';
  }
}
