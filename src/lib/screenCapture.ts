/** Lightweight screen context for narrator / voice assistant (no full recording session). */

export interface ScreenSnapshot {
  platform: 'electron' | 'web' | 'android' | 'unknown';
  primarySource?: string;
  sources: { id: string; name: string; type: string }[];
  /** Base64 data URL thumbnail when available (Electron/Android) */
  thumbnailDataUrl?: string;
  capturedAt: number;
}

export async function captureScreenSnapshot(): Promise<ScreenSnapshot> {
  const api = typeof window !== 'undefined' ? window.ocean : undefined;
  const platform = api?.platform ?? 'web';

  if (api?.recording?.getSources) {
    try {
      const sources = await api.recording.getSources();
      const screen = sources.find((s) => s.type === 'screen') ?? sources[0];
      return {
        platform: platform as ScreenSnapshot['platform'],
        primarySource: screen?.name,
        sources: sources.map((s) => ({ id: s.id, name: s.name, type: s.type })),
        thumbnailDataUrl: screen?.thumbnail,
        capturedAt: Date.now(),
      };
    } catch {
      /* fall through */
    }
  }

  return {
    platform: platform as ScreenSnapshot['platform'],
    primarySource: typeof document !== 'undefined' ? document.title : undefined,
    sources: typeof document !== 'undefined'
      ? [{ id: 'tab', name: document.title || 'Browser tab', type: 'window' }]
      : [],
    capturedAt: Date.now(),
  };
}

export function formatScreenSnapshotForPrompt(snapshot: ScreenSnapshot): string {
  const lines = [
    '## Live screen context',
    `Platform: ${snapshot.platform}`,
    snapshot.primarySource ? `Primary display/window: ${snapshot.primarySource}` : '',
    snapshot.sources.length > 0
      ? `Visible sources: ${snapshot.sources.map((s) => s.name).join(', ')}`
      : '',
    snapshot.thumbnailDataUrl
      ? 'A screen thumbnail was captured (visual layout available to the agent runtime).'
      : 'Describe workspace UI from editor context when pixels are unavailable.',
  ].filter(Boolean);
  return lines.join('\n');
}
