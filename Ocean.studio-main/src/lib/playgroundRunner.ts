import { getOceanAPI } from './platform';
import { buildAgentContext } from './agentContext';
import { runAgentInference } from './providerInference';
import type { PlaygroundAgentTask, PlaygroundMediaJob, MusicStudioConfig } from '../playground/types';
import { getPlaygroundTool } from '../playground/catalog';

export async function runPlaygroundAgentTask(
  task: Pick<PlaygroundAgentTask, 'prompt' | 'scope' | 'title'>,
  workspacePath: string,
  activeFile: string | null
): Promise<string> {
  const api = getOceanAPI();
  const platform = await api.platform.get();

  const scopePrompts: Record<string, string> = {
    filesystem: `[Playground — Filesystem Research] Scan the entire workspace and file manager. ${task.prompt}`,
    apps: `[Playground — App Research] Research installed apps, dependencies, and project structure. ${task.prompt}`,
    web: `[Playground — Web Research] Deep web scrape and long-run research. ${task.prompt}`,
    workspace: `[Playground — Workspace] ${task.prompt}`,
    custom: `[Playground] ${task.prompt}`,
  };

  const message = scopePrompts[task.scope] ?? task.prompt;
  const ctx = await buildAgentContext({
    workspacePath,
    activeFile,
    terminalType: platform.preferredTerminal ?? 'shell',
    skillScope: 'playground',
  });

  try {
    if (api.agent?.send) {
      const response = await api.agent.send(message, {
        ...ctx,
        playground: { task: task.title, scope: task.scope },
        _skipMultiAgentOrchestrator: true,
      });
      if (typeof response === 'string' && response.length > 0) return response;
    }
  } catch {
    /* fall through */
  }

  const inference = await runAgentInference(message, ctx as unknown as Record<string, unknown>);
  return inference.content;
}

export async function runPlaygroundMediaJob(
  job: Pick<PlaygroundMediaJob, 'type' | 'prompt' | 'provider'>,
  workspacePath: string
): Promise<string> {
  const providerMap: Record<string, string> = {
    google: 'google',
    'google-flow': 'google.antigravity',
    veo: 'google',
    openai: 'openai',
    replicate: 'openrouter',
    elevenlabs: 'anthropic.claude-code',
  };

  const providerId = providerMap[job.provider] ?? 'openai.codex';
  const prompt = `[Playground Media — ${job.type}] Provider: ${job.provider}. ${job.prompt}`;

  const inference = await runAgentInference(prompt, {
    workspacePath,
    modelConfig: { provider: providerId, modelId: 'default' },
    playgroundMedia: { type: job.type, provider: job.provider },
  } as Record<string, unknown>);

  return inference.content;
}

export function buildMusicGenerationPrompt(config: MusicStudioConfig): string {
  return [
    `[Playground Music Studio]`,
    `Genre: ${config.genre} | Mood: ${config.mood} | Tempo: ${config.tempo} BPM`,
    `Voice: ${config.voiceGender} — ${config.voiceTone}`,
    `Provider: ${config.provider}`,
    '',
    config.lyrics ? `Lyrics:\n${config.lyrics}` : 'Instrumental — no lyrics',
    '',
    'Instrumentation:',
    config.drumDesc && `Drums: ${config.drumDesc}`,
    config.guitarDesc && `Guitar: ${config.guitarDesc}`,
    config.pianoDesc && `Piano: ${config.pianoDesc}`,
    config.bassDesc && `Bass: ${config.bassDesc}`,
    config.sfxDesc && `SFX: ${config.sfxDesc}`,
    config.beatUploadName && `Custom beat: ${config.beatUploadName}`,
    '',
    'Generate music using the best available provider (Google Lyria, ElevenLabs, or connected MCP).',
  ].filter(Boolean).join('\n');
}

export async function runMusicGeneration(
  config: MusicStudioConfig,
  workspacePath: string
): Promise<string> {
  const prompt = buildMusicGenerationPrompt(config);
  return runPlaygroundAgentTask(
    { title: 'Music Generation', prompt, scope: 'custom' },
    workspacePath,
    null
  );
}

export async function runBeatResearch(workspacePath: string): Promise<string> {
  return runPlaygroundAgentTask(
    {
      title: 'Beat & Lyrics Research',
      scope: 'web',
      prompt: 'Research and fetch top 100 trending music beats, lyrical patterns, and sound references. Summarize genres, BPM ranges, and popular chord progressions.',
    },
    workspacePath,
    null
  );
}

export function resolveToolProvider(toolId: string): string {
  const tool = getPlaygroundTool(toolId);
  return tool?.provider ?? 'auto';
}
