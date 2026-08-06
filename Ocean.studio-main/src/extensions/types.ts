export type ExtensionPlatform = 'electron' | 'web' | 'mobile';

export type ExtensionCategory =
  | 'language'
  | 'formatter'
  | 'linter'
  | 'debug'
  | 'scm'
  | 'theme'
  | 'ai'
  | 'productivity'
  | 'testing'
  | 'docker'
  | 'database'
  | 'other';

export type ExtensionInstallType = 'builtin' | 'openvsx' | 'npm' | 'git' | 'vsix' | 'custom';

export type ExtensionSource = 'builtin' | 'oss' | 'custom' | 'agent';

export interface ExtensionCommand {
  id: string;
  title: string;
  category?: string;
}

export interface ExtensionDefinition {
  id: string;
  name: string;
  displayName: string;
  description: string;
  version: string;
  publisher: string;
  category: ExtensionCategory;
  platforms: ExtensionPlatform[];
  installType: ExtensionInstallType;
  source: ExtensionSource;
  /** How the agent should use this extension — injected into context */
  agentGuide: string;
  commands?: ExtensionCommand[];
  activationEvents?: string[];
  openvsxId?: string;
  npmPackage?: string;
  repository?: string;
  vsixUrl?: string;
  envRequirements?: string;
  tags: string[];
  verified?: boolean;
  downloads?: number;
  rating?: number;
  icon?: string;
}

export interface InstalledExtension extends ExtensionDefinition {
  enabled: boolean;
  installedAt: number;
  config?: Record<string, string>;
}

export const EXTENSION_CATEGORIES: { id: ExtensionCategory; label: string }[] = [
  { id: 'language', label: 'Languages' },
  { id: 'formatter', label: 'Formatters' },
  { id: 'linter', label: 'Linters' },
  { id: 'debug', label: 'Debuggers' },
  { id: 'scm', label: 'Source Control' },
  { id: 'theme', label: 'Themes' },
  { id: 'ai', label: 'AI & Agents' },
  { id: 'productivity', label: 'Productivity' },
  { id: 'testing', label: 'Testing' },
  { id: 'docker', label: 'Docker & DevOps' },
  { id: 'database', label: 'Database' },
  { id: 'other', label: 'Other' },
];

export function extensionToAgentPayload(ext: InstalledExtension): { id: string; name: string; guide: string; commands: string } {
  const cmdList = (ext.commands ?? []).map((c) => `${c.id}: ${c.title}`).join('; ') || 'none';
  return {
    id: ext.id,
    name: ext.displayName,
    guide: ext.agentGuide,
    commands: cmdList,
  };
}

export function serializeExtensionsForAgent(extensions: InstalledExtension[]): string {
  if (!extensions.length) return '';
  const lines = ['## Installed Extensions\n'];
  for (const ext of extensions.filter((e) => e.enabled)) {
    lines.push(`### ${ext.displayName} (${ext.id})`);
    lines.push(`Publisher: ${ext.publisher} · v${ext.version} · ${ext.category}`);
    lines.push(ext.agentGuide);
    if (ext.commands?.length) {
      lines.push('**Commands:**', ...ext.commands.map((c) => `- \`${c.id}\` — ${c.title}`));
    }
    if (ext.envRequirements) lines.push(`**Env:** ${ext.envRequirements}`);
    lines.push('');
  }
  return lines.join('\n');
}

export function parseExtensionManifest(json: Record<string, unknown>, fallbackId?: string): Partial<ExtensionDefinition> {
  const contributes = json.contributes as Record<string, unknown> | undefined;
  const cmds = (contributes?.commands as { command?: string; id?: string; title: string; category?: string }[] | undefined) ?? [];
  return {
    id: (json.name as string) ?? fallbackId ?? 'custom.extension',
    name: (json.name as string) ?? 'custom',
    displayName: (json.displayName as string) ?? (json.name as string) ?? 'Custom Extension',
    description: (json.description as string) ?? '',
    version: (json.version as string) ?? '1.0.0',
    publisher: (json.publisher as string) ?? 'custom',
    commands: cmds.map((c) => ({ id: c.command ?? c.id ?? '', title: c.title, category: c.category })).filter((c) => c.id),
    activationEvents: (json.activationEvents as string[]) ?? [],
  };
}
