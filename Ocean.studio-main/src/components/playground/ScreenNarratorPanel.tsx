import { useEffect, useRef, useCallback } from 'react';
import { Mic, MicOff, Eye, Volume2, Loader2 } from 'lucide-react';
import { useAppStore } from '../../store/appStore';
import { useNarratorStore } from '../../store/narratorStore';
import { usePromptEnhancerStore } from '../../store/promptEnhancerStore';
import { useSpeechRecognition } from '../../hooks/useSpeechRecognition';
import { useWakeWord } from '../../hooks/useWakeWord';
import { describeScreen, runVoiceCommand, narrateAndSpeak } from '../../lib/screenNarratorRunner';
import './ScreenNarratorPanel.css';

export default function ScreenNarratorPanel() {
  const workspacePath = useAppStore((s) => s.workspacePath);
  const activeFile = useAppStore((s) => s.activeFile);
  const config = usePromptEnhancerStore((s) => s.config);
  const mode = useNarratorStore((s) => s.mode);
  const enabled = useNarratorStore((s) => s.enabled);
  const lastDescription = useNarratorStore((s) => s.lastDescription);
  const setMode = useNarratorStore((s) => s.setMode);
  const setEnabled = useNarratorStore((s) => s.setEnabled);
  const setLastDescription = useNarratorStore((s) => s.setLastDescription);
  const addCommand = useNarratorStore((s) => s.addCommand);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const wakePhrase = config.wakePhrase ?? 'ocean';

  const handleVoiceCommand = useCallback(async (command: string) => {
    if (!command.trim()) return;
    addCommand(command);
    setMode('processing');
    try {
      const response = await runVoiceCommand({ command, workspacePath, activeFile });
      setLastDescription(response);
      setMode('speaking');
      await narrateAndSpeak(response, config);
    } catch (err) {
      setLastDescription(err instanceof Error ? err.message : 'Command failed');
    }
    setMode(enabled ? 'listening' : 'idle');
  }, [workspacePath, activeFile, config, enabled, addCommand, setLastDescription, setMode]);

  const { processTranscript } = useWakeWord({
    wakePhrase,
    enabled: enabled && config.backgroundAssistant !== false,
    onWake: () => setMode('listening'),
    onCommand: handleVoiceCommand,
  });

  const stt = useSpeechRecognition({
    language: config.sttLanguage,
    continuous: true,
    onResult: ({ transcript, isFinal }) => {
      if (enabled) processTranscript(transcript, isFinal);
    },
  });

  async function handleDescribe() {
    setMode('processing');
    try {
      const text = await describeScreen({ workspacePath, activeFile, config });
      setLastDescription(text);
      setMode('speaking');
      await narrateAndSpeak(text, config);
    } catch (err) {
      setLastDescription(err instanceof Error ? err.message : 'Failed to describe screen');
    }
    setMode(enabled ? 'listening' : 'idle');
  }

  function toggleBackground() {
    const next = !enabled;
    setEnabled(next);
    if (next) {
      stt.start();
    } else {
      stt.stop();
      if (intervalRef.current) clearInterval(intervalRef.current);
    }
  }

  useEffect(() => {
    if (!enabled) return;
    const sec = config.narratorIntervalSec ?? 30;
    intervalRef.current = setInterval(() => {
      if (mode === 'idle' || mode === 'listening') void handleDescribe();
    }, sec * 1000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [enabled, config.narratorIntervalSec]);

  const statusClass = mode === 'listening' ? 'snp-status--listening'
    : mode === 'processing' ? 'snp-status--processing'
    : 'snp-status--idle';

  return (
    <div className="snp-panel">
      <header className="snp-header">
        <h2>Screen Narrator & Voice Assistant</h2>
        <p>Live screen reading with provider agent — say &quot;{wakePhrase}&quot; + command to activate in background</p>
      </header>

      <div className={`snp-status ${statusClass}`}>
        {mode === 'processing' && <Loader2 size={14} className="spin" />}
        {mode === 'listening' && <Mic size={14} />}
        {mode === 'speaking' && <Volume2 size={14} />}
        {mode === 'idle' && <MicOff size={14} />}
        {mode === 'listening' ? `Listening for "${wakePhrase}"…` : mode}
      </div>

      <div className="snp-controls">
        <button
          type="button"
          className={`snp-btn ${enabled ? 'snp-btn--active' : 'snp-btn--primary'}`}
          onClick={toggleBackground}
        >
          {enabled ? <MicOff size={16} /> : <Mic size={16} />}
          {enabled ? 'Stop background assistant' : 'Start background assistant'}
        </button>
        <button type="button" className="snp-btn" onClick={handleDescribe} disabled={mode === 'processing'}>
          <Eye size={16} /> Describe screen now
        </button>
        <button type="button" className="snp-btn" onClick={() => stt.toggle()}>
          <Mic size={16} /> {stt.listening ? 'Stop mic' : 'Push-to-talk'}
        </button>
      </div>

      <div className="snp-output">
        {lastDescription || 'Narrator output will appear here. Enable background assistant or click Describe screen.'}
      </div>

      <p className="snp-hint">
        Tone: {config.narratorTone ?? 'helper'} · Interval: {config.narratorIntervalSec ?? 30}s ·
        Configure wake phrase in Agent Studio → Voice & Screen
      </p>
    </div>
  );
}
