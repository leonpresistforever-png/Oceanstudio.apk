import { useEffect, useRef, useState } from 'react';
import { X, Upload, Monitor, Cloud, ExternalLink } from 'lucide-react';
import { buildCloudShellProxyUrl } from '../../lib/cloudShellPreview';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import { WebLinksAddon } from '@xterm/addon-web-links';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';
import { onNativeSetupProgress } from '../../lib/capacitorNativeTerminal';
import '@xterm/xterm/css/xterm.css';
import './TerminalPanel.css';

interface Props {
  type: 'shell' | 'cloud' | 'native';
  shellLabel?: string;
  onClose: () => void;
}

const TYPE_LABELS: Record<string, string> = {
  shell: 'Shell',
  cloud: 'Cloud Terminal',
  native: 'Native Terminal',
};

export default function TerminalPanel({ type, shellLabel, onClose }: Props) {
  const terminalRef = useRef<HTMLDivElement>(null);
  const xtermRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const terminalId = useRef(`terminal-${type}-${Date.now()}`);
  const [platformInfo, setPlatformInfo] = useState({ isMobile: false, isWeb: false, isElectron: false, isAndroid: false });
  const [setupMessage, setSetupMessage] = useState('');
  const [settingUp, setSettingUp] = useState(false);
  const [error, setError] = useState('');
  const [activePort, setActivePort] = useState<number | null>(null);
  const [activePortUrl, setActivePortUrl] = useState<string | null>(null);
  const [activePortOpenUrl, setActivePortOpenUrl] = useState<string | null>(null);
  const [isCloudPort, setIsCloudPort] = useState(false);
  const [manualPort, setManualPort] = useState('');
  const setPreviewPort = useAppStore((s) => s.setPreviewPort);
  const setPreviewUrl = useAppStore((s) => s.setPreviewUrl);
  const setCenterView = useAppStore((s) => s.setCenterView);
  const workspacePath = useAppStore((s) => s.workspacePath);

  const title = type === 'shell' && shellLabel ? `${TYPE_LABELS.shell} (${shellLabel})` : TYPE_LABELS[type];

  useEffect(() => {
    getOceanAPI().platform.get().then((info) => setPlatformInfo({
      isMobile: info.isMobile,
      isWeb: info.isWeb,
      isElectron: info.isElectron,
      isAndroid: info.isAndroid ?? false,
    }));
  }, []);

  useEffect(() => {
    if (type !== 'native') return;
    return onNativeSetupProgress((message) => {
      setSetupMessage(message);
      setSettingUp(true);
      if (message.toLowerCase().includes('ready') || message.toLowerCase().includes('installed')) {
        setSettingUp(false);
      }
    });
  }, [type]);

  useEffect(() => {
    if (!terminalRef.current) return;
    let disposed = false;

    const term = new Terminal({
      fontFamily: 'JetBrains Mono, monospace',
      fontSize: 13,
      theme: {
        background: '#1C1917',
        foreground: '#E7E5E4',
        cursor: '#FAFAF9',
        selectionBackground: '#44403C',
      },
      cursorBlink: true,
      scrollback: 5000,
    });

    const fitAddon = new FitAddon();
    term.loadAddon(fitAddon);
    term.loadAddon(new WebLinksAddon());
    term.open(terminalRef.current);
    fitAddon.fit();

    xtermRef.current = term;
    fitAddonRef.current = fitAddon;

    const api = getOceanAPI();

    async function init() {
      try {
        if (type === 'native') {
          const apiWithSetup = api as { nativeSetup?: { getStatus: () => Promise<{ ready: boolean; message: string }>; setup: () => Promise<{ ready: boolean; message: string }> } };
          if (apiWithSetup.nativeSetup) {
            setSettingUp(true);
            term.writeln('\x1b[36mSetting up Ocean Linux environment...\x1b[0m');
            const status = await apiWithSetup.nativeSetup.getStatus();
            if (!status.ready) {
              const result = await apiWithSetup.nativeSetup.setup();
              setSetupMessage(result.message);
              term.writeln(`\x1b[32m${result.message}\x1b[0m\r\n`);
            } else {
              setSetupMessage(status.message);
            }
            setSettingUp(false);
          }
        }
        await api.terminal.create({
          id: terminalId.current,
          type,
          cwd: workspacePath || undefined,
        });
        if (disposed) return;
        if (type === 'cloud') {
          term.writeln('\x1b[90mCloud Terminal — ports run in Google sandbox (not localhost)\x1b[0m');
          term.writeln('\x1b[90mPreview: https://$PORT-$WEB_HOST or use Register Port in the toolbar\x1b[0m\r\n');
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Failed to start terminal';
        setError(msg);
        term.writeln(`\x1b[31m${msg}\x1b[0m`);
        if (type === 'native') {
          term.writeln('\x1b[90mNative terminal requires the Ocean.studio Android APK.\x1b[0m');
        } else if (platformInfo.isAndroid) {
          term.writeln('\x1b[90mOpen Native Terminal from the agent menu.\x1b[0m');
        } else {
          term.writeln('\x1b[90mOn Windows: run npm run electron:dev for full shell access.\x1b[0m');
          term.writeln('\x1b[90mFor browser preview: run npm run dev (starts terminal server).\x1b[0m');
        }
      }
    }

    init();

    const unsubData = api.terminal.onData((id: string, data: string) => {
      if (id === terminalId.current) term.write(data);
    });

    const unsubExit = api.terminal.onExit((id: string, code: number) => {
      if (id === terminalId.current) {
        term.write(`\r\n\x1b[90m[Process exited with code ${code}]\x1b[0m\r\n`);
      }
    });

    const unsubPort = api.preview.onPortDetected((port: number, url: string, meta) => {
      setActivePort(port);
      setActivePortUrl(url);
      setActivePortOpenUrl(meta?.openUrl ?? url);
      setIsCloudPort(meta?.source === 'cloudshell' || type === 'cloud');
      setPreviewPort(port);
      setPreviewUrl(url);
    });

    term.onData((data) => {
      api.terminal.write(terminalId.current, data);
    });

    const resizeObserver = new ResizeObserver(() => {
      fitAddon.fit();
      api.terminal.resize(terminalId.current, term.cols, term.rows);
    });
    resizeObserver.observe(terminalRef.current);

    return () => {
      disposed = true;
      unsubData();
      unsubExit();
      unsubPort();
      resizeObserver.disconnect();
      api.terminal.kill(terminalId.current);
      term.dispose();
    };
  }, [type, workspacePath, setPreviewPort, setPreviewUrl]);

  function handleClear() {
    getOceanAPI().terminal.clear(terminalId.current);
    xtermRef.current?.clear();
  }

  function handleRestart() {
    getOceanAPI().terminal.restart(terminalId.current);
    xtermRef.current?.clear();
    setError('');
  }

  async function handleUpload() {
    const api = getOceanAPI();
    const files = await api.upload.selectFiles();
    if (files.length > 0) {
      xtermRef.current?.write(`\r\n\x1b[33mUploaded: ${files.map((f: { name: string }) => f.name).join(', ')}\x1b[0m\r\n`);
    }
  }

  function handleViewPort() {
    if (activePort) {
      setPreviewPort(activePort);
      if (activePortUrl) setPreviewUrl(activePortUrl);
      setCenterView('preview');
    }
  }

  async function handleRegisterCloudPort() {
    const port = parseInt(manualPort, 10);
    if (!port || port < 2000 || port > 65000) return;
    const api = getOceanAPI();
    try {
      if (api.cloudshell?.registerPort) {
        const preview = await api.cloudshell.registerPort(port);
        setActivePort(preview.port);
        setActivePortUrl(preview.url);
        setActivePortOpenUrl(preview.openUrl);
        setIsCloudPort(true);
        setPreviewPort(preview.port);
        setPreviewUrl(preview.url);
      } else if (api.preview.registerCloudShellPort) {
        const preview = await api.preview.registerCloudShellPort(port);
        setActivePort(preview.port);
        setActivePortUrl(preview.url);
        setActivePortOpenUrl(preview.openUrl);
        setIsCloudPort(true);
        setPreviewPort(preview.port);
        setPreviewUrl(preview.url);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to register port';
      xtermRef.current?.write(`\r\n\x1b[31m${msg}\x1b[0m\r\n`);
    }
  }

  async function handleOpenInCloudShell() {
    const url = activePortOpenUrl ?? (activePort ? buildCloudShellProxyUrl(activePort) : null);
    if (url) await getOceanAPI().shell.openExternal(url);
  }

  const showMobileActions = platformInfo.isMobile || platformInfo.isWeb || type === 'cloud';

  return (
    <div className="terminal-panel">
      <div className="terminal-header">
        <div className="terminal-header-left">
          <span className="terminal-title">{title}</span>
          <span className="terminal-type-badge">{type}</span>
        </div>
        <div className="terminal-actions">
          {type === 'cloud' && (
            <>
              <input
                type="number"
                placeholder="Port"
                value={manualPort}
                onChange={(e) => setManualPort(e.target.value)}
                style={{
                  width: '64px', padding: '2px 6px', fontSize: '0.7rem',
                  background: 'var(--bg-secondary)', border: '1px solid var(--border)', borderRadius: '4px',
                  color: 'var(--text-primary)',
                }}
              />
              <button className="terminal-action-btn" onClick={handleRegisterCloudPort} title="Register Cloud Shell port">
                <Cloud size={12} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
                Register
              </button>
            </>
          )}
          {activePort && (
            <button className="terminal-action-btn" onClick={handleViewPort} title="View port in preview">
              <Monitor size={12} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
              :{activePort}
            </button>
          )}
          {activePort && isCloudPort && (
            <button className="terminal-action-btn" onClick={handleOpenInCloudShell} title="Open in Google Cloud Shell browser">
              <ExternalLink size={12} style={{ display: 'inline', marginRight: 4, verticalAlign: 'middle' }} />
              Open
            </button>
          )}
          <button className="terminal-action-btn" onClick={handleClear}>Clear</button>
          <button className="terminal-action-btn" onClick={handleRestart}>Restart</button>
          <button className="terminal-close-btn" onClick={onClose}>
            <X size={16} />
          </button>
        </div>
      </div>

      {setupMessage && !error && (
        <div style={{ padding: '6px 12px', fontSize: '0.75rem', color: '#86EFAC', background: '#292524' }}>
          {settingUp ? 'Setting up...' : setupMessage}
        </div>
      )}

      {error && (
        <div style={{ padding: '6px 12px', fontSize: '0.75rem', color: '#FCA5A5', background: '#292524' }}>
          {error}
        </div>
      )}

      <div className="terminal-body" ref={terminalRef} />

      {showMobileActions && (
        <div className="terminal-mobile-actions">
          <button className="terminal-mobile-btn" onClick={handleClear}>Clear</button>
          <button className="terminal-mobile-btn" onClick={handleRestart}>Restart</button>
          {activePort && (
            <button className="terminal-mobile-btn" onClick={handleViewPort}>View :{activePort}</button>
          )}
          <button className="terminal-mobile-btn" onClick={() => {
            getOceanAPI().terminal.write(terminalId.current, '\x03');
          }}>Ctrl+C</button>
          <button className="terminal-mobile-btn" onClick={() => {
            getOceanAPI().terminal.write(terminalId.current, '\x1b[A');
          }}>Up</button>
          <button className="terminal-mobile-btn" onClick={() => {
            getOceanAPI().terminal.write(terminalId.current, '\t');
          }}>Tab</button>
          <button className="terminal-upload-btn" onClick={handleUpload}>
            <Upload size={12} /> Upload file
          </button>
        </div>
      )}
    </div>
  );
}
