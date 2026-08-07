export type GameInputMethod = 'keyboard' | 'mouse' | 'controller' | 'touch';

export type GameControlProfile = 'fps' | 'mmorpg' | 'strategy' | 'idle' | 'sandbox' | 'custom';

export type GameAfkAction =
  | 'move_wasd'
  | 'jump'
  | 'interact'
  | 'rotate_camera'
  | 'press_random_key'
  | 'click_center'
  | 'scroll_wheel'
  | 'custom_macro';

export type GameVisionProvider = 'auto' | 'openai' | 'anthropic' | 'google' | 'local';

export interface GameKeyBinding {
  action: string;
  keys: string;
}

export interface GameScreenRegion {
  name: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface GameCompanionConfig {
  enabled: boolean;
  gameTitle: string;
  processName: string;
  windowTitle: string;
  launcherPath: string;
  inputMethod: GameInputMethod;
  controlProfile: GameControlProfile;
  keyBindings: GameKeyBinding[];
  antiAfkEnabled: boolean;
  antiAfkIntervalSec: number;
  antiAfkJitterSec: number;
  antiAfkActions: GameAfkAction[];
  customMacro: string;
  visionEnabled: boolean;
  visionProvider: GameVisionProvider;
  screenshotIntervalSec: number;
  screenRegions: GameScreenRegion[];
  pauseOnUserInput: boolean;
  maxSessionHours: number;
  safeZoneOnly: boolean;
  emergencyStopKey: string;
  updateGameplayMd: boolean;
  gameplayMdPath: string;
  bringWindowToFront: boolean;
  focusBeforeAction: boolean;
  androidPackageName: string;
  notes: string;
}

export const GAME_AFK_ACTIONS: { id: GameAfkAction; label: string; description: string }[] = [
  { id: 'move_wasd', label: 'WASD movement', description: 'Brief directional key presses' },
  { id: 'jump', label: 'Jump / Space', description: 'Press jump or space key' },
  { id: 'interact', label: 'Interact', description: 'Press E / F / use key' },
  { id: 'rotate_camera', label: 'Rotate camera', description: 'Small mouse movement' },
  { id: 'press_random_key', label: 'Random key', description: 'Press a safe random key' },
  { id: 'click_center', label: 'Click center', description: 'Left-click screen center' },
  { id: 'scroll_wheel', label: 'Scroll wheel', description: 'Small scroll up/down' },
  { id: 'custom_macro', label: 'Custom macro', description: 'Run custom macro sequence' },
];

export const GAME_CONTROL_PROFILES: { id: GameControlProfile; label: string; bindings: GameKeyBinding[] }[] = [
  {
    id: 'fps',
    label: 'FPS / Shooter',
    bindings: [
      { action: 'move_forward', keys: 'w' },
      { action: 'move_back', keys: 's' },
      { action: 'strafe_left', keys: 'a' },
      { action: 'strafe_right', keys: 'd' },
      { action: 'jump', keys: 'space' },
      { action: 'crouch', keys: 'ctrl' },
      { action: 'interact', keys: 'e' },
      { action: 'reload', keys: 'r' },
    ],
  },
  {
    id: 'mmorpg',
    label: 'MMORPG / RPG',
    bindings: [
      { action: 'move_forward', keys: 'w' },
      { action: 'auto_run', keys: 'numlock' },
      { action: 'interact', keys: 'f' },
      { action: 'ability_1', keys: '1' },
      { action: 'ability_2', keys: '2' },
      { action: 'mount', keys: 'shift+g' },
      { action: 'inventory', keys: 'i' },
      { action: 'map', keys: 'm' },
    ],
  },
  {
    id: 'strategy',
    label: 'Strategy / RTS',
    bindings: [
      { action: 'select', keys: 'left_click' },
      { action: 'move_camera', keys: 'arrow_keys' },
      { action: 'group_1', keys: 'ctrl+1' },
      { action: 'attack', keys: 'a' },
      { action: 'stop', keys: 's' },
      { action: 'hold', keys: 'h' },
    ],
  },
  {
    id: 'idle',
    label: 'Idle / Clicker',
    bindings: [
      { action: 'click', keys: 'left_click' },
      { action: 'upgrade', keys: 'u' },
      { action: 'collect', keys: 'c' },
      { action: 'prestige', keys: 'p' },
    ],
  },
  {
    id: 'sandbox',
    label: 'Sandbox (Minecraft-style)',
    bindings: [
      { action: 'move_forward', keys: 'w' },
      { action: 'jump', keys: 'space' },
      { action: 'break_block', keys: 'left_click' },
      { action: 'place_block', keys: 'right_click' },
      { action: 'inventory', keys: 'e' },
      { action: 'sprint', keys: 'ctrl' },
    ],
  },
  {
    id: 'custom',
    label: 'Custom',
    bindings: [],
  },
];

export const GAME_PRESETS: { id: string; label: string; config: Partial<GameCompanionConfig> }[] = [
  {
    id: 'minecraft',
    label: 'Minecraft',
    config: {
      gameTitle: 'Minecraft',
      processName: 'Minecraft',
      windowTitle: 'Minecraft',
      controlProfile: 'sandbox',
      antiAfkIntervalSec: 120,
      antiAfkActions: ['move_wasd', 'rotate_camera', 'jump'],
      androidPackageName: 'com.mojang.minecraftpe',
    },
  },
  {
    id: 'roblox',
    label: 'Roblox',
    config: {
      gameTitle: 'Roblox',
      processName: 'RobloxPlayerBeta',
      windowTitle: 'Roblox',
      controlProfile: 'sandbox',
      antiAfkIntervalSec: 90,
      antiAfkActions: ['move_wasd', 'jump', 'interact'],
      androidPackageName: 'com.roblox.client',
    },
  },
  {
    id: 'fortnite',
    label: 'Fortnite',
    config: {
      gameTitle: 'Fortnite',
      processName: 'FortniteClient',
      windowTitle: 'Fortnite',
      controlProfile: 'fps',
      antiAfkIntervalSec: 180,
      antiAfkActions: ['move_wasd', 'rotate_camera'],
    },
  },
  {
    id: 'wow',
    label: 'World of Warcraft',
    config: {
      gameTitle: 'World of Warcraft',
      processName: 'Wow',
      windowTitle: 'World of Warcraft',
      controlProfile: 'mmorpg',
      antiAfkIntervalSec: 240,
      antiAfkActions: ['move_wasd', 'jump', 'press_random_key'],
    },
  },
  {
    id: 'idle',
    label: 'Idle / AFK Grinder',
    config: {
      gameTitle: 'Idle Game',
      controlProfile: 'idle',
      antiAfkIntervalSec: 60,
      antiAfkActions: ['click_center', 'press_random_key'],
      visionEnabled: true,
      screenshotIntervalSec: 30,
    },
  },
];

export const DEFAULT_GAME_COMPANION_CONFIG: GameCompanionConfig = {
  enabled: false,
  gameTitle: '',
  processName: '',
  windowTitle: '',
  launcherPath: '',
  inputMethod: 'keyboard',
  controlProfile: 'sandbox',
  keyBindings: GAME_CONTROL_PROFILES.find((p) => p.id === 'sandbox')!.bindings,
  antiAfkEnabled: true,
  antiAfkIntervalSec: 120,
  antiAfkJitterSec: 15,
  antiAfkActions: ['move_wasd', 'rotate_camera'],
  customMacro: 'w,a,s,d,space',
  visionEnabled: false,
  visionProvider: 'auto',
  screenshotIntervalSec: 30,
  screenRegions: [
    { name: 'HUD', x: 0, y: 0, width: 1920, height: 120 },
    { name: 'Play area', x: 0, y: 120, width: 1920, height: 960 },
  ],
  pauseOnUserInput: true,
  maxSessionHours: 8,
  safeZoneOnly: true,
  emergencyStopKey: 'F12',
  updateGameplayMd: true,
  gameplayMdPath: 'gameplay.md',
  bringWindowToFront: true,
  focusBeforeAction: true,
  androidPackageName: '',
  notes: '',
};

export function applyGamePreset(
  presetId: string,
  current: GameCompanionConfig
): GameCompanionConfig {
  const preset = GAME_PRESETS.find((p) => p.id === presetId);
  if (!preset) return current;
  const profile = GAME_CONTROL_PROFILES.find(
    (p) => p.id === (preset.config.controlProfile ?? current.controlProfile)
  );
  return {
    ...current,
    ...preset.config,
    keyBindings: profile?.bindings ?? current.keyBindings,
  };
}

export function serializeGameConfigForAgent(config: GameCompanionConfig): string {
  const lines = [
    `Game: ${config.gameTitle || 'Unnamed'}`,
    `Process: ${config.processName || 'auto-detect'}`,
    `Window: ${config.windowTitle || 'auto-detect'}`,
    `Input: ${config.inputMethod} | Profile: ${config.controlProfile}`,
    `Anti-AFK: ${config.antiAfkEnabled ? `every ${config.antiAfkIntervalSec}s ±${config.antiAfkJitterSec}s` : 'off'}`,
    `Actions: ${config.antiAfkActions.join(', ')}`,
    `Vision: ${config.visionEnabled ? `${config.visionProvider} every ${config.screenshotIntervalSec}s` : 'off'}`,
    `Safety: pause on user input=${config.pauseOnUserInput}, max ${config.maxSessionHours}h, stop key=${config.emergencyStopKey}`,
    `Key bindings: ${config.keyBindings.map((b) => `${b.action}=${b.keys}`).join('; ')}`,
  ];
  if (config.notes.trim()) lines.push(`Notes: ${config.notes.trim()}`);
  return lines.join('\n');
}
