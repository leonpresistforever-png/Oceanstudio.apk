/**
 * Google Cloud Shell web preview URL helpers.
 * Ports in Cloud Shell run in Google's sandbox — preview via HTTPS proxy, not localhost.
 * @see https://cloud.google.com/shell/docs/using-web-preview
 */

export type PreviewPortSource = 'local' | 'cloudshell';

export interface PreviewPortInfo {
  port: number;
  url: string;
  label?: string;
  source: PreviewPortSource;
  /** Open in browser (proxy auth for Cloud Shell) */
  openUrl?: string;
  detectedAt?: number;
}

/** Format used inside Cloud Shell VM: https://$PORT-$WEB_HOST */
export function buildCloudShellDevUrl(port: number, webHost: string): string {
  const host = webHost.replace(/^https?:\/\//, '').replace(/\/$/, '');
  return `https://${port}-${host}`;
}

/**
 * Google Cloud Console proxy — authenticates user then redirects to sandbox port.
 * Works from outside the VM when OAuth session is active.
 */
export function buildCloudShellProxyUrl(port: number, authUser = '0', environmentId = 'default'): string {
  const params = new URLSearchParams({
    authuser: authUser,
    port: String(port),
    environment_id: environmentId,
  });
  return `https://shell.cloud.google.com/devshell/proxy?${params.toString()}`;
}

/** Fallback pattern when region/machine metadata is available */
export function buildCloudShellVpcfUrl(port: number, machine: string, region: string, authUser = '0'): string {
  const machineId = machine.endsWith('-default') ? machine : `${machine}-default`;
  return `https://${port}-${machineId}.cs-${region}-vpcf.cloudshell.dev/?authuser=${authUser}`;
}

export function isCloudShellPreviewUrl(url: string): boolean {
  return url.includes('cloudshell.dev') || url.includes('shell.cloud.google.com/devshell');
}

export function previewUrlForPort(
  port: number,
  source: PreviewPortSource,
  opts?: { webHost?: string; machine?: string; region?: string }
): PreviewPortInfo {
  if (source === 'local') {
    const url = `http://localhost:${port}`;
    return { port, url, openUrl: url, source, label: 'local' };
  }

  const openUrl = buildCloudShellProxyUrl(port);
  let url = openUrl;

  if (opts?.webHost) {
    url = buildCloudShellDevUrl(port, opts.webHost);
  } else if (opts?.machine && opts?.region) {
    url = buildCloudShellVpcfUrl(port, opts.machine, opts.region);
  }

  return {
    port,
    url,
    openUrl,
    source: 'cloudshell',
    label: 'Cloud Shell',
  };
}
