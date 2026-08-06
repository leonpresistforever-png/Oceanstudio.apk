import { useState, useRef, useEffect } from 'react';
import { AnimatePresence } from 'framer-motion';
import { Send, Plus, Pause, MoreHorizontal, Image, FileUp, Plug, Webhook, Puzzle, X, Users, Mic, MicOff, Sparkles } from 'lucide-react';
import { useAppStore, type AgentLogEntry } from '../../store/appStore';
import { getOceanAPI } from '../../lib/platform';
import { buildAgentContext } from '../../lib/agentContext';
import { useModelStore } from '../../store/modelStore';
import { useMultiAgentStore } from '../../store/multiAgentStore';
import { runMultiAgentWorkflow } from '../../lib/multiAgentOrchestrator';
import { resolvePreferredTerminal } from '../../lib/terminalRouter';
import AgentLogItem from './AgentLogItem';
import TerminalPanel from './TerminalPanel';
import ModelSelector from './ModelSelector';
import ModelConfigPanel from './ModelConfigPanel';
import AgentTimeoutBar from './AgentTimeoutBar';
import { usePromptEnhancerStore } from '../../store/promptEnhancerStore';
import { processOutgoingPrompt, enhancePromptLocally, compressPrompt, estimateTokens } from '../../lib/promptProcessor';
import { useSpeechRecognition } from '../../hooks/useSpeechRecognition';
import { speakText, stopSpeaking } from '../../lib/tts';
import { getAgentSession, recordUserMessage, resetAgentSession } from '../../lib/agentSession';
import './AgentPanel.css';

interface Attachment {
  name: string;
  mimeType: string;
  content: string;
}

interface PlatformInfo {
  isElectron: boolean;
  isDesktop: boolean;
  isWeb: boolean;
  isMobile: boolean;
  isAndroid: boolean;
  hasNativeShell: boolean;
  hasNativeTerminal: boolean;
  shellLabel: string;
  preferredTerminal: 'shell' | 'cloud' | 'native';
}

const DEFAULT_PLATFORM: PlatformInfo = {
  isElectron: false,
  isDesktop: true,
  isWeb: true,
  isMobile: false,
  isAndroid: false,
  hasNativeShell: false,
  hasNativeTerminal: false,
  shellLabel: 'Shell',
  preferredTerminal: 'shell',
};

export default function AgentPanel() {
  const [message, setMessage] = useState('');
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [showTerminalMenu, setShowTerminalMenu] = useState(false);
  const [showTerminal, setShowTerminal] = useState(false);
  const [terminalType, setTerminalType] = useState<'shell' | 'cloud' | 'native'>('shell');
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [platformInfo, setPlatformInfo] = useState<PlatformInfo>(DEFAULT_PLATFORM);
  const logsEndRef = useRef<HTMLDivElement>(null);
  const attachRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const agentLogs = useAppStore((s) => s.agentLogs);
  const addAgentLog = useAppStore((s) => s.addAgentLog);
  const updateAgentLog = useAppStore((s) => s.updateAgentLog);
  const agentRunning = useAppStore((s) => s.agentRunning);
  const setAgentRunning = useAppStore((s) => s.setAgentRunning);
  const workspacePath = useAppStore((s) => s.workspacePath);
  const activeFile = useAppStore((s) => s.activeFile);
  const workspaceConfig = useAppStore((s) => s.workspaceConfig);
  const setCenterView = useAppStore((s) => s.setCenterView);
  const terminalRequestId = useAppStore((s) => s.terminalRequestId);
  const showConfigPanel = useModelStore((s) => s.showConfigPanel);
  const multiAgentEnabled = useMultiAgentStore((s) => s.multiAgentEnabled);
  const activeCombo = useMultiAgentStore((s) => s.getActiveCombo());
  const systemConfig = useMultiAgentStore((s) => s.systemConfig);
  const startRun = useMultiAgentStore((s) => s.startRun);
  const addRunPhase = useMultiAgentStore((s) => s.addRunPhase);
  const updateRunPhase = useMultiAgentStore((s) => s.updateRunPhase);
  const completeRun = useMultiAgentStore((s) => s.completeRun);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [sessionStats, setSessionStats] = useState(getAgentSession);
  const enhancerConfig = usePromptEnhancerStore((s) => s.config);

  const stt = useSpeechRecognition({
    language: enhancerConfig.sttLanguage,
    continuous: enhancerConfig.sttContinuous,
    onResult: ({ transcript, isFinal }) => {
      if (isFinal) {
        setMessage((prev) => (prev ? `${prev} ${transcript}` : transcript).trim());
      }
    },
  });

  useEffect(() => {
    getOceanAPI().platform.get().then((info) => {
      setPlatformInfo({
        isElectron: info.isElectron,
        isDesktop: info.isDesktop,
        isWeb: info.isWeb,
        isMobile: info.isMobile,
        isAndroid: info.isAndroid ?? false,
        hasNativeShell: info.hasNativeShell ?? info.isElectron,
        hasNativeTerminal: info.hasNativeTerminal ?? false,
        shellLabel: info.shellLabel ?? 'Shell',
        preferredTerminal: info.preferredTerminal ?? resolvePreferredTerminal(info),
      });
    });
  }, []);

  useEffect(() => {
    if (terminalRequestId > 0) {
      const type = platformInfo.preferredTerminal ?? 'shell';
      setTerminalType(type);
      setShowTerminal(true);
    }
  }, [terminalRequestId, platformInfo.preferredTerminal]);

  useEffect(() => {
    const api = getOceanAPI();
    const unsub = api.agent.onEvent((event: unknown) => {
      const e = event as {
        type: string; id: string; timestamp: number; summary: string;
        detail?: string; duration?: number; status?: string;
      };
      const existing = useAppStore.getState().agentLogs.find((l) => l.id === e.id);
      if (existing) {
        updateAgentLog(e.id, {
          summary: e.summary,
          detail: e.detail,
          duration: e.duration,
          status: e.status as AgentLogEntry['status'],
        });
      } else {
        addAgentLog({
          id: e.id,
          type: e.type as AgentLogEntry['type'],
          timestamp: e.timestamp,
          summary: e.summary,
          detail: e.detail,
          duration: e.duration,
          status: e.status as AgentLogEntry['status'],
        });
      }
    });
    return unsub;
  }, [addAgentLog, updateAgentLog]);

  useEffect(() => {
    logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [agentLogs]);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (attachRef.current && !attachRef.current.contains(e.target as Node)) {
        setShowAttachMenu(false);
      }
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowTerminalMenu(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  async function handleSend() {
    if (!message.trim() && attachments.length === 0) return;

    let userMsg = message.trim() || `[${attachments.length} attachment(s)]`;

    const api = getOceanAPI();
    const platform = await api.platform.get();
    const terminalType = platform.preferredTerminal ?? resolvePreferredTerminal(platform);

    const baseContext = await buildAgentContext({
      workspacePath,
      activeFile,
      agentMode: workspaceConfig?.agentMode,
      terminalType,
      attachments: attachments.map((a) => ({ name: a.name, mimeType: a.mimeType, content: a.content })),
    });

    if (enhancerConfig.autoEnhance || enhancerConfig.autoCompress) {
      const { text, result } = await processOutgoingPrompt(
        userMsg,
        enhancerConfig,
        baseContext as unknown as Record<string, unknown>
      );
      userMsg = text;
      if (result.savedTokens > 0 || result.enhanced) {
        addAgentLog({
          id: crypto.randomUUID(),
          type: 'status',
          timestamp: Date.now(),
          summary: `Prompt ${result.enhanced ? 'enhanced' : ''}${result.enhanced && result.compressed ? ' + ' : ''}${result.compressed ? 'compressed' : ''} (~${result.estimatedTokensBefore}→${result.estimatedTokensAfter} tokens)`,
          status: 'completed',
        });
      }
    }

    recordUserMessage(userMsg);
    setSessionStats(getAgentSession());

    addAgentLog({
      id: crypto.randomUUID(),
      type: 'user',
      timestamp: Date.now(),
      summary: userMsg,
    });

    const filesToSend = attachments;
    setMessage('');
    setAttachments([]);
    setAgentRunning(true);

    const agentContext = baseContext;

    if (multiAgentEnabled && activeCombo && activeCombo.members.filter((m) => m.enabled).length > 0) {
      let currentRunId = '';
      try {
        await runMultiAgentWorkflow(
          activeCombo,
          userMsg,
          agentContext as unknown as Record<string, unknown>,
          {
            onLog: (entry) => addAgentLog({
              id: entry.id ?? crypto.randomUUID(),
              type: entry.type as AgentLogEntry['type'],
              timestamp: entry.timestamp ?? Date.now(),
              summary: entry.summary,
              detail: entry.detail,
              status: entry.status as AgentLogEntry['status'],
              metadata: entry.metadata,
            }),
            onAgentSend: async (msg, ctx) => {
              const response = await api.agent.send(msg, { ...ctx, _skipMultiAgentOrchestrator: true });
              return typeof response === 'string' ? response : '';
            },
            maxConcurrentWorkers: systemConfig.maxParallelWorkers,
            onRunStart: (combo, prompt) => {
              currentRunId = startRun({
                comboId: combo.id,
                comboName: combo.name,
                mode: combo.mode,
                prompt,
                agentNames: combo.members.filter((m) => m.enabled).map((m) => m.name),
                currentPhase: combo.mode === 'parallel' ? 'Planning' : 'Collaborative',
              });
              return currentRunId;
            },
            onPhaseStart: (runId, phase) => addRunPhase(runId, phase),
            onPhaseEnd: (runId, phaseId, status) =>
              updateRunPhase(runId, phaseId, { status, completedAt: Date.now() }),
            onRunComplete: (runId, success) => {
              if (runId) completeRun(runId, success ? 'completed' : 'failed');
            },
          },
          systemConfig
        );
      } catch {
        if (currentRunId) completeRun(currentRunId, 'failed');
      }
    } else if (multiAgentEnabled && activeCombo) {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: 'Multi-agent enabled but no active members — enable agents in Multi Agent page',
        status: 'failed',
      });
      await api.agent.send(userMsg, agentContext);
    } else {
      await api.agent.send(userMsg, agentContext);
    }

    setAgentRunning(false);
    setSessionStats(getAgentSession());
  }

  function handleEnhanceDraft() {
    let out = message.trim();
    if (!out) return;
    out = enhancePromptLocally(out, enhancerConfig.enhanceStyle);
    if (enhancerConfig.autoCompress) {
      out = compressPrompt(out, enhancerConfig.compressLevel);
    }
    setMessage(out);
  }

  function handleAskFollowUp(text: string, entryId: string) {
    const excerpt = text.slice(0, 400);
    setMessage(`Follow-up on your previous response:\n\n"${excerpt}${text.length > 400 ? '…' : ''}"\n\n`);
    updateAgentLog(entryId, { taggedForQuestion: true });
  }

  async function handleSpeak(text: string, entryId: string) {
    setSpeakingId(entryId);
    try {
      await speakText(text, {
        voiceUri: enhancerConfig.ttsVoiceUri || undefined,
        rate: enhancerConfig.ttsRate,
        pitch: enhancerConfig.ttsPitch,
        stripMarkdown: enhancerConfig.ttsStripMarkdown,
        provider: enhancerConfig.ttsProvider,
      });
    } catch {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: 'Text-to-speech failed',
        status: 'failed',
      });
    } finally {
      setSpeakingId(null);
    }
  }

  function handleStopSpeak() {
    stopSpeaking();
    setSpeakingId(null);
  }

  function handleNewSession() {
    resetAgentSession();
    setSessionStats(getAgentSession());
  }

  async function handlePause() {
    const api = getOceanAPI();
    await api.agent.pause();
    setAgentRunning(false);
  }

  async function handleUploadFiles() {
    setShowAttachMenu(false);
    const api = getOceanAPI();
    const files = await api.upload.selectFiles();
    setAttachments((prev) => [
      ...prev,
      ...files.map((f: { name: string; mimeType: string; content: string }) => ({ name: f.name, mimeType: f.mimeType, content: f.content })),
    ]);
  }

  async function handleUploadImages() {
    setShowAttachMenu(false);
    const api = getOceanAPI();
    const images = await api.upload.selectImages();
    setAttachments((prev) => [
      ...prev,
      ...images.map((f: { name: string; mimeType: string; content: string }) => ({ name: f.name, mimeType: f.mimeType, content: f.content })),
    ]);
  }

  function openTerminal(type: 'shell' | 'cloud' | 'native') {
    setTerminalType(type);
    setShowTerminal(true);
    setShowTerminalMenu(false);
  }

  async function setupCloudShell() {
    setShowTerminalMenu(false);
    const api = getOceanAPI();
    addAgentLog({
      id: crypto.randomUUID(),
      type: 'action',
      timestamp: Date.now(),
      summary: 'Connecting to Google Cloud Shell',
      status: 'running',
    });
    try {
      const auth = await api.cloudshell.authenticate();
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: `Signed in as ${auth.email || 'Google account'}`,
        status: 'completed',
      });
      const activated = await api.cloudshell.activate();
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'result',
        timestamp: Date.now(),
        summary: activated.message,
        status: 'completed',
      });
      openTerminal('cloud');
    } catch (err) {
      addAgentLog({
        id: crypto.randomUUID(),
        type: 'error',
        timestamp: Date.now(),
        summary: err instanceof Error ? err.message : 'Cloud Shell setup failed',
        status: 'failed',
      });
    }
  }

  const showShell = (platformInfo.isElectron || platformInfo.hasNativeShell) && !platformInfo.hasNativeTerminal;
  const showNative = platformInfo.hasNativeTerminal || platformInfo.isAndroid;
  const showCloud = !platformInfo.hasNativeTerminal;

  if (showTerminal) {
    return (
      <TerminalPanel
        type={terminalType}
        shellLabel={platformInfo.shellLabel}
        onClose={() => setShowTerminal(false)}
      />
    );
  }

  return (
    <div className="agent-panel">
      <div className="agent-header" style={{ position: 'relative' }}>
        <h3>Agent{multiAgentEnabled && activeCombo ? ` · ${activeCombo.name}` : ''}</h3>
        <div className="agent-session-bar">
          <span title="Session ID">{sessionStats.sessionId.slice(-8)}</span>
          <span>{sessionStats.messageCount} msgs</span>
          <span>~{sessionStats.estimatedInputTokens + sessionStats.estimatedOutputTokens} tok</span>
          <button type="button" className="agent-session-reset" onClick={handleNewSession}>New</button>
        </div>
        <div ref={menuRef} style={{ position: 'relative' }}>
          <button className="agent-menu-btn" onClick={() => setShowTerminalMenu(!showTerminalMenu)} title="Terminals">
            <MoreHorizontal size={18} />
          </button>
          {showTerminalMenu && (
            <div className="agent-menu-dropdown">
              {showShell && (
                <button className="agent-menu-item" onClick={() => openTerminal('shell')}>
                  Shell — {platformInfo.shellLabel}
                </button>
              )}
              {!showShell && platformInfo.isWeb && !platformInfo.hasNativeTerminal && (
                <button className="agent-menu-item" disabled style={{ opacity: 0.5, cursor: 'not-allowed' }}>
                  Shell — requires Electron or npm run dev
                </button>
              )}
              {showCloud && (
                <>
                  <button className="agent-menu-item" onClick={setupCloudShell}>
                    Setup Cloud Shell
                  </button>
                  <button className="agent-menu-item" onClick={() => openTerminal('cloud')}>
                    Cloud Terminal
                  </button>
                </>
              )}
              {showNative && (
                <button className="agent-menu-item" onClick={() => openTerminal('native')}>
                  Native Terminal — Linux on device
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      <ModelSelector />
      <AgentTimeoutBar />
      {multiAgentEnabled && activeCombo && (
        <button
          className="ma-agent-team-bar"
          onClick={() => setCenterView('multiagent')}
          type="button"
        >
          <Users size={12} />
          <span>{activeCombo.name}</span>
          <span className="ma-agent-team-mode">{activeCombo.mode}</span>
          <span className="ma-agent-team-count">{activeCombo.members.filter((m) => m.enabled).length} agents</span>
        </button>
      )}
      <AnimatePresence>
        {showConfigPanel && (
          <ModelConfigPanel platformInfo={platformInfo} />
        )}
      </AnimatePresence>

      <div className="agent-logs">
        {agentLogs.length === 0 && (
          <div style={{ color: 'var(--text-tertiary)', fontSize: '0.8125rem', padding: '20px 0' }}>
            Ask the agent to help with your code, run commands, or manage your workspace.
          </div>
        )}
        {agentLogs.map((entry) => (
          <AgentLogItem
            key={entry.id}
            entry={entry}
            speakingId={speakingId}
            onAsk={handleAskFollowUp}
            onSpeak={handleSpeak}
            onStopSpeak={handleStopSpeak}
          />
        ))}
        <div ref={logsEndRef} />
      </div>

      {attachments.length > 0 && (
        <div className="agent-attachments">
          {attachments.map((a, i) => (
            <div key={i} className="agent-attachment-chip">
              {a.name}
              <button onClick={() => setAttachments((prev) => prev.filter((_, j) => j !== i))}>
                <X size={12} />
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="agent-input-area">
        {agentRunning && (
          <button className="agent-pause-btn" onClick={handlePause}>
            <Pause size={12} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
            Pause session
          </button>
        )}
        <div className="agent-input-wrapper rim-glow">
          <textarea
            className="agent-input"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="Ask the agent anything..."
            rows={1}
          />
          <div className="agent-input-actions">
            <div ref={attachRef} style={{ position: 'relative' }}>
              <button className="agent-attach-btn" onClick={() => setShowAttachMenu(!showAttachMenu)}>
                <Plus size={16} />
              </button>
              {showAttachMenu && (
                <div className="agent-attach-menu">
                  <button className="agent-attach-item" onClick={handleUploadImages}>
                    <Image size={14} /> Upload photo
                  </button>
                  <button className="agent-attach-item" onClick={handleUploadFiles}>
                    <FileUp size={14} /> Upload file
                  </button>
                  <button className="agent-attach-item" onClick={() => { setShowAttachMenu(false); setCenterView('mcp'); }}>
                    <Plug size={14} /> MCP connectors
                  </button>
                  <button className="agent-attach-item" onClick={() => { setShowAttachMenu(false); setCenterView('plugins'); }}>
                    <Webhook size={14} /> Webhooks
                  </button>
                  <button className="agent-attach-item" onClick={() => { setShowAttachMenu(false); setCenterView('plugins'); }}>
                    <Puzzle size={14} /> Plugins
                  </button>
                </div>
              )}
            </div>
            {stt.supported && (
              <button
                type="button"
                className={`agent-mic-btn ${stt.listening ? 'listening' : ''}`}
                onClick={stt.toggle}
                title={stt.listening ? 'Stop listening' : 'Speech to text'}
              >
                {stt.listening ? <MicOff size={16} /> : <Mic size={16} />}
              </button>
            )}
            <button
              type="button"
              className="agent-enhance-btn"
              onClick={handleEnhanceDraft}
              disabled={!message.trim()}
              title="Enhance prompt"
            >
              <Sparkles size={16} />
            </button>
            {message.trim() && (
              <span className="agent-token-hint">~{estimateTokens(message)} tok</span>
            )}
            <button
              className="agent-send-btn"
              onClick={handleSend}
              disabled={!message.trim() && attachments.length === 0}
            >
              <Send size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
