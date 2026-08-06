import { useState, useEffect, useCallback } from 'react';
import {
  Video, Monitor, Smartphone, Globe, AlertTriangle, Play, Square, Pause,
  HardDrive, Cpu, Loader2, Bell, FolderOpen, Mic, Settings2,
} from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import { useRecordingStore } from '../../store/recordingStore';
import {
  ELECTRON_RECORDING_FEATURES,
  ANDROID_RECORDING_FEATURES,
  RESOLUTION_DIMS,
  type RecordingFps,
  type RecordingBitrateMbps,
  type RecordingResolution,
  type RecordingCaptureMode,
  type RecordingAudioSource,
  type RecordingOutputFormat,
  type RecordingCodec,
} from '../../playground/recordingTypes';

function formatElapsed(ms: number): string {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const h = Math.floor(m / 60);
  if (h > 0) return `${h}:${String(m % 60).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  return `${m}:${String(s % 60).padStart(2, '0')}`;
}

export default function RecordingPanel() {
  const config = useRecordingStore((s) => s.config);
  const hardware = useRecordingStore((s) => s.hardware);
  const sources = useRecordingStore((s) => s.sources);
  const status = useRecordingStore((s) => s.status);
  const elapsedMs = useRecordingStore((s) => s.elapsedMs);
  const outputPath = useRecordingStore((s) => s.outputPath);
  const message = useRecordingStore((s) => s.message);
  const selectedSourceId = useRecordingStore((s) => s.selectedSourceId);
  const setConfig = useRecordingStore((s) => s.setConfig);
  const setHardware = useRecordingStore((s) => s.setHardware);
  const setSources = useRecordingStore((s) => s.setSources);
  const setStatus = useRecordingStore((s) => s.setStatus);
  const setSelectedSource = useRecordingStore((s) => s.setSelectedSource);

  const [loading, setLoading] = useState(false);
  const [platform, setPlatform] = useState<'electron' | 'android' | 'web'>('web');
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [countdown, setCountdown] = useState<number | null>(null);

  const loadHardware = useCallback(async () => {
    const api = getOceanAPI();
    const info = await api.platform.get();
    const p = info.isElectron ? 'electron' : info.isAndroid ? 'android' : 'web';
    setPlatform(p);
    if (api.recording?.getHardwareInfo) {
      const hw = await api.recording.getHardwareInfo() as unknown as typeof hardware;
      setHardware(hw);
    }
    if (p === 'electron' && api.recording?.getSources) {
      const src = await api.recording.getSources();
      setSources(src as typeof sources);
      if (src[0] && !selectedSourceId) setSelectedSource(src[0].id);
    }
  }, [setHardware, setSources, setSelectedSource, selectedSourceId]);

  useEffect(() => {
    void loadHardware();
    const api = getOceanAPI();
    const unsub = api.recording?.onStatus?.((s) => {
      const st = s as { status?: string; elapsedMs?: number; outputPath?: string; countdown?: number };
      if (st.status) {
        setStatus(st.status as typeof status, { elapsedMs: st.elapsedMs, outputPath: st.outputPath });
        if (st.status === 'recording') setCountdown(null);
      }
      if (st.countdown !== undefined) setCountdown(st.countdown > 0 ? st.countdown : null);
    });
    return () => unsub?.();
  }, [loadHardware, setStatus]);

  useEffect(() => {
    if (status !== 'recording') return;
    const t = setInterval(() => {
      useRecordingStore.setState((s) => ({ elapsedMs: s.elapsedMs + 1000 }));
    }, 1000);
    return () => clearInterval(t);
  }, [status]);

  const dims = RESOLUTION_DIMS[config.resolution];
  const isRecording = status === 'recording';
  const isPaused = status === 'paused';
  const canRecord = platform !== 'web';

  async function handleStart() {
    setLoading(true);
    try {
      const api = getOceanAPI();
      const startConfig = {
        ...config,
        sourceId: selectedSourceId ?? config.sourceId,
      };
      const res = await api.recording?.start?.(startConfig as unknown as Record<string, unknown>, selectedSourceId ?? undefined);
      if (res?.ok) {
        setStatus('recording', { elapsedMs: 0, message: res.message });
      } else {
        setStatus('error', { message: res?.message ?? 'Failed to start' });
      }
    } finally {
      setLoading(false);
    }
  }

  async function handlePause() {
    const api = getOceanAPI();
    const res = isPaused ? await api.recording?.resume?.() : await api.recording?.pause?.();
    if (res?.ok) {
      const st = await api.recording?.getStatus?.() as { elapsedMs?: number } | undefined;
      setStatus(isPaused ? 'recording' : 'paused', {
        message: res.message,
        elapsedMs: st?.elapsedMs,
      });
    }
  }

  async function handleStop() {
    setLoading(true);
    try {
      const api = getOceanAPI();
      const res = await api.recording?.stop?.();
      if (res?.ok) setStatus('idle', { outputPath: res.path, message: res.message, elapsedMs: 0 });
    } finally {
      setLoading(false);
    }
  }

  async function handleCancel() {
    const api = getOceanAPI();
    const res = await api.recording?.cancel?.();
    if (res?.ok) {
      setStatus('idle', { message: res.message, elapsedMs: 0 });
      setCountdown(null);
    }
  }

  const canCancel = status !== 'idle' || countdown !== null;

  const PlatformIcon = platform === 'electron' ? Monitor : platform === 'android' ? Smartphone : Globe;
  const features = platform === 'electron' ? ELECTRON_RECORDING_FEATURES : platform === 'android' ? ANDROID_RECORDING_FEATURES : [];

  return (
    <div className="pg-recording">
      {platform === 'web' && (
        <div className="pg-rec-web-warn">
          <Globe size={18} />
          <p>Screen recording is not available in the web browser. Use <strong>Electron (EXE)</strong> or <strong>Android APK</strong>.</p>
        </div>
      )}

      <div className={`pg-rec-hw pg-rec-hw-${platform}`}>
        <PlatformIcon size={22} />
        <div>
          <strong>{platform === 'electron' ? 'Desktop Recording' : platform === 'android' ? 'Android Recording' : 'Web (unavailable)'}</strong>
          {hardware && <p>{hardware.gpuName} · {hardware.encoder}</p>}
        </div>
        {hardware?.gpuBypass && <span className="pg-rec-bypass">HW Bypass</span>}
      </div>

      {hardware && (
        <div className="pg-rec-hw-stats">
          <span><Cpu size={12} /> Max {hardware.maxResolution.toUpperCase()} @ {hardware.maxFps}fps</span>
          <span><HardDrive size={12} /> {hardware.displays.length} display(s)</span>
        </div>
      )}

      {/* Manual start — prominent control */}
      <div className="pg-rec-manual-start">
        <Video size={20} />
        <div>
          <strong>Manual Recording</strong>
          <p>Configure below, then press Record. App can run in background while capturing.</p>
        </div>
        <button className="pg-rec-btn start pg-rec-manual-btn" onClick={() => void handleStart()} disabled={!canRecord || isRecording || loading}>
          {loading ? <Loader2 size={18} className="pg-spin" /> : <Play size={18} />}
          {countdown !== null ? `Starting in ${countdown}s...` : 'Start Recording'}
        </button>
      </div>

      <div className="pg-rec-config">
        <h4><Settings2 size={14} /> Video Quality</h4>
        <div className="pg-rec-grid">
          <label>
            <span>Resolution</span>
            <select value={config.resolution} onChange={(e) => setConfig({ resolution: e.target.value as RecordingResolution })} disabled={!canRecord}>
              {(['4k', '2k', '1080p', '720p'] as const).map((r) => (
                <option key={r} value={r}>{r.toUpperCase()} ({RESOLUTION_DIMS[r].width}×{RESOLUTION_DIMS[r].height})</option>
              ))}
            </select>
          </label>
          <label>
            <span>FPS</span>
            <select value={config.fps} onChange={(e) => setConfig({ fps: Number(e.target.value) as RecordingFps })} disabled={!canRecord}>
              {([30, 60, 90, 120, 144, 165] as const).map((f) => (
                <option key={f} value={f}>{f} fps</option>
              ))}
            </select>
          </label>
          <label>
            <span>Bitrate</span>
            <select value={config.bitrateMbps} onChange={(e) => setConfig({ bitrateMbps: Number(e.target.value) as RecordingBitrateMbps })} disabled={!canRecord}>
              {([30, 60, 80, 100, 150] as const).map((b) => (
                <option key={b} value={b}>{b} Mbps</option>
              ))}
            </select>
          </label>
          <label>
            <span>Codec / Format</span>
            <div className="pg-rec-inline">
              <select value={config.codec} onChange={(e) => setConfig({ codec: e.target.value as RecordingCodec })} disabled={!canRecord}>
                <option value="h264">H.264</option>
                <option value="hevc">HEVC</option>
                <option value="vp9">VP9</option>
              </select>
              <select value={config.outputFormat} onChange={(e) => setConfig({ outputFormat: e.target.value as RecordingOutputFormat })} disabled={!canRecord}>
                <option value="webm">WebM</option>
                <option value="mp4">MP4</option>
                <option value="mkv">MKV</option>
              </select>
            </div>
          </label>
          <label>
            <span>Capture mode</span>
            <select value={config.captureMode} onChange={(e) => setConfig({ captureMode: e.target.value as RecordingCaptureMode })} disabled={!canRecord}>
              <option value="fullscreen">Full screen</option>
              <option value="window">Specific app / window</option>
              <option value="region">Partial / selected area</option>
            </select>
          </label>
          <label>
            <span>Audio</span>
            <select value={config.audioSource} onChange={(e) => setConfig({ audioSource: e.target.value as RecordingAudioSource })} disabled={!canRecord}>
              <option value="both">System + Microphone</option>
              <option value="system">System only</option>
              <option value="mic">Microphone only</option>
              <option value="none">No audio</option>
            </select>
          </label>
        </div>

        <div className="pg-rec-toggles">
          <label><input type="checkbox" checked={config.ultraRender} onChange={(e) => setConfig({ ultraRender: e.target.checked })} disabled={!canRecord} /> Ultra render (crystal sharp)</label>
          <label><input type="checkbox" checked={config.hardwareBypass} onChange={(e) => setConfig({ hardwareBypass: e.target.checked })} disabled={!canRecord} /> GPU hardware encoder bypass</label>
          <label><input type="checkbox" checked={config.backgroundMode} onChange={(e) => setConfig({ backgroundMode: e.target.checked })} disabled={!canRecord} /> Background recording (app stays alive)</label>
          <label><input type="checkbox" checked={config.minimizeToTray} onChange={(e) => setConfig({ minimizeToTray: e.target.checked })} disabled={!canRecord} /> Minimize to tray while recording</label>
          <label><input type="checkbox" checked={config.keepAliveInBackground} onChange={(e) => setConfig({ keepAliveInBackground: e.target.checked })} disabled={!canRecord} /> Keep process alive in background</label>
          <label><input type="checkbox" checked={config.cursorHighlight} onChange={(e) => setConfig({ cursorHighlight: e.target.checked })} disabled={!canRecord} /> Cursor highlight overlay</label>
          <label><input type="checkbox" checked={config.clickRipple} onChange={(e) => setConfig({ clickRipple: e.target.checked })} disabled={!canRecord} /> Click ripple effect</label>
          <label><input type="checkbox" checked={config.showCountdown} onChange={(e) => setConfig({ showCountdown: e.target.checked })} disabled={!canRecord} /> Show countdown before start</label>
          {platform === 'android' && (
            <>
              <label><input type="checkbox" checked={config.saveToGallery} onChange={(e) => setConfig({ saveToGallery: e.target.checked })} /> Save to Gallery / Movies</label>
              <label><input type="checkbox" checked={config.notificationControls} onChange={(e) => setConfig({ notificationControls: e.target.checked })} /> <Bell size={12} style={{ display: 'inline' }} /> Notification panel controls</label>
            </>
          )}
        </div>

        <button type="button" className="pg-rec-advanced-toggle" onClick={() => setShowAdvanced(!showAdvanced)}>
          {showAdvanced ? 'Hide' : 'Show'} advanced output settings
        </button>

        {showAdvanced && (
          <div className="pg-rec-grid pg-rec-advanced">
            <label>
              <span>Start delay (seconds)</span>
              <input type="number" min={0} max={30} value={config.startDelaySec} onChange={(e) => setConfig({ startDelaySec: Number(e.target.value) })} disabled={!canRecord} />
            </label>
            <label>
              <span>Auto-stop (minutes, 0=off)</span>
              <input type="number" min={0} max={480} value={config.autoStopMinutes} onChange={(e) => setConfig({ autoStopMinutes: Number(e.target.value) })} disabled={!canRecord} />
            </label>
            <label>
              <span>Mic gain %</span>
              <input type="number" min={0} max={200} value={config.micGain} onChange={(e) => setConfig({ micGain: Number(e.target.value) })} disabled={!canRecord} />
            </label>
            <label className="pg-rec-full">
              <span>Output folder (empty = default Videos/Ocean.studio)</span>
              <input value={config.outputFolder} placeholder="/path/to/save" onChange={(e) => setConfig({ outputFolder: e.target.value })} disabled={!canRecord} />
            </label>
            <label className="pg-rec-full">
              <span>Filename template</span>
              <input value={config.filenameTemplate} onChange={(e) => setConfig({ filenameTemplate: e.target.value })} disabled={!canRecord} />
            </label>
            {config.captureMode === 'region' && config.region && (
              <>
                <label><span>Region X</span><input type="number" value={config.region.x} onChange={(e) => setConfig({ region: { ...config.region!, x: Number(e.target.value) } })} /></label>
                <label><span>Region Y</span><input type="number" value={config.region.y} onChange={(e) => setConfig({ region: { ...config.region!, y: Number(e.target.value) } })} /></label>
                <label><span>Width</span><input type="number" value={config.region.width} onChange={(e) => setConfig({ region: { ...config.region!, width: Number(e.target.value) } })} /></label>
                <label><span>Height</span><input type="number" value={config.region.height} onChange={(e) => setConfig({ region: { ...config.region!, height: Number(e.target.value) } })} /></label>
              </>
            )}
          </div>
        )}
      </div>

      {platform === 'electron' && sources.length > 0 && config.captureMode !== 'fullscreen' && (
        <div className="pg-rec-sources">
          <h4>Source picker</h4>
          <div className="pg-rec-source-grid">
            {sources.slice(0, 12).map((s) => (
              <button key={s.id} type="button" className={`pg-rec-source ${selectedSourceId === s.id ? 'active' : ''}`} onClick={() => setSelectedSource(s.id)}>
                {s.thumbnail && <img src={s.thumbnail} alt="" />}
                <span>{s.name.slice(0, 40)}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {platform === 'android' && config.notificationControls && (
        <div className="pg-rec-notif-panel">
          <Bell size={16} />
          <div>
            <strong>Notification Remote Panel</strong>
            <p>Timer, pause, stop, and save in the notification shade — record in background with no overlay ball.</p>
          </div>
        </div>
      )}

      <div className="pg-rec-status-bar">
        <Mic size={20} />
        <div>
          <strong>{isRecording ? 'RECORDING' : isPaused ? 'PAUSED' : 'Ready'}</strong>
          {(isRecording || isPaused) && <span className="pg-rec-timer">{formatElapsed(elapsedMs)}</span>}
          <small>{dims.width}×{dims.height} · {config.fps}fps · {config.bitrateMbps}Mbps · {config.codec}/{config.outputFormat}</small>
          {config.backgroundMode && isRecording && <small className="pg-rec-bg-hint">Background mode active — app can minimize</small>}
        </div>
        <div className="pg-rec-controls">
          <button className="pg-rec-btn start" onClick={() => void handleStart()} disabled={!canRecord || isRecording || loading}>
            {loading ? <Loader2 size={16} className="pg-spin" /> : <Play size={16} />}
            Record
          </button>
          <button className="pg-rec-btn" onClick={() => void handlePause()} disabled={!isRecording && !isPaused}><Pause size={16} /></button>
          <button className="pg-rec-btn stop" onClick={() => void handleStop()} disabled={!isRecording && !isPaused}><Square size={16} /> Save</button>
          <button className="pg-rec-btn cancel" onClick={() => void handleCancel()} disabled={!canCancel}>Cancel</button>
        </div>
      </div>

      {message && <div className="pg-rec-message"><AlertTriangle size={14} />{message}</div>}
      {outputPath && <div className="pg-rec-saved"><FolderOpen size={14} />Saved: {outputPath}</div>}

      {features.length > 0 && (
        <section className="pg-rec-features">
          <h4>{platform === 'electron' ? 'Desktop' : 'Android'} Features</h4>
          <ul>{features.map((f) => <li key={f}>{f}</li>)}</ul>
        </section>
      )}
    </div>
  );
}
