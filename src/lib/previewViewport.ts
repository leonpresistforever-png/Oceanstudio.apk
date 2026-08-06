export type PreviewViewportMode = 'website' | 'mobile' | 'desktop';

export interface PreviewViewportSpec {
  id: PreviewViewportMode;
  label: string;
  shortLabel: string;
  width: number | '100%';
  height: number | '100%';
  scale?: number;
  frame: 'none' | 'phone' | 'monitor';
  userAgentHint?: string;
}

export const PREVIEW_VIEWPORTS: PreviewViewportSpec[] = [
  {
    id: 'website',
    label: 'Website',
    shortLabel: 'Web',
    width: '100%',
    height: '100%',
    frame: 'none',
  },
  {
    id: 'mobile',
    label: 'Mobile',
    shortLabel: 'Mobile',
    width: 390,
    height: 844,
    frame: 'phone',
    userAgentHint: 'iPhone / Android viewport',
  },
  {
    id: 'desktop',
    label: 'Desktop',
    shortLabel: 'Desktop',
    width: 1280,
    height: 800,
    scale: 0.85,
    frame: 'monitor',
    userAgentHint: '1280×800 virtual display',
  },
];

export function getViewportSpec(mode: PreviewViewportMode): PreviewViewportSpec {
  return PREVIEW_VIEWPORTS.find((v) => v.id === mode) ?? PREVIEW_VIEWPORTS[0];
}
