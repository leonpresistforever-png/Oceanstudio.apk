import type { PreviewViewportMode } from '../../lib/previewViewport';
import { getViewportSpec } from '../../lib/previewViewport';
import './PreviewPanel.css';

interface Props {
  src: string;
  title?: string;
  mode: PreviewViewportMode;
  className?: string;
}

export default function PreviewFrame({ src, title = 'Preview', mode, className = '' }: Props) {
  const spec = getViewportSpec(mode);

  const iframe = (
    <iframe
      src={src}
      title={title}
      className="preview-frame-iframe"
      sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
    />
  );

  if (spec.frame === 'none') {
    return (
      <div className={`preview-frame preview-frame--website ${className}`}>
        {iframe}
      </div>
    );
  }

  if (spec.frame === 'phone') {
    return (
      <div className={`preview-frame preview-frame--device ${className}`}>
        <div
          className="preview-device preview-device--phone"
          style={{ width: spec.width, height: spec.height }}
        >
          <div className="preview-device-status">
            <span className="preview-device-time">9:41</span>
            <div className="preview-device-notch" />
            <span className="preview-device-icons">●●●</span>
          </div>
          <div className="preview-device-screen">{iframe}</div>
          <div className="preview-device-home-bar" />
        </div>
        <p className="preview-device-label">{spec.width}×{spec.height} · {spec.userAgentHint}</p>
      </div>
    );
  }

  // Virtual desktop monitor
  const scale = spec.scale ?? 1;
  const w = typeof spec.width === 'number' ? spec.width : 1280;
  const h = typeof spec.height === 'number' ? spec.height : 800;

  return (
    <div className={`preview-frame preview-frame--device ${className}`}>
      <div
        className="preview-device preview-device--monitor"
        style={{ transform: `scale(${scale})`, transformOrigin: 'top center' }}
      >
        <div className="preview-monitor-bezel">
          <div className="preview-monitor-chrome">
            <span className="preview-monitor-dot preview-monitor-dot--red" />
            <span className="preview-monitor-dot preview-monitor-dot--yellow" />
            <span className="preview-monitor-dot preview-monitor-dot--green" />
            <span className="preview-monitor-title">Ocean Preview — Virtual Desktop</span>
          </div>
          <div className="preview-monitor-screen" style={{ width: w, height: h }}>
            {iframe}
          </div>
        </div>
        <div className="preview-monitor-neck" />
        <div className="preview-monitor-stand" />
      </div>
      <p className="preview-device-label">{w}×{h} virtual display · desktop format</p>
    </div>
  );
}
