export type TerminalType = 'shell' | 'cloud' | 'native';

export interface PlatformContext {
  platform: string;
  isElectron: boolean;
  isDesktop: boolean;
  isMobile: boolean;
  isWeb: boolean;
  hasNativeShell?: boolean;
  shellLabel?: string;
}

export function resolvePreferredTerminal(ctx: PlatformContext): TerminalType {
  if (ctx.isElectron && ctx.isDesktop && !ctx.isMobile) return 'shell';
  if ((ctx as PlatformContext & { hasNativeTerminal?: boolean }).hasNativeTerminal) return 'native';
  if (ctx.isMobile && !ctx.isDesktop) return 'native';
  if (ctx.isWeb && !ctx.isElectron) return 'cloud';
  if (ctx.hasNativeShell && ctx.isDesktop) return 'shell';
  return 'cloud';
}

export function describeTerminal(type: TerminalType, platform?: string): string {
  if (type === 'shell') {
    if (platform === 'win32') return 'Real PowerShell/WSL on your Windows machine';
    return 'Real native shell on your machine — apt, pip, brew, all installed binaries';
  }
  if (type === 'cloud') return 'Google Cloud Shell — Debian VM in GCP via OAuth';
  return 'Native mobile Linux (proot) — full package manager on device';
}
