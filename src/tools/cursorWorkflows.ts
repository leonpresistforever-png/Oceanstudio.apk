import type { PreviewViewportMode } from '../lib/previewViewport';
import type { AgentMode } from '../store/appStore';
import { useSkillsStore } from '../skills/skillsStore';
import { useAppStore } from '../store/appStore';
import { CURSOR_WORKFLOW_SKILLS } from '../skills/cursorWorkflowSkills';

export type WorkflowParamType = 'string' | 'number' | 'boolean' | 'select';

export interface WorkflowParam {
  key: string;
  label: string;
  type: WorkflowParamType;
  default: string | number | boolean;
  options?: { value: string; label: string }[];
  description?: string;
}

export interface AgentWorkflow {
  id: string;
  name: string;
  description: string;
  icon: string;
  category: 'coding' | 'design' | 'review' | 'ops';
  skillIds: string[];
  agentMode: AgentMode;
  params: WorkflowParam[];
  promptTemplate: string;
  /** Optional preview viewport to set when applied */
  previewViewport?: PreviewViewportMode;
}

export const CURSOR_WORKFLOWS: AgentWorkflow[] = [
  {
    id: 'codebase-chat',
    name: 'Codebase Chat',
    description: 'Cursor-style @codebase context — read workspace, answer with file citations',
    icon: '💬',
    category: 'coding',
    skillIds: ['builtin.cursor-codebase-chat'],
    agentMode: 'review',
    params: [
      { key: 'contextDepth', label: 'Context depth', type: 'select', default: 'full', options: [
        { value: 'active', label: 'Active file only' },
        { value: 'folder', label: 'Current folder' },
        { value: 'full', label: 'Full workspace' },
      ]},
      { key: 'includeTests', label: 'Include test files', type: 'boolean', default: true },
      { key: 'temperature', label: 'Temperature', type: 'number', default: 0.5 },
    ],
    promptTemplate: 'Answer using full workspace context (@codebase). Cite file paths. Context depth: {{contextDepth}}. Include tests: {{includeTests}}.',
  },
  {
    id: 'composer-multi-file',
    name: 'Composer Multi-file',
    description: 'Edit multiple files in one pass — scaffold features across components, routes, and tests',
    icon: '🎼',
    category: 'coding',
    skillIds: ['builtin.cursor-composer'],
    agentMode: 'auto',
    params: [
      { key: 'maxFiles', label: 'Max files per pass', type: 'number', default: 8 },
      { key: 'runBuildAfter', label: 'Run build after edits', type: 'boolean', default: true },
      { key: 'thinkingLevel', label: 'Thinking', type: 'select', default: 'high', options: [
        { value: 'low', label: 'Low' },
        { value: 'medium', label: 'Medium' },
        { value: 'high', label: 'High' },
      ]},
    ],
    promptTemplate: 'Composer mode: edit up to {{maxFiles}} files. Run build after: {{runBuildAfter}}. Plan files first, then implement.',
  },
  {
    id: 'bugbot-review',
    name: 'Bugbot Review',
    description: 'Review changes like Bugbot — bugs, security, edge cases, missing tests',
    icon: '🤖',
    category: 'review',
    skillIds: ['builtin.cursor-bugbot'],
    agentMode: 'review',
    params: [
      { key: 'strictness', label: 'Strictness', type: 'select', default: 'strict', options: [
        { value: 'normal', label: 'Normal' },
        { value: 'strict', label: 'Strict' },
        { value: 'maximum', label: 'Maximum' },
      ]},
      { key: 'includeSecurity', label: 'Security scan', type: 'boolean', default: true },
      { key: 'includePerformance', label: 'Performance check', type: 'boolean', default: false },
    ],
    promptTemplate: 'Bugbot review at {{strictness}} strictness. Security: {{includeSecurity}}. Performance: {{includePerformance}}. List issues by severity.',
  },
  {
    id: 'design-preview',
    name: 'Design + Preview',
    description: 'UI design with responsive preview — mobile, website, or desktop virtual screen',
    icon: '🎨',
    category: 'design',
    skillIds: ['builtin.cursor-design-preview', 'builtin.figma-to-code'],
    agentMode: 'auto',
    previewViewport: 'mobile',
    params: [
      { key: 'previewViewport', label: 'Preview format', type: 'select', default: 'mobile', options: [
        { value: 'website', label: 'Website (full width)' },
        { value: 'mobile', label: 'Mobile (390×844)' },
        { value: 'desktop', label: 'Desktop virtual screen' },
      ]},
      { key: 'includeA11y', label: 'Accessibility pass', type: 'boolean', default: true },
      { key: 'useDefaultUiScaffold', label: 'Use default-ui.tsx', type: 'boolean', default: true },
    ],
    promptTemplate: 'Design UI using agent/templates/default-ui.tsx. Preview in {{previewViewport}} format. A11y: {{includeA11y}}. Scaffold: {{useDefaultUiScaffold}}.',
  },
  {
    id: 'terminal-agent',
    name: 'Terminal Agent',
    description: 'Run shell commands with terminal routing — shell, cloud, or native',
    icon: '⌨️',
    category: 'ops',
    skillIds: ['builtin.cursor-terminal', 'builtin.terminal-routing'],
    agentMode: 'bypass',
    params: [
      { key: 'confirmCommands', label: 'Confirm destructive cmds', type: 'boolean', default: true },
      { key: 'shellType', label: 'Terminal', type: 'select', default: 'auto', options: [
        { value: 'auto', label: 'Auto-detect' },
        { value: 'shell', label: 'Desktop shell' },
        { value: 'cloud', label: 'Cloud Shell' },
        { value: 'native', label: 'Native (mobile)' },
      ]},
    ],
    promptTemplate: 'Terminal agent. Shell: {{shellType}}. Confirm destructive: {{confirmCommands}}. Follow agent/skill.md routing.',
  },
  {
    id: 'debug-systematic',
    name: 'Debug Systematic',
    description: 'Reproduce → isolate → fix — Cursor-style debug loop with logs',
    icon: '🔍',
    category: 'coding',
    skillIds: ['builtin.cursor-debug', 'builtin.debug-systematic'],
    agentMode: 'review',
    params: [
      { key: 'reproduceFirst', label: 'Reproduce before fix', type: 'boolean', default: true },
      { key: 'logLevel', label: 'Log verbosity', type: 'select', default: 'verbose', options: [
        { value: 'minimal', label: 'Minimal' },
        { value: 'normal', label: 'Normal' },
        { value: 'verbose', label: 'Verbose' },
      ]},
      { key: 'runTestsAfter', label: 'Run tests after fix', type: 'boolean', default: true },
    ],
    promptTemplate: 'Systematic debug. Reproduce first: {{reproduceFirst}}. Logs: {{logLevel}}. Tests after: {{runTestsAfter}}.',
  },
];

export function getWorkflowById(id: string): AgentWorkflow | undefined {
  return CURSOR_WORKFLOWS.find((w) => w.id === id);
}

export function renderWorkflowPrompt(workflow: AgentWorkflow, paramValues: Record<string, unknown>): string {
  let prompt = workflow.promptTemplate;
  for (const p of workflow.params) {
    const val = paramValues[p.key] ?? p.default;
    prompt = prompt.replace(new RegExp(`\\{\\{${p.key}\\}\\}`, 'g'), String(val));
  }
  return prompt;
}

export interface ApplyWorkflowResult {
  ok: boolean;
  message: string;
  prompt?: string;
  skillsInstalled: string[];
}

/** Apply workflow — install skills, set agent mode, preview viewport */
export function applyWorkflow(
  workflowId: string,
  paramValues: Record<string, unknown> = {}
): ApplyWorkflowResult {
  const workflow = getWorkflowById(workflowId);
  if (!workflow) {
    return { ok: false, message: 'Workflow not found', skillsInstalled: [] };
  }

  const skillsStore = useSkillsStore.getState();
  const installed: string[] = [];

  for (const skillId of workflow.skillIds) {
    const def = CURSOR_WORKFLOW_SKILLS.find((s) => s.id === skillId)
      ?? skillsStore.marketplaceSkills.find((s) => s.id === skillId);
    if (def) {
      const result = skillsStore.installSkill(def);
      if (result.ok) installed.push(def.name);
    }
  }

  const appStore = useAppStore.getState();
  const cfg = appStore.workspaceConfig;
  if (cfg) {
    appStore.setWorkspaceConfig({ ...cfg, agentMode: workflow.agentMode });
  }

  const viewport = (paramValues.previewViewport as PreviewViewportMode)
    ?? workflow.previewViewport;
  if (viewport) {
    appStore.setPreviewViewportMode(viewport);
  }

  const prompt = renderWorkflowPrompt(workflow, paramValues);

  return {
    ok: true,
    message: `Applied "${workflow.name}" — ${installed.length} skill(s), mode: ${workflow.agentMode}`,
    prompt,
    skillsInstalled: installed,
  };
}

export function getDefaultParamValues(workflow: AgentWorkflow): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  for (const p of workflow.params) {
    values[p.key] = p.default;
  }
  return values;
}
