import { runPlaygroundAgentTask } from './playgroundRunner';
import { speakText, stopSpeaking } from './tts';
import { captureScreenSnapshot, formatScreenSnapshotForPrompt } from './screenCapture';
import type { PromptEnhancerConfig } from '../prompt/types';

const TONE_PROMPTS: Record<string, string> = {
  helper: 'You are a helpful screen assistant. Describe what you see clearly and offer actionable help.',
  storyteller: 'Narrate the screen content like a storyteller — engaging, descriptive, with personality.',
  technical: 'Describe the screen technically — UI elements, layout, data, and system state.',
  casual: 'Describe the screen casually, like a friend looking over your shoulder.',
};

export async function describeScreen(opts: {
  workspacePath: string;
  activeFile?: string | null;
  config: PromptEnhancerConfig;
  userHint?: string;
}): Promise<string> {
  const tone = opts.config.narratorTone ?? 'helper';
  const snapshot = await captureScreenSnapshot();
  const screenCtx = formatScreenSnapshotForPrompt(snapshot);
  const prompt = [
    TONE_PROMPTS[tone] ?? TONE_PROMPTS.helper,
    '',
    screenCtx,
    '',
    opts.userHint ? `User context: ${opts.userHint}` : 'Describe what is on screen and what the user might need help with.',
    'Keep response under 3 sentences unless asked for detail.',
  ].join('\n');

  return runPlaygroundAgentTask(
    { title: 'Screen Narrator', prompt, scope: 'custom' },
    opts.workspacePath,
    opts.activeFile ?? null
  );
}

export async function runVoiceCommand(opts: {
  command: string;
  workspacePath: string;
  activeFile?: string | null;
}): Promise<string> {
  const snapshot = await captureScreenSnapshot();
  const screenCtx = formatScreenSnapshotForPrompt(snapshot);
  const prompt = [
    `Voice command from user (wake phrase activated): "${opts.command}"`,
    '',
    screenCtx,
    '',
    'Execute or respond to this command using current screen and workspace context.',
  ].join('\n');
  return runPlaygroundAgentTask(
    { title: 'Voice Command', prompt, scope: 'custom' },
    opts.workspacePath,
    opts.activeFile ?? null
  );
}

export async function narrateAndSpeak(
  text: string,
  config: PromptEnhancerConfig
): Promise<void> {
  stopSpeaking();
  await speakText(text, {
    stripMarkdown: config.ttsStripMarkdown,
    rate: config.ttsRate,
    pitch: config.ttsPitch,
    voiceUri: config.ttsVoiceUri || undefined,
  });
}
