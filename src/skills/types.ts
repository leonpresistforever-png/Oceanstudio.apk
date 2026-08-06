export type SkillScope = 'main' | 'playground' | 'both';

export type SkillCategory =
  | 'coding'
  | 'research'
  | 'deployment'
  | 'testing'
  | 'security'
  | 'database'
  | 'api'
  | 'media'
  | 'productivity'
  | 'playground'
  | 'firebase'
  | 'figma'
  | 'gateway'
  | 'multi-agent';

export type SkillSource = 'builtin' | 'oss' | 'custom' | 'agent';

export interface SkillDefinition {
  id: string;
  name: string;
  description: string;
  category: SkillCategory;
  scope: SkillScope;
  tags: string[];
  source: SkillSource;
  content?: string;
  author?: string;
  repoUrl?: string;
  installs?: number;
  verified?: boolean;
  installedAt?: number;
}

export interface InstalledSkill extends SkillDefinition {
  enabled: boolean;
  installedAt: number;
}

export const SKILL_CATEGORIES: { id: SkillCategory; label: string }[] = [
  { id: 'coding', label: 'Coding' },
  { id: 'research', label: 'Research' },
  { id: 'deployment', label: 'Deployment' },
  { id: 'testing', label: 'Testing' },
  { id: 'security', label: 'Security' },
  { id: 'database', label: 'Database' },
  { id: 'api', label: 'API & Integrations' },
  { id: 'media', label: 'Media & Creative' },
  { id: 'productivity', label: 'Productivity' },
  { id: 'playground', label: 'Playground' },
  { id: 'firebase', label: 'Firebase' },
  { id: 'figma', label: 'Figma' },
  { id: 'gateway', label: 'Gateway' },
  { id: 'multi-agent', label: 'Multi-Agent' },
];

export function parseSkillMd(raw: string, fallbackName = 'Custom Skill'): {
  name: string;
  description: string;
  content: string;
} {
  const trimmed = raw.trim();
  if (!trimmed.startsWith('---')) {
    return { name: fallbackName, description: '', content: trimmed };
  }
  const end = trimmed.indexOf('---', 3);
  if (end === -1) return { name: fallbackName, description: '', content: trimmed };
  const front = trimmed.slice(3, end).trim();
  const body = trimmed.slice(end + 3).trim();
  let name = fallbackName;
  let description = '';
  for (const line of front.split('\n')) {
    const [key, ...rest] = line.split(':');
    const val = rest.join(':').trim();
    if (key.trim() === 'name') name = val;
    if (key.trim() === 'description') description = val;
  }
  return { name, description, content: trimmed };
}

export function skillToAgentPayload(skills: InstalledSkill[]): { name: string; content: string }[] {
  return skills
    .filter((s) => s.enabled && s.content)
    .map((s) => ({ name: s.name, content: s.content! }));
}
