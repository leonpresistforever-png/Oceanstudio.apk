/** Check Ocean proxy status from any platform (Electron, APK, web on LAN) */
export interface OceanProxyClientStatus {
  running: boolean;
  port: number;
  url: string;
  lanUrl: string;
  message: string;
}

export async function fetchOceanProxyStatus(baseUrl = 'http://localhost:20128'): Promise<OceanProxyClientStatus> {
  try {
    const res = await fetch(`${baseUrl}/ocean-proxy/status`, { signal: AbortSignal.timeout(2000) });
    if (!res.ok) throw new Error('not running');
    const data = await res.json() as OceanProxyClientStatus;
    return { ...data, running: true };
  } catch {
    return {
      running: false,
      port: 20128,
      url: baseUrl,
      lanUrl: baseUrl,
      message: 'Ocean proxy not reachable',
    };
  }
}

export async function startOceanProxyViaApi(port = 20128): Promise<OceanProxyClientStatus> {
  const api = typeof window !== 'undefined' ? window.ocean?.providers : undefined;
  if (api?.startOceanProxy) {
    const s = await api.startOceanProxy(port);
    return { running: s.running, port, url: s.url, lanUrl: s.lanUrl, message: s.message };
  }
  if (api?.startProxy) {
    const s = await api.startProxy('ocean', port);
    return { running: s.running, port, url: s.url, lanUrl: s.lanUrl ?? s.url, message: s.message };
  }
  return fetchOceanProxyStatus();
}

export function getOAuthRedirectHint(proxyStatus: OceanProxyClientStatus, providerId: string): string {
  const slug = providerId.replace(/\./g, '-');
  return `${proxyStatus.lanUrl}/oauth/${slug}/callback`;
}
