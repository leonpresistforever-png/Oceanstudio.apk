import type { AgentArchetype, AgentMember, AgentMemberSkill, SkillFileRef } from './types';
import skillMd from '../../agent/skill.md?raw';
import designSkillMd from '../../agent/design-skill.md?raw';
import defaultUiTsx from '../../agent/templates/default-ui.tsx?raw';

const DESIGNER_SKILL_FILE_IDS = {
  platform: 'designer-skill-platform',
  design: 'designer-skill-design',
  ui: 'designer-skill-default-ui',
} as const;

/** Default skill files injected into designer sub-agents (Cursor-style context pack). */
export function getDesignerDefaultSkillFiles(): SkillFileRef[] {
  return [
    {
      id: DESIGNER_SKILL_FILE_IDS.platform,
      name: 'agent/skill.md',
      path: 'agent/skill.md',
      content: skillMd,
      format: 'markdown',
    },
    {
      id: DESIGNER_SKILL_FILE_IDS.design,
      name: 'agent/design-skill.md',
      path: 'agent/design-skill.md',
      content: designSkillMd,
      format: 'markdown',
    },
    {
      id: DESIGNER_SKILL_FILE_IDS.ui,
      name: 'agent/templates/default-ui.tsx',
      path: 'agent/templates/default-ui.tsx',
      content: defaultUiTsx,
      format: 'text',
    },
  ];
}

const DESIGNER_BUILTIN_SKILLS: AgentMemberSkill[] = [
  {
    id: 'builtin.figma-to-code',
    name: 'Figma to Code',
    source: 'builtin',
    content: `## Figma to Code (builtin)

When Figma MCP is connected:
1. Call get_design_context on the target frame before writing JSX
2. Map Figma variables to CSS custom properties from default-ui.tsx
3. Preserve auto-layout as flexbox/grid with matching gap and padding
4. Use lucide-react for icons unless Figma exports SVG assets

Follow agent/design-skill.md for full workflow.`,
  },
];

export const DESIGNER_SYSTEM_PROMPT = `You are the Designer sub-agent for Ocean.studio.

You receive pre-loaded context:
- agent/skill.md — platform workflow, terminal routing, preview
- agent/design-skill.md — UI/UX principles, Figma workflow, handoff rules
- agent/templates/default-ui.tsx — default UI scaffold (extend, do not ignore)

Your job: craft clean, modern, accessible interfaces. Use the default UI scaffold and design tokens.
When implementing UI tasks, output complete component code with file paths and preview instructions.`;

export const DESIGNER_TASK_ASSIGNMENT =
  'UI/UX design, layouts, styling, component polish, Figma-to-code, accessibility';

const DESIGN_KEYWORDS = ['ui', 'ux', 'design', 'layout', 'style', 'component', 'figma', 'css', 'frontend', 'screen', 'page', 'dashboard', 'modal', 'form'];

function mergeSkillFiles(existing: SkillFileRef[], defaults: SkillFileRef[]): SkillFileRef[] {
  const names = new Set(existing.map((f) => f.name));
  const merged = [...existing];
  for (const file of defaults) {
    if (!names.has(file.name)) merged.push(file);
  }
  return merged;
}

function mergeSkills(existing: AgentMemberSkill[], defaults: AgentMemberSkill[]): AgentMemberSkill[] {
  const ids = new Set(existing.map((s) => s.id));
  const merged = [...existing];
  for (const skill of defaults) {
    if (!ids.has(skill.id)) merged.push(skill);
  }
  return merged;
}

/** Apply archetype defaults when creating a new member. */
export function applyArchetypeDefaults(
  archetype: AgentArchetype,
  partial: Partial<AgentMember>
): Partial<AgentMember> {
  if (archetype !== 'designer') return partial;

  const defaults = getDesignerDefaultSkillFiles();
  return {
    ...partial,
    systemPrompt: partial.systemPrompt?.trim() ? partial.systemPrompt : DESIGNER_SYSTEM_PROMPT,
    taskAssignment: partial.taskAssignment?.trim() ? partial.taskAssignment : DESIGNER_TASK_ASSIGNMENT,
    skillFiles: mergeSkillFiles(partial.skillFiles ?? [], defaults),
    skills: mergeSkills(partial.skills ?? [], DESIGNER_BUILTIN_SKILLS),
    assignedMcpIds: partial.assignedMcpIds?.length
      ? partial.assignedMcpIds
      : ['mcp.figma'],
    access: {
      terminal: true,
      mcp: true,
      plugins: true,
      mcpMode: 'assigned',
      pluginMode: 'all',
      ...partial.access,
    },
  };
}

/**
 * Runtime enrichment — ensures designer sub-agents always get the context pack
 * even if created before defaults existed or from workflow templates.
 */
export function enrichMemberForContext(member: AgentMember): AgentMember {
  if (member.archetype !== 'designer') return member;

  const defaults = getDesignerDefaultSkillFiles();
  const hasDesignSkill = member.skillFiles.some((f) => f.name.includes('design-skill'));
  if (hasDesignSkill && member.skillFiles.length >= defaults.length) return member;

  return {
    ...member,
    skillFiles: mergeSkillFiles(member.skillFiles, defaults),
    skills: mergeSkills(member.skills, DESIGNER_BUILTIN_SKILLS),
    systemPrompt: member.systemPrompt?.includes('default-ui.tsx')
      ? member.systemPrompt
      : [member.systemPrompt, DESIGNER_SYSTEM_PROMPT].filter(Boolean).join('\n\n'),
    assignedMcpIds: member.assignedMcpIds.length > 0 ? member.assignedMcpIds : ['mcp.figma'],
  };
}

/** Whether a prompt should route to designer workers in parallel mode. */
export function isDesignFocusedPrompt(prompt: string): boolean {
  const lower = prompt.toLowerCase();
  return DESIGN_KEYWORDS.some((k) => lower.includes(k));
}

/** Build designer-specific task prefix for parallel decomposition. */
export function buildDesignerTask(prompt: string, focus: string): string {
  return [
    '## Designer sub-agent task',
    '',
    'Use the injected skill pack: agent/skill.md, agent/design-skill.md, agent/templates/default-ui.tsx.',
    'Extend default-ui.tsx primitives. Output file paths + preview steps.',
    '',
    `Focus: ${focus}`,
    '',
    `Request: ${prompt}`,
  ].join('\n');
}
