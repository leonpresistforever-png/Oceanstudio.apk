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

/**
 * Resolves which terminal backend the agent (and UI) should use.
 *
 * - Electron .exe / desktop app  → shell (PowerShell on Windows, Bash on Linux/Mac)
 * - Website / web preview        → cloud (Google Cloud Shell)
 * - Mobile APK                   → native (proot-based Linux environment)
 */
export function resolvePreferredTerminal(ctx: PlatformContext): TerminalType {
  if (ctx.isElectron && ctx.isDesktop && !ctx.isMobile) {
    return 'shell';
  }
  if (ctx.isWeb && !ctx.isElectron) {
    return 'cloud';
  }
  if (ctx.isMobile && !ctx.isDesktop) {
    return 'native';
  }
  // Dev browser on desktop: use shell if dev WS terminal available, else cloud
  if (ctx.hasNativeShell && ctx.isDesktop) {
    return 'shell';
  }
  return 'cloud';
}

export function terminalTypeLabel(type: TerminalType, shellLabel?: string): string {
  switch (type) {
    case 'shell':
      return shellLabel ? `Shell (${shellLabel})` : 'Shell';
    case 'cloud':
      return 'Cloud Terminal (GCP)';
    case 'native':
      return 'Native Terminal';
  }
}

export function describeTerminalCapabilities(type: TerminalType, platform: string): string {
  switch (type) {
    case 'shell':
      if (platform === 'win32') {
        return 'Real PowerShell on Windows. Full access to npm, pip, winget, choco, and WSL if installed. Not a Linux VM unless WSL is used.';
      }
      return 'Real native shell (Bash/Zsh). Full access to apt, pip, pkg, brew, and all installed binaries on the host OS.';
    case 'cloud':
      return 'Google Cloud Shell — Debian Linux VM in GCP. apt, pip, gcloud, docker, and persistent home directory. OAuth-connected at user level.';
    case 'native':
      return 'Mobile native Linux environment (proot-based). Full package manager access on device without root.';
  }
}
