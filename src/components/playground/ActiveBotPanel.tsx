import { useState, useEffect } from 'react';
import {
  Bot, AlertTriangle, Play, Square, Pause, Shield, Accessibility,
  Monitor, Smartphone, Globe, Loader2, MessageCircle,
} from 'lucide-react';
import { usePlaygroundStore } from '../../store/playgroundStore';
import { useAppStore } from '../../store/appStore';
import {
  getCapabilitiesForPlatform,
  getPlatformLabel,
  getPlatformDescription,
  ACTIVE_BOT_TOOLS,
} from '../../playground/activeBotCapabilities';
import {
  resolveActiveBotPlatform,
  createActiveBotSession,
  startActiveBot,
  stopActiveBot,
  pauseActiveBot,
  runActiveBotCommand,
  tellJoke,
  checkMood,
} from '../../lib/activeBotRunner';
import type { ActiveBotMood } from '../../playground/types';
import GameCompanionConfigPanel from './GameCompanionConfigPanel';
import { useGameCompanionStore } from '../../store/gameCompanionStore';

export default function ActiveBotPanel() {
  const workspacePath = useAppStore((s) => s.workspacePath);
  const activeFile = useAppStore((s) => s.activeFile);
  const prompt = usePlaygroundStore((s) => s.prompt);
  const setPrompt = usePlaygroundStore((s) => s.setPrompt);
  const session = usePlaygroundStore((s) => s.activeBotSession);
  const setActiveBotSession = usePlaygroundStore((s) => s.setActiveBotSession);
  const acceptActiveBotRisk = usePlaygroundStore((s) => s.acceptActiveBotRisk);
  const setAccessibilityGranted = usePlaygroundStore((s) => s.setAccessibilityGranted);

  const [platform, setPlatform] = useState<'electron' | 'android' | 'web'>('web');
  const [loading, setLoading] = useState(false);
  const [showHidden, setShowHidden] = useState(false);
  const [response, setResponse] = useState('');
  const gameConfig = useGameCompanionStore((s) => s.config);

  useEffect(() => {
    void resolveActiveBotPlatform().then((p) => {
      setPlatform(p);
      const existing = usePlaygroundStore.getState().activeBotSession;
      if (!existing) {
        setActiveBotSession(createActiveBotSession(p));
      } else if (existing.platform !== p) {
        setActiveBotSession({ ...existing, platform: p });
      }
    });
  }, [setActiveBotSession]);

  const caps = getCapabilitiesForPlatform(platform, showHidden);
  const isRunning = session?.status === 'running';
  const isPaused = session?.status === 'paused';
  const canStart = session?.riskAccepted
    && (platform !== 'android' || session.accessibilityGranted)
    && (!gameConfig.enabled || gameConfig.gameTitle.trim().length > 0);

  async function handleStart() {
    if (!session?.riskAccepted) return;
    setLoading(true);
    try {
      await startActiveBot(workspacePath, activeFile, {
        gameMode: gameConfig.enabled,
        gameTitle: gameConfig.gameTitle || undefined,
      });
    } catch (err) {
      setResponse(err instanceof Error ? err.message : 'Failed to start');
    } finally {
      setLoading(false);
    }
  }

  async function handleCommand() {
    if (!prompt.trim()) return;
    setLoading(true);
    try {
      const result = await runActiveBotCommand(prompt, workspacePath, activeFile);
      setResponse(result);
      setPrompt('');
    } finally {
      setLoading(false);
    }
  }

  const PlatformIcon = platform === 'electron' ? Monitor : platform === 'android' ? Smartphone : Globe;

  return (
    <div className="pg-active-bot">
      {/* Risk Warning */}
      <div className="pg-ab-warning">
        <AlertTriangle size={20} />
        <div>
          <strong>Use at your own risk</strong>
          <p>
            Active Bot can take full control of your device upon your request — screen interaction,
            file operations, app installation, social media uploads, and continuous multi-day sessions.
            Only enable if you fully understand and accept the risks.
          </p>
        </div>
        <label className="pg-ab-risk-check">
          <input
            type="checkbox"
            checked={session?.riskAccepted ?? false}
            onChange={(e) => {
              if (!session) setActiveBotSession(createActiveBotSession(platform));
              if (e.target.checked) acceptActiveBotRisk();
              else usePlaygroundStore.getState().updateActiveBotSession({ riskAccepted: false });
            }}
          />
          I understand and accept the risks
        </label>
      </div>

      {/* Platform Banner */}
      <div className={`pg-ab-platform pg-ab-platform-${platform}`}>
        <PlatformIcon size={22} />
        <div>
          <strong>{getPlatformLabel(platform)}</strong>
          <p>{getPlatformDescription(platform)}</p>
        </div>
        {platform === 'web' && (
          <span className="pg-ab-limited">Limited</span>
        )}
        {platform === 'electron' && (
          <span className="pg-ab-full">Full Control</span>
        )}
        {platform === 'android' && (
          <span className="pg-ab-full">APK Mode</span>
        )}
      </div>

      {/* Android Accessibility */}
      {platform === 'android' && (
        <div className="pg-ab-accessibility">
          <Accessibility size={18} />
          <div>
            <strong>Accessibility Service Required</strong>
            <p>
              For screen interaction on Android APK, enable Accessibility for Ocean.studio in
              Settings → Accessibility. Without it, only file and web tasks are available.
            </p>
          </div>
          <label className="pg-ab-risk-check">
            <input
              type="checkbox"
              checked={session?.accessibilityGranted ?? false}
              onChange={(e) => setAccessibilityGranted(e.target.checked)}
            />
            Accessibility enabled
          </label>
        </div>
      )}

      {/* Bot Status */}
      <div className="pg-ab-status-bar">
        <div className="pg-ab-avatar">
          <Bot size={28} />
          {isRunning && <span className="pg-ab-live" />}
        </div>
        <div className="pg-ab-status-info">
          <strong>Active Bot</strong>
          <span className={`pg-ab-status ${session?.status ?? 'idle'}`}>
            {isRunning ? 'LIVE — running continuously' : isPaused ? 'Paused' : session?.status === 'stopped' ? 'Stopped' : 'Standby'}
          </span>
          {session?.lastHeartbeat && isRunning && (
            <small>Heartbeat: {new Date(session.lastHeartbeat).toLocaleTimeString()}</small>
          )}
        </div>
        <div className="pg-ab-controls">
          <button
            className="pg-ab-btn start"
            onClick={() => void handleStart()}
            disabled={!canStart || isRunning || loading}
          >
            {loading ? <Loader2 size={16} className="pg-spin" /> : <Play size={16} />}
            {isPaused ? 'Resume' : 'Start'}
          </button>
          <button
            className="pg-ab-btn pause"
            onClick={pauseActiveBot}
            disabled={!isRunning}
          >
            <Pause size={16} />
          </button>
          <button
            className="pg-ab-btn stop"
            onClick={stopActiveBot}
            disabled={!isRunning && !isPaused}
          >
            <Square size={16} />
            Stop
          </button>
        </div>
      </div>

      {/* Game Companion — full configuration */}
      <GameCompanionConfigPanel platform={platform} disabled={isRunning} />

      {gameConfig.enabled && !gameConfig.gameTitle.trim() && (
        <p className="pg-game-config-hint">Enter a game title in the configuration above before starting.</p>
      )}

      {/* Platform Capabilities */}
      <section className="pg-ab-caps">
        <div className="pg-ab-caps-header">
          <Shield size={16} />
          <h4>Capabilities — {getPlatformLabel(platform)}</h4>
          <button className="pg-ab-hidden-toggle" onClick={() => setShowHidden(!showHidden)}>
            {showHidden ? 'Hide' : 'Show'} platform details
          </button>
        </div>
        <div className="pg-ab-cap-grid">
          {caps.map((cap) => (
            <div key={cap.id} className={`pg-ab-cap ${cap.requiresElevated ? 'elevated' : ''}`}>
              <span className="pg-ab-cap-icon">{cap.icon}</span>
              <div>
                <strong>{cap.label}</strong>
                <p>{cap.description}</p>
              </div>
              {cap.requiresElevated && <span className="pg-ab-elevated">Elevated</span>}
            </div>
          ))}
        </div>
      </section>

      {/* Quick Tools */}
      <section className="pg-ab-tools">
        <h4>Active Bot Tools</h4>
        <div className="pg-ab-tool-grid">
          {ACTIVE_BOT_TOOLS.filter((t) => {
            if (t.id === 'ab-game') return platform !== 'web';
            if (t.id === 'ab-screen' || t.id === 'ab-install' || t.id === 'ab-social') return platform !== 'web';
            return true;
          }).map((t) => (
            <button
              key={t.id}
              className="pg-ab-tool-btn"
              onClick={() => setPrompt(t.description)}
            >
              <span>{t.icon}</span>
              <strong>{t.name}</strong>
            </button>
          ))}
        </div>
      </section>

      {/* Mood & Social */}
      <section className="pg-ab-social">
        <h4><MessageCircle size={14} /> Partner Mode</h4>
        <div className="pg-ab-mood-row">
          {(['happy', 'neutral', 'tired', 'stressed', 'focused'] as ActiveBotMood[]).map((m) => (
            <button
              key={m}
              className={`pg-ab-mood-btn ${session?.currentMood === m ? 'active' : ''}`}
              onClick={() => setResponse(checkMood(m))}
            >
              {m}
            </button>
          ))}
          <button className="pg-ab-mood-btn joke" onClick={() => setResponse(tellJoke())}>
            Tell joke
          </button>
        </div>
      </section>

      {/* Command Input */}
      <div className="pg-ab-prompt">
        <textarea
          className="pg-prompt-input"
          rows={3}
          placeholder="Tell Active Bot what to do — screen control, file ops, research, upload, game assist... Say 'stop' to end session."
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
              e.preventDefault();
              void handleCommand();
            }
          }}
        />
      </div>

      {/* Response */}
      {response && (
        <div className="pg-ab-response">
          <Bot size={16} />
          <p>{response}</p>
        </div>
      )}

      {/* Activity Log */}
      {session && session.activityLog.length > 0 && (
        <section className="pg-ab-log">
          <h4>Activity Log</h4>
          <div className="pg-ab-log-list">
            {session.activityLog.slice(0, 12).map((entry) => (
              <div key={entry.id} className={`pg-ab-log-entry ${entry.type}`}>
                <span className="pg-ab-log-time">
                  {new Date(entry.timestamp).toLocaleTimeString()}
                </span>
                <span className="pg-ab-log-type">{entry.type}</span>
                <span>{entry.message}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Run command from parent prompt area */}
      <button
        className="pg-ab-run-cmd"
        onClick={() => void handleCommand()}
        disabled={loading || !prompt.trim() || !session?.riskAccepted}
      >
        {loading ? <Loader2 size={16} className="pg-spin" /> : <Bot size={16} />}
        Send to Active Bot
      </button>
    </div>
  );
}
