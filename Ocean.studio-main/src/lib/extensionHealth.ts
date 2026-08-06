import { useExtensionsStore } from '../extensions/extensionsStore';
import type { InstalledExtension } from '../extensions/types';

export interface ExtensionHealthReport {
  total: number;
  enabled: number;
  healthy: number;
  issues: { id: string; name: string; problem: string }[];
  serialized: string;
}

export function checkExtensionHealth(extensions?: InstalledExtension[]): ExtensionHealthReport {
  const list = extensions ?? useExtensionsStore.getState().installed;
  const enabled = list.filter((e) => e.enabled);
  const issues: ExtensionHealthReport['issues'] = [];

  for (const ext of enabled) {
    if (!ext.agentGuide?.trim()) {
      issues.push({ id: ext.id, name: ext.displayName, problem: 'Missing agent guide' });
    }
    if (ext.envRequirements && !ext.commands?.length) {
      issues.push({ id: ext.id, name: ext.displayName, problem: 'Env required but no commands defined' });
    }
  }

  const healthy = enabled.length - issues.length;

  const lines = [
    '## Extension Health',
    `Enabled: ${enabled.length}/${list.length} · Healthy: ${healthy}`,
  ];
  if (issues.length) {
    lines.push('', 'Issues:');
    for (const i of issues) {
      lines.push(`- ${i.name} (${i.id}): ${i.problem}`);
    }
  } else if (enabled.length) {
    lines.push('', 'All enabled extensions have agent guides.');
  }

  return {
    total: list.length,
    enabled: enabled.length,
    healthy,
    issues,
    serialized: lines.join('\n'),
  };
}
