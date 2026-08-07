import { getOceanAPI } from './platform';
import { buildAgentContext } from './agentContext';
import { runAgentInference } from './providerInference';
import { runPlaygroundAgentTask } from './playgroundRunner';
import {
  detectActiveBotPlatform,
  getCapabilitiesForPlatform,
  type ActiveBotPlatform,
} from '../playground/activeBotCapabilities';
import type { ActiveBotSession, ActiveBotMood } from '../playground/types';
import { usePlaygroundStore } from '../store/playgroundStore';
import { useGameCompanionStore } from '../store/gameCompanionStore';
import type { GameCompanionConfig } from '../playground/gameCompanionTypes';
import { serializeGameConfigForAgent } from '../playground/gameCompanionTypes';
import {
  buildGameplayMdContent,
  startGameCompanionTimers,
  stopGameCompanionTimers,
} from './gameCompanionRunner';

const GREETINGS = [
  "Hey there! I'm here whenever you need me — just say the word.",
  "Good to see you! Ready to help with whatever's on your mind.",
  "Hello! Your active bot is online and listening.",
  "Hi! Hope your day is going well. What can I do for you?",
];

const JOKES = [
  "Why do programmers prefer dark mode? Because light attracts bugs.",
  "I told my computer I needed a break — it said 'No problem, I'll go to sleep.'",
  "There are only 10 types of people: those who understand binary and those who don't.",
  "Why did the developer go broke? Because he used up all his cache.",
  "I'd tell you a UDP joke, but you might not get it.",
];

const MOOD_PROMPTS: Record<ActiveBotMood, string> = {
  happy: "You seem in great spirits! Want me to keep the momentum going?",
  neutral: "How are you feeling today? I'm here if you need anything.",
  tired: "Sounds like a long day. I can handle the heavy lifting while you rest.",
  stressed: "Take a breath — I'll take care of the tasks. You've got this.",
  focused: "Love the focus! I'll stay quiet unless you need me.",
  unknown: "Just checking in — how's your mood today?",
};

let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let moodTimer: ReturnType<typeof setInterval> | null = null;
let heartbeatInFlight = false;

const MAX_TASK_QUEUE = 50;

function log(type: ActiveBotSession['activityLog'][0]['type'], message: string) {
  usePlaygroundStore.getState().addActiveBotLog({ type, message });
}

export async function resolveActiveBotPlatform(): Promise<ActiveBotPlatform> {
  const api = getOceanAPI();
  const info = await api.platform.get();
  return detectActiveBotPlatform(info);
}

export function createActiveBotSession(platform: ActiveBotPlatform): ActiveBotSession {
  const caps = getCapabilitiesForPlatform(platform, true).map((c) => c.id);
  return {
    id: crypto.randomUUID(),
    status: 'idle',
    platform,
    startedAt: Date.now(),
    lastHeartbeat: Date.now(),
    riskAccepted: false,
    accessibilityGranted: false,
    gameMode: false,
    currentMood: 'unknown',
    enabledCapabilities: caps,
    activityLog: [],
    taskQueue: [],
  };
}

export async function ensureGameplayMd(
  workspacePath: string,
  config: GameCompanionConfig
): Promise<void> {
  const api = getOceanAPI();
  const relPath = config.gameplayMdPath || 'gameplay.md';
  const path = relPath.startsWith('/') ? relPath : `${workspacePath}/${relPath}`;
  const title = config.gameTitle || 'Game';
  let existing = '';
  try {
    existing = await api.fs.readFile(path);
  } catch {
    /* new file */
  }

  if (existing.includes(`# ${title} — Gameplay Guide`)) return;

  const content = buildGameplayMdContent(config);
  await api.fs.writeFile(path, existing ? `${existing}\n\n${content}` : content);
  log('game', `Created ${relPath} for ${title}`);
}

export async function startActiveBot(
  workspacePath: string,
  activeFile: string | null,
  options?: { gameMode?: boolean; gameTitle?: string }
): Promise<void> {
  const store = usePlaygroundStore.getState();
  const session = store.activeBotSession;
  if (!session?.riskAccepted) {
    throw new Error('You must accept the risk warning before starting Active Bot.');
  }

  if (session.status === 'paused') {
    resumeActiveBot(workspacePath, activeFile);
    return;
  }

  const platform = session.platform;
  if (platform === 'android' && !session.accessibilityGranted) {
    throw new Error('Enable Android Accessibility Service before starting screen control.');
  }

  const gameConfig = useGameCompanionStore.getState().config;
  const gameMode = options?.gameMode ?? gameConfig.enabled;
  const gameTitle = options?.gameTitle ?? gameConfig.gameTitle;

  if (gameMode && !gameTitle.trim()) {
    throw new Error('Set a game title in Game Companion configuration before starting.');
  }

  store.updateActiveBotSession({
    status: 'running',
    startedAt: Date.now(),
    lastHeartbeat: Date.now(),
    gameMode,
    gameTitle: gameTitle || undefined,
  });

  const greeting = GREETINGS[Math.floor(Math.random() * GREETINGS.length)];
  log('greeting', greeting);

  if (gameMode && workspacePath) {
    const cfg = { ...gameConfig, enabled: true, gameTitle };
    if (cfg.updateGameplayMd) {
      await ensureGameplayMd(workspacePath, cfg);
    }
    log('game', `Game companion active: ${gameTitle}. Anti-AFK ${cfg.antiAfkEnabled ? 'on' : 'off'}.`);

    startGameCompanionTimers(
      cfg,
      (prompt) => queueActiveBotTask(prompt),
      (prompt) => queueActiveBotTask(prompt),
      () => {
        log('warning', `Session limit reached (${cfg.maxSessionHours}h). Stopping Active Bot.`);
        stopActiveBot();
      },
    );
  }

  stopActiveBotTimers();
  heartbeatTimer = setInterval(() => {
    void runHeartbeat(workspacePath, activeFile);
  }, 15_000);

  moodTimer = setInterval(() => {
    const s = usePlaygroundStore.getState().activeBotSession;
    if (!s || s.status !== 'running') return;
    const mood = s.currentMood;
    log('mood', MOOD_PROMPTS[mood]);
  }, 300_000);

  log('system', `Active Bot started on ${platform}. Running continuously until you say stop.`);
}

async function runHeartbeat(workspacePath: string, activeFile: string | null): Promise<void> {
  if (heartbeatInFlight) return;
  heartbeatInFlight = true;
  try {
    const store = usePlaygroundStore.getState();
    const session = store.activeBotSession;
    if (!session || session.status !== 'running') return;

    store.updateActiveBotSession({ lastHeartbeat: Date.now() });

    const freshQueue = usePlaygroundStore.getState().activeBotSession?.taskQueue ?? [];
    if (freshQueue.length === 0) return;

    const next = freshQueue[0];
    store.updateActiveBotSession({ taskQueue: freshQueue.slice(1) });
    log('action', `Processing: ${next.slice(0, 80)}...`);
    try {
      await runPlaygroundAgentTask(
        { title: 'Active Bot Task', prompt: next, scope: 'custom' },
        workspacePath,
        activeFile
      );
    } catch (err) {
      log('warning', err instanceof Error ? err.message : 'Task failed');
    }
  } finally {
    heartbeatInFlight = false;
  }
}

export function stopActiveBot(): void {
  stopActiveBotTimers();
  stopGameCompanionTimers();
  const store = usePlaygroundStore.getState();
  store.updateActiveBotSession({ status: 'stopped' });
  log('system', 'Active Bot stopped. Session ended.');
}

export function pauseActiveBot(): void {
  stopActiveBotTimers();
  stopGameCompanionTimers();
  usePlaygroundStore.getState().updateActiveBotSession({ status: 'paused' });
  log('system', 'Active Bot paused.');
}

export function resumeActiveBot(workspacePath: string, activeFile: string | null): void {
  const store = usePlaygroundStore.getState();
  const session = store.activeBotSession;
  if (!session || session.status !== 'paused') return;

  store.updateActiveBotSession({
    status: 'running',
    lastHeartbeat: Date.now(),
  });

  const gameConfig = useGameCompanionStore.getState().config;
  if (session.gameMode) {
    const cfg = { ...gameConfig, enabled: true, gameTitle: session.gameTitle ?? gameConfig.gameTitle };
    startGameCompanionTimers(
      cfg,
      (prompt) => queueActiveBotTask(prompt),
      (prompt) => queueActiveBotTask(prompt),
      () => {
        log('warning', `Session limit reached (${cfg.maxSessionHours}h). Stopping Active Bot.`);
        stopActiveBot();
      },
    );
  }

  stopActiveBotTimers();
  heartbeatTimer = setInterval(() => {
    void runHeartbeat(workspacePath, activeFile);
  }, 15_000);

  moodTimer = setInterval(() => {
    const s = usePlaygroundStore.getState().activeBotSession;
    if (!s || s.status !== 'running') return;
    log('mood', MOOD_PROMPTS[s.currentMood]);
  }, 300_000);

  log('system', 'Active Bot resumed.');
}

function stopActiveBotTimers(): void {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
  if (moodTimer) {
    clearInterval(moodTimer);
    moodTimer = null;
  }
}

export function queueActiveBotTask(prompt: string): void {
  usePlaygroundStore.setState((s) => {
    if (!s.activeBotSession) return s;
    const queue = [...s.activeBotSession.taskQueue, prompt];
    const trimmed = queue.length > MAX_TASK_QUEUE ? queue.slice(-MAX_TASK_QUEUE) : queue;
    return {
      activeBotSession: { ...s.activeBotSession, taskQueue: trimmed },
    };
  });
  log('action', `Queued: ${prompt.slice(0, 100)}`);
}

export async function runActiveBotCommand(
  command: string,
  workspacePath: string,
  activeFile: string | null
): Promise<string> {
  const store = usePlaygroundStore.getState();
  const session = store.activeBotSession;
  if (!session?.riskAccepted) {
    return 'Please accept the risk warning first.';
  }

  const lower = command.toLowerCase().trim();
  const gameConfig = useGameCompanionStore.getState().config;
  const stopKey = gameConfig.emergencyStopKey.toLowerCase();

  if (lower === 'stop' || lower === 'stop bot' || lower === 'end session' || lower === stopKey.toLowerCase()) {
    stopActiveBot();
    return 'Active Bot stopped. See you next time!';
  }

  if (lower === 'joke' || lower.includes('tell me a joke')) {
    const joke = JOKES[Math.floor(Math.random() * JOKES.length)];
    log('joke', joke);
    return joke;
  }

  if (lower.includes('mood') || lower.includes('how am i')) {
    const mood = session.currentMood;
    const msg = MOOD_PROMPTS[mood];
    log('mood', msg);
    return msg;
  }

  const platform = session.platform;
  const platformCtx = `[Active Bot — ${platform.toUpperCase()}] Full device partner. Soft tone. Continuous until stop. `;

  const scopePrompts: Record<string, string> = {
    screen: `${platformCtx}[Screen Control] Interact with screen in real time. ${command}`,
    files: `${platformCtx}[File Ops] Arrange, compile, decompile files. ${command}`,
    install: `${platformCtx}[App Install] Install and configure apps. ${command}`,
    research: `${platformCtx}[Web Research] Open websites and analyze articles. ${command}`,
    social: `${platformCtx}[Social Upload] Upload content to social media. ${command}`,
    game: `${platformCtx}[Game Companion] Anti-AFK, live interaction. Update gameplay.md. ${command}`,
  };

  let prompt = `${platformCtx}${command}`;
  if (lower.includes('screen') || lower.includes('click') || lower.includes('interact')) {
    prompt = scopePrompts.screen;
  } else if (lower.includes('file') || lower.includes('compile') || lower.includes('decompile')) {
    prompt = scopePrompts.files;
  } else if (lower.includes('install') || lower.includes('app')) {
    prompt = scopePrompts.install;
  } else if (lower.includes('upload') || lower.includes('social') || lower.includes('video')) {
    prompt = scopePrompts.social;
  } else if (lower.includes('game') || lower.includes('afk') || session.gameMode) {
    const cfg = { ...gameConfig, gameTitle: session.gameTitle ?? gameConfig.gameTitle };
    prompt = `${scopePrompts.game}\n\n${serializeGameConfigForAgent(cfg)}`;
    if (session.gameTitle && workspacePath && cfg.updateGameplayMd) {
      await ensureGameplayMd(workspacePath, cfg);
    }
  } else if (lower.includes('article') || lower.includes('website') || lower.includes('research')) {
    prompt = scopePrompts.research;
  }

  log('action', command.slice(0, 120));

  if (session.status === 'running') {
    queueActiveBotTask(prompt);
    return 'Task queued — Active Bot is working on it continuously.';
  }

  try {
    const ctx = await buildAgentContext({
      workspacePath,
      activeFile,
      terminalType: 'shell',
      skillScope: 'playground',
    });
    const inference = await runAgentInference(prompt, {
      ...ctx,
      activeBot: {
        platform,
        gameMode: session.gameMode,
        gameTitle: session.gameTitle,
        capabilities: session.enabledCapabilities,
        gameConfig: session.gameMode ? serializeGameConfigForAgent(gameConfig) : undefined,
      },
    } as Record<string, unknown>);
    return inference.content;
  } catch {
    return runPlaygroundAgentTask(
      { title: 'Active Bot', prompt, scope: 'custom' },
      workspacePath,
      activeFile
    );
  }
}

export function tellJoke(): string {
  const joke = JOKES[Math.floor(Math.random() * JOKES.length)];
  usePlaygroundStore.getState().addActiveBotLog({ type: 'joke', message: joke });
  return joke;
}

export function checkMood(mood: ActiveBotMood): string {
  usePlaygroundStore.getState().setActiveBotMood(mood);
  const msg = MOOD_PROMPTS[mood];
  usePlaygroundStore.getState().addActiveBotLog({ type: 'mood', message: msg });
  return msg;
}
