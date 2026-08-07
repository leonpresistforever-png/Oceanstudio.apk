import { useState, useEffect, useCallback } from 'react';
import { Play, Calendar, Sparkles, Loader2 } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { usePlaygroundStore, getDueScheduledTasks } from '../store/playgroundStore';
import { PLAYGROUND_CATEGORIES, PLAYGROUND_TOOLS, getPlaygroundTool, getAllPlaygroundTools, getMcpCount, getPluginCount } from '../playground/catalog';
import { VOICE_TONES, MUSIC_GENRES, type MusicStudioConfig, type PlaygroundMediaJob } from '../playground/types';
import {
  runPlaygroundAgentTask,
  runPlaygroundMediaJob,
  runMusicGeneration,
  runBeatResearch,
} from '../lib/playgroundRunner';
import PlaygroundVisual from '../components/playground/PlaygroundVisual';
import PlaygroundToolSidebar from '../components/playground/PlaygroundToolSidebar';
import ActiveBotPanel from '../components/playground/ActiveBotPanel';
import RecordingPanel from '../components/playground/RecordingPanel';
import ScreenNarratorPanel from '../components/playground/ScreenNarratorPanel';
import ThreeDStudioPanel from '../components/playground/ThreeDStudioPanel';
import PlaygroundMarketplacePanel from '../components/playground/PlaygroundMarketplacePanel';
import SkillsStorePanel from '../components/skills/SkillsStorePanel';
import '../pages/PluginsPage.css';
import './PlaygroundPage.css';

export default function PlaygroundPage() {
  const setCenterView = useAppStore((s) => s.setCenterView);
  const workspacePath = useAppStore((s) => s.workspacePath);
  const activeFile = useAppStore((s) => s.activeFile);
  const addAgentLog = useAppStore((s) => s.addAgentLog);

  const activeCategory = usePlaygroundStore((s) => s.activeCategory);
  const activeToolId = usePlaygroundStore((s) => s.activeToolId);
  const searchQuery = usePlaygroundStore((s) => s.searchQuery);
  const prompt = usePlaygroundStore((s) => s.prompt);
  const musicConfig = usePlaygroundStore((s) => s.musicConfig);
  const agentTasks = usePlaygroundStore((s) => s.agentTasks);
  const scheduledTasks = usePlaygroundStore((s) => s.scheduledTasks);
  const mediaJobs = usePlaygroundStore((s) => s.mediaJobs);
  const customMarketplaceTools = usePlaygroundStore((s) => s.customMarketplaceTools);
  const selected3DSourceId = usePlaygroundStore((s) => s.selected3DSourceId);

  const setActiveCategory = usePlaygroundStore((s) => s.setActiveCategory);
  const setActiveTool = usePlaygroundStore((s) => s.setActiveTool);
  const setSearchQuery = usePlaygroundStore((s) => s.setSearchQuery);
  const setPrompt = usePlaygroundStore((s) => s.setPrompt);
  const setMusicConfig = usePlaygroundStore((s) => s.setMusicConfig);
  const addAgentTask = usePlaygroundStore((s) => s.addAgentTask);
  const updateAgentTask = usePlaygroundStore((s) => s.updateAgentTask);
  const addScheduledTask = usePlaygroundStore((s) => s.addScheduledTask);
  const removeScheduledTask = usePlaygroundStore((s) => s.removeScheduledTask);
  const addMediaJob = usePlaygroundStore((s) => s.addMediaJob);
  const updateMediaJob = usePlaygroundStore((s) => s.updateMediaJob);

  const [running, setRunning] = useState(false);
  const [scheduleTitle, setScheduleTitle] = useState('');
  const [scheduleAt, setScheduleAt] = useState('');

  const activeTool = activeToolId ? getPlaygroundTool(activeToolId, customMarketplaceTools) : null;
  const totalToolCount = getAllPlaygroundTools(customMarketplaceTools).length;
  const catMeta = PLAYGROUND_CATEGORIES.find((c) => c.id === activeCategory);

  const runTask = useCallback(async (
    title: string,
    taskPrompt: string,
    scope: 'filesystem' | 'apps' | 'web' | 'workspace' | 'custom' = 'custom'
  ) => {
    if (!taskPrompt.trim()) return;
    setRunning(true);
    const taskId = addAgentTask({ title, prompt: taskPrompt, scope });
    updateAgentTask(taskId, { status: 'running' });
    try {
      const result = await runPlaygroundAgentTask(
        { title, prompt: taskPrompt, scope },
        workspacePath,
        activeFile
      );
      updateAgentTask(taskId, { status: 'completed', result });
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Playground: ${title}`,
        detail: result.slice(0, 2000),
        status: 'completed',
      });
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed';
      updateAgentTask(taskId, { status: 'failed', result: message });
      throw err;
    } finally {
      setRunning(false);
    }
  }, [workspacePath, activeFile, addAgentTask, updateAgentTask, addAgentLog]);

  const runMedia = useCallback(async (type: PlaygroundMediaJob['type'], provider: string) => {
    if (!prompt.trim()) return;
    setRunning(true);
    const jobId = addMediaJob({ type, prompt, provider });
    updateMediaJob(jobId, { status: 'running' });
    try {
      const result = await runPlaygroundMediaJob({ type, prompt, provider }, workspacePath);
      updateMediaJob(jobId, { status: 'done', outputUrl: result.slice(0, 500) });
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Playground ${type}: ${provider}`,
        detail: result.slice(0, 2000),
        status: 'completed',
      });
    } catch (err) {
      updateMediaJob(jobId, { status: 'error' });
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: `Playground ${type} failed`,
        detail: err instanceof Error ? err.message : 'Failed',
        status: 'failed',
      });
    } finally {
      setRunning(false);
    }
  }, [prompt, workspacePath, addMediaJob, updateMediaJob, addAgentLog]);

  const handleToolSelect = useCallback((id: string) => {
    setActiveTool(id);
    const tool = getPlaygroundTool(id, customMarketplaceTools);
    if (tool) {
      setPrompt(`${tool.name}: ${tool.description}`);
    }
  }, [setActiveTool, setPrompt, customMarketplaceTools]);

  // Scheduled task runner
  useEffect(() => {
    const interval = setInterval(() => {
      const due = getDueScheduledTasks();
      for (const task of due) {
        usePlaygroundStore.getState().updateScheduledTask(task.id, { status: 'running' });
        void runTask(task.title, task.prompt, 'custom')
          .then(() => {
            const repeat = task.repeat ?? 'once';
            if (repeat === 'hourly') {
              usePlaygroundStore.getState().updateScheduledTask(task.id, {
                status: 'pending',
                runAt: Date.now() + 3_600_000,
                lastRunAt: Date.now(),
              });
            } else if (repeat === 'daily') {
              usePlaygroundStore.getState().updateScheduledTask(task.id, {
                status: 'pending',
                runAt: Date.now() + 86_400_000,
                lastRunAt: Date.now(),
              });
            } else {
              usePlaygroundStore.getState().updateScheduledTask(task.id, {
                status: 'completed',
                lastRunAt: Date.now(),
              });
            }
          })
          .catch(() => {
            usePlaygroundStore.getState().updateScheduledTask(task.id, { status: 'failed' });
          });
      }
    }, 30_000);
    return () => clearInterval(interval);
  }, [runTask]);

  function handleRun() {
    if (!prompt.trim()) return;

    if (activeCategory === 'agents') {
      void runTask(activeTool?.name ?? 'Agent Task', prompt, 'filesystem');
    } else if (activeCategory === 'research') {
      void runTask('Research', prompt, activeTool?.id?.includes('web') ? 'web' : 'filesystem');
    } else if (activeCategory === 'media-image') {
      void runMedia('image', activeTool?.provider ?? 'google');
    } else if (activeCategory === 'media-video') {
      void runMedia('video', activeTool?.provider ?? 'google');
    } else if (activeCategory === 'upscale') {
      void runMedia(prompt.includes('video') ? 'upscale-video' : 'upscale-image', activeTool?.provider ?? 'replicate');
    } else if (activeCategory === 'editor') {
      void runMedia('edit-image', 'google');
    } else if (activeCategory === 'media-3d') {
      const provider = activeTool?.provider ?? selected3DSourceId ?? 'meshy';
      void runMedia(activeTool?.id?.includes('2d') ? '2d-to-3d' : 'text-to-3d', provider);
    } else if (activeCategory === 'audio-tts') {
      void runTask(`TTS — ${activeTool?.name ?? 'Speech'}`, `Convert to speech: ${prompt}`, 'custom');
    } else if (activeCategory === 'audio-music') {
      if (activeTool?.id === 'pg-beat-fetch') {
        setRunning(true);
        void runBeatResearch(workspacePath)
          .then((r) => {
            addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: 'Beat research', detail: r, status: 'completed' });
          })
          .catch((err) => {
            addAgentLog({
              id: crypto.randomUUID(),
              type: 'error',
              timestamp: Date.now(),
              summary: 'Beat research failed',
              detail: err instanceof Error ? err.message : 'Failed',
              status: 'failed',
            });
          })
          .finally(() => setRunning(false));
      } else {
        setRunning(true);
        void runMusicGeneration({ ...musicConfig, lyrics: prompt || musicConfig.lyrics }, workspacePath)
          .then((r) => {
            addAgentLog({ id: crypto.randomUUID(), type: 'result', timestamp: Date.now(), summary: 'Music generated', detail: r, status: 'completed' });
          })
          .catch((err) => {
            addAgentLog({
              id: crypto.randomUUID(),
              type: 'error',
              timestamp: Date.now(),
              summary: 'Music generation failed',
              detail: err instanceof Error ? err.message : 'Failed',
              status: 'failed',
            });
          })
          .finally(() => setRunning(false));
      }
    } else if (activeCategory === 'mcp' || activeCategory === 'plugin') {
      void runTask(activeTool?.name ?? 'Connector', `Use ${activeTool?.kind ?? 'tool'} "${activeTool?.name}": ${prompt}`, 'custom');
    } else {
      void runTask(activeTool?.name ?? catMeta?.label ?? 'Playground', prompt, 'custom');
    }
  }

  function handleSchedule() {
    if (!scheduleTitle || !scheduleAt || !prompt) return;
    let repeat: 'once' | 'hourly' | 'daily' = 'once';
    if (activeTool?.id === 'pg-cron') repeat = 'daily';
    addScheduledTask({
      title: scheduleTitle,
      prompt,
      toolId: activeToolId ?? undefined,
      runAt: new Date(scheduleAt).getTime(),
      repeat,
    });
    setScheduleTitle('');
    setScheduleAt('');
  }

  function truncate(text: string, max: number): string {
    if (text.length <= max) return text;
    return `${text.slice(0, max)}...`;
  }

  return (
    <div className="playground-page">
      <header className="plugins-header pg-header">
        <div className="plugins-header-left">
          <button className="plugins-back" onClick={() => setCenterView('editor')}>← Back</button>
          <h1><Sparkles size={20} style={{ display: 'inline', marginRight: 8, verticalAlign: -3 }} />Playground</h1>
        </div>
        <span className="pg-header-stat">{PLAYGROUND_CATEGORIES.length} categories · {totalToolCount} tools · {getMcpCount()}+ MCPs · {getPluginCount()}+ plugins</span>
      </header>

      <PlaygroundVisual />

      <div className="pg-body">
        <PlaygroundToolSidebar
          activeCategory={activeCategory}
          activeToolId={activeToolId}
          searchQuery={searchQuery}
          onCategory={setActiveCategory}
          onTool={handleToolSelect}
          onSearch={setSearchQuery}
        />

        <main className="pg-main">
          {!workspacePath && activeCategory !== 'active-bot' && activeCategory !== 'research' && (
            <div className="pg-workspace-hint">
              Open a workspace from the home screen to run filesystem and agent tasks.
            </div>
          )}

          <div className="pg-context-card">
            <h3>{catMeta?.icon} {catMeta?.label}</h3>
            <p className="pg-context-desc">
              {activeCategory === 'agents' && 'Assign agents to read your filesystem, research apps, and execute long-running tasks.'}
              {activeCategory === 'active-bot' && 'Your daily life partner — full device control, screen interaction, game companion, and continuous multi-day sessions. Use at your own risk.'}
              {activeCategory === 'recording' && 'HEVC/H.265 high-quality recording — anti-blur pan, anti-compression, up to 4K/200Mbps. Electron EXE and Android APK only.'}
              {activeCategory === 'research' && 'Deep web scraping, file manager research, and long-run content extraction.'}
              {activeCategory === 'media-image' && 'Generate images via Imagen, DALL·E, Flux, SDXL, or connected MCP/plugins.'}
              {activeCategory === 'media-video' && 'Create videos with Veo 3.1, Google Flow OAuth, Runway, Pika, and more.'}
              {activeCategory === 'upscale' && 'Upscale images and videos to high quality with Real-ESRGAN, Topaz, and more.'}
              {activeCategory === 'editor' && 'Edit images — inpaint, background removal, style transfer.'}
              {activeCategory === 'media-3d' && 'Text-to-3D, 2D-to-3D — open-source local models on Electron EXE; cloud APIs with free tiers on APK.'}
              {activeCategory === 'audio-tts' && 'Text-to-speech via Google TTS, ElevenLabs MCP, OpenAI, Azure.'}
              {activeCategory === 'audio-music' && 'Music with Google Lyria, custom beats, lyrics, full instrument config.'}
              {activeCategory === 'schedule' && 'Schedule and assign recurring agent tasks.'}
              {activeCategory === 'mcp' && `Browse ${getMcpCount()}+ MCP connectors tagged per playground feature. Add custom MCPs with env requirements.`}
              {activeCategory === 'plugin' && `Browse ${getPluginCount()}+ plugins for media, audio, 3D, and automation. Add custom plugins with install guides.`}
              {activeCategory === 'skills' && 'Install 51+ builtin skills and browse 2000+ open-source skills. Upload skill.md, create with agents, auto-populated from OSS stores.'}
            </p>
            {activeTool && (
              <div className="pg-active-tool">
                <span>{activeTool.icon}</span>
                <strong>{activeTool.name}</strong>
                <span className={`pg-tool-kind ${activeTool.kind}`}>{activeTool.kind}</span>
                {activeTool.provider && <span className="pg-provider-tag">{activeTool.provider}</span>}
              </div>
            )}
          </div>

          {activeCategory !== 'active-bot' && activeCategory !== 'recording' && activeCategory !== 'skills' && (
          <div className="pg-prompt-area">
            <textarea
              className="pg-prompt-input"
              rows={4}
              placeholder={
                activeCategory === 'audio-music'
                  ? 'Enter lyrics or describe the music you want to generate...'
                  : activeCategory === 'mcp' || activeCategory === 'plugin'
                    ? 'Describe what you want this connector or plugin to do...'
                    : 'Describe your task — agents will read filesystem, scrape web, generate media...'
              }
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                  e.preventDefault();
                  handleRun();
                }
              }}
            />
            <button className="pg-run-btn" onClick={handleRun} disabled={running || !prompt.trim()}>
              {running ? <Loader2 size={16} className="pg-spin" /> : <Play size={16} />}
              {running ? 'Running...' : 'Run'}
            </button>
          </div>
          )}

          {activeCategory === 'active-bot' && (
            <ActiveBotPanel />
          )}

          {activeCategory === 'recording' && (
            <RecordingPanel />
          )}

          {activeCategory === 'narrator' && (
            <ScreenNarratorPanel />
          )}

          {activeCategory === 'media-3d' && (
            <ThreeDStudioPanel />
          )}

          {activeCategory === 'mcp' && (
            <PlaygroundMarketplacePanel kind="mcp" />
          )}

          {activeCategory === 'plugin' && (
            <PlaygroundMarketplacePanel kind="plugin" />
          )}

          {activeCategory === 'skills' && (
            <SkillsStorePanel scope="playground" title="Playground Skills" showUpload showAgentCreate />
          )}

          {activeCategory === 'audio-music' && (
            <section className="pg-music-studio">
              <h4>Music Studio Config</h4>
              <div className="pg-music-grid">
                <label>
                  <span>Genre</span>
                  <select value={musicConfig.genre} onChange={(e) => setMusicConfig({ genre: e.target.value })}>
                    {MUSIC_GENRES.map((g) => <option key={g} value={g}>{g}</option>)}
                  </select>
                </label>
                <label>
                  <span>Tempo (BPM)</span>
                  <input type="number" min={60} max={200} value={musicConfig.tempo}
                    onChange={(e) => setMusicConfig({ tempo: Number(e.target.value) })} />
                </label>
                <label>
                  <span>Voice</span>
                  <select value={musicConfig.voiceGender} onChange={(e) => setMusicConfig({ voiceGender: e.target.value as 'male' | 'female' | 'neutral' })}>
                    <option value="male">Male</option>
                    <option value="female">Female</option>
                    <option value="neutral">Neutral</option>
                  </select>
                </label>
                <label>
                  <span>Tone</span>
                  <select value={musicConfig.voiceTone} onChange={(e) => setMusicConfig({ voiceTone: e.target.value })}>
                    {VOICE_TONES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </label>
                <label>
                  <span>Provider</span>
                  <select value={musicConfig.provider} onChange={(e) => setMusicConfig({ provider: e.target.value as MusicStudioConfig['provider'] })}>
                    <option value="auto">Auto (best available)</option>
                    <option value="google-lyria">Google Lyria</option>
                    <option value="elevenlabs">ElevenLabs MCP</option>
                    <option value="suno">Suno AI</option>
                  </select>
                </label>
                <label className="pg-full">
                  <span>Drums</span>
                  <input value={musicConfig.drumDesc} onChange={(e) => setMusicConfig({ drumDesc: e.target.value })} />
                </label>
                <label className="pg-full">
                  <span>Guitar</span>
                  <input value={musicConfig.guitarDesc} onChange={(e) => setMusicConfig({ guitarDesc: e.target.value })} />
                </label>
                <label className="pg-full">
                  <span>Piano</span>
                  <input value={musicConfig.pianoDesc} onChange={(e) => setMusicConfig({ pianoDesc: e.target.value })} />
                </label>
                <label className="pg-full">
                  <span>Bass</span>
                  <input value={musicConfig.bassDesc} onChange={(e) => setMusicConfig({ bassDesc: e.target.value })} />
                </label>
                <label className="pg-full">
                  <span>Sound Effects</span>
                  <input value={musicConfig.sfxDesc} onChange={(e) => setMusicConfig({ sfxDesc: e.target.value })} />
                </label>
                <label className="pg-full">
                  <span>Upload Beat (name)</span>
                  <input placeholder="my-beat.wav" value={musicConfig.beatUploadName ?? ''}
                    onChange={(e) => setMusicConfig({ beatUploadName: e.target.value })} />
                </label>
              </div>
            </section>
          )}

          {(activeCategory === 'schedule' || activeCategory === 'agents') && (
            <section className="pg-schedule-section">
              <h4><Calendar size={14} /> Schedule Task</h4>
              <div className="pg-schedule-row">
                <input placeholder="Task title" value={scheduleTitle} onChange={(e) => setScheduleTitle(e.target.value)} />
                <input type="datetime-local" value={scheduleAt} onChange={(e) => setScheduleAt(e.target.value)} />
                <button className="pg-schedule-btn" onClick={handleSchedule}>Schedule</button>
              </div>
              {scheduledTasks.length > 0 && (
                <div className="pg-task-list">
                  {scheduledTasks.map((t) => (
                    <div key={t.id} className={`pg-task-row ${t.status}`}>
                      <strong>{t.title}</strong>
                      <span>{new Date(t.runAt).toLocaleString()}</span>
                      <button onClick={() => removeScheduledTask(t.id)}>×</button>
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {(agentTasks.length > 0 || mediaJobs.length > 0) && (
            <section className="pg-results">
              <h4>Recent Results</h4>
              {agentTasks.slice(0, 5).map((t) => (
                <div key={t.id} className={`pg-result-card ${t.status}`}>
                  <div className="pg-result-top">
                    <strong>{t.title}</strong>
                    <span>{t.status}</span>
                  </div>
                  {t.result && <p>{truncate(t.result, 300)}</p>}
                </div>
              ))}
              {mediaJobs.slice(0, 3).map((j) => (
                <div key={j.id} className={`pg-result-card ${j.status}`}>
                  <div className="pg-result-top">
                    <strong>{j.type} — {j.provider}</strong>
                    <span>{j.status}</span>
                  </div>
                  {j.outputUrl && <p>{j.outputUrl.slice(0, 200)}</p>}
                </div>
              ))}
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
