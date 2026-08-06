import { useState, useEffect, useCallback } from 'react';
import { Monitor, Smartphone, Cloud, Cpu, Key, Loader2, Box } from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import {
  get3DSourcesForPlatform,
  ELECTRON_3D_SOURCES,
  ANDROID_CLOUD_3D_SOURCES,
  type ThreeDMode,
  type ThreeDSource,
} from '../../playground/threeDStudio';
import { usePlaygroundStore } from '../../store/playgroundStore';

const MODES: { id: ThreeDMode; label: string }[] = [
  { id: 'text-to-3d', label: 'Text → 3D' },
  { id: 'image-to-3d', label: 'Image → 3D' },
  { id: '2d-to-3d', label: '2D → 3D' },
];

export default function ThreeDStudioPanel() {
  const selected3DSourceId = usePlaygroundStore((s) => s.selected3DSourceId);
  const setSelected3DSource = usePlaygroundStore((s) => s.setSelected3DSource);
  const setPrompt = usePlaygroundStore((s) => s.setPrompt);
  const setActiveTool = usePlaygroundStore((s) => s.setActiveTool);

  const [platform, setPlatform] = useState<'electron' | 'android' | 'web'>('web');
  const [mode, setMode] = useState<ThreeDMode>('text-to-3d');
  const [loading, setLoading] = useState(true);

  const loadPlatform = useCallback(async () => {
    setLoading(true);
    try {
      const api = getOceanAPI();
      const info = await api.platform.get();
      const p = info.isElectron ? 'electron' : info.isAndroid ? 'android' : 'web';
      setPlatform(p);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadPlatform();
  }, [loadPlatform]);

  const sources = get3DSourcesForPlatform(platform).filter(
    (s) => s.mode === 'all' || s.mode === mode
  );

  const localSources = sources.filter((s) => s.platforms.includes('electron') && s.kind === 'opensource');
  const cloudSources = sources.filter((s) => s.kind === 'cloud' || s.platforms.includes('cloud') || s.platforms.includes('android'));

  function selectSource(source: ThreeDSource) {
    setSelected3DSource(source.id);
    setActiveTool(`3d-${source.id}`);
    const modeLabel = mode === 'text-to-3d' ? 'Generate 3D from text' : mode === 'image-to-3d' ? 'Convert image to 3D' : 'Convert 2D art to 3D';
    setPrompt(`${modeLabel} using ${source.name}: `);
  }

  if (loading) {
    return (
      <section className="pg-3d-studio">
        <div className="pg-3d-loading"><Loader2 size={20} className="pg-spin" /> Detecting platform...</div>
      </section>
    );
  }

  return (
    <section className="pg-3d-studio">
      <div className="pg-3d-platform-banner">
        {platform === 'electron' && (
          <>
            <Monitor size={16} />
            <strong>Electron EXE</strong>
            <span>— Open-source local models on your GPU + optional cloud APIs</span>
          </>
        )}
        {platform === 'android' && (
          <>
            <Smartphone size={16} />
            <strong>Android APK</strong>
            <span>— Local 3D inference not available; cloud APIs with free tiers handle heavy GPU work</span>
          </>
        )}
        {platform === 'web' && (
          <>
            <Cloud size={16} />
            <strong>Web Preview</strong>
            <span>— Cloud 3D APIs only (free-tier sources listed below)</span>
          </>
        )}
      </div>

      <div className="pg-3d-stats">
        <span><Box size={14} /> {ELECTRON_3D_SOURCES.length} open-source (EXE)</span>
        <span><Cloud size={14} /> {ANDROID_CLOUD_3D_SOURCES.length} cloud free-tier (APK)</span>
      </div>

      <div className="pg-3d-modes">
        {MODES.map((m) => (
          <button
            key={m.id}
            className={`pg-3d-mode-btn ${mode === m.id ? 'active' : ''}`}
            onClick={() => setMode(m.id)}
          >
            {m.label}
          </button>
        ))}
      </div>

      {platform === 'electron' && localSources.length > 0 && (
        <div className="pg-3d-section">
          <h4><Cpu size={14} /> Open-Source Local Models (EXE)</h4>
          <p className="pg-3d-section-desc">Runs on your GPU — TripoSR, InstantMesh, Shap-E, TRELLIS, Blender, and more.</p>
          <div className="pg-3d-grid">
            {localSources.map((s) => (
              <SourceCard key={s.id} source={s} selected={selected3DSourceId === s.id} onSelect={selectSource} />
            ))}
          </div>
        </div>
      )}

      <div className="pg-3d-section">
        <h4><Cloud size={14} /> {platform === 'android' ? 'Cloud APIs (APK — required)' : 'Cloud APIs (optional)'}</h4>
        <p className="pg-3d-section-desc">
          {platform === 'android'
            ? 'Heavy 3D generation runs in the cloud. All sources below offer free tiers.'
            : 'Offload to Meshy, Tripo, Luma, Replicate, Fal, HuggingFace, and more.'}
        </p>
        <div className="pg-3d-grid">
          {cloudSources.map((s) => (
            <SourceCard key={s.id} source={s} selected={selected3DSourceId === s.id} onSelect={selectSource} />
          ))}
        </div>
      </div>

      {platform === 'android' && (
        <div className="pg-3d-apk-note">
          <Key size={14} />
          <p>
            APK cannot run local CUDA/PyTorch 3D models. Add API keys in <strong>Providers</strong> or
            connect MCP servers (Meshy, Tripo, Replicate) — results display in the Playground viewer.
          </p>
        </div>
      )}
    </section>
  );
}

function SourceCard({
  source,
  selected,
  onSelect,
}: {
  source: ThreeDSource;
  selected: boolean;
  onSelect: (s: ThreeDSource) => void;
}) {
  return (
    <button
      className={`pg-3d-card ${selected ? 'active' : ''}`}
      onClick={() => onSelect(source)}
    >
      <div className="pg-3d-card-top">
        <span className="pg-3d-icon">{source.icon}</span>
        <div>
          <strong>{source.name}</strong>
          <small>{source.description}</small>
        </div>
      </div>
      <div className="pg-3d-card-badges">
        {source.freeTier && <span className="pg-badge free">Free tier</span>}
        {source.gpuRequired && <span className="pg-badge gpu">GPU</span>}
        <span className="pg-badge kind">{source.kind}</span>
        {source.outputFormats.slice(0, 3).map((f) => (
          <span key={f} className="pg-badge fmt">{f}</span>
        ))}
      </div>
      {source.envRequirements && (
        <code className="pg-3d-env">{source.envRequirements}</code>
      )}
      {source.installGuide && (
        <small className="pg-3d-install">{source.installGuide}</small>
      )}
    </button>
  );
}
