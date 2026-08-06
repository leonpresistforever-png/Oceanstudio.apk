import { Globe, Smartphone, Monitor } from 'lucide-react';
import type { PreviewViewportMode } from '../../lib/previewViewport';
import { PREVIEW_VIEWPORTS } from '../../lib/previewViewport';

const ICONS = {
  website: Globe,
  mobile: Smartphone,
  desktop: Monitor,
} as const;

interface Props {
  mode: PreviewViewportMode;
  onChange: (mode: PreviewViewportMode) => void;
  compact?: boolean;
}

export default function PreviewViewportBar({ mode, onChange, compact }: Props) {
  return (
    <div className={`preview-viewport-bar ${compact ? 'preview-viewport-bar--compact' : ''}`}>
      {PREVIEW_VIEWPORTS.map((v) => {
        const Icon = ICONS[v.id];
        const active = mode === v.id;
        return (
          <button
            key={v.id}
            type="button"
            className={`preview-viewport-btn ${active ? 'preview-viewport-btn--active' : ''}`}
            onClick={() => onChange(v.id)}
            title={`${v.label} preview`}
          >
            <Icon size={compact ? 13 : 14} />
            {!compact && <span>{v.shortLabel}</span>}
          </button>
        );
      })}
    </div>
  );
}
