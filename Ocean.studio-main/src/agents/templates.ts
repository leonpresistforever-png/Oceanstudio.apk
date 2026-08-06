import { createAgentCombo, createAgentMember } from './types';
import type { AgentCombo } from './types';
import { enrichMemberForContext } from './archetypeDefaults';

export interface WorkflowTemplate {
  id: string;
  name: string;
  tagline: string;
  mode: 'parallel' | 'collaborative';
  icon: string;
  gradient: [string, string];
  category: 'engineering' | 'research' | 'creative' | 'ops';
  build: () => AgentCombo;
}

function tpl(
  id: string,
  name: string,
  tagline: string,
  mode: 'parallel' | 'collaborative',
  icon: string,
  gradient: [string, string],
  category: WorkflowTemplate['category'],
  members: Parameters<typeof createAgentMember>[0][],
  description: string
): WorkflowTemplate {
  return {
    id, name, tagline, mode, icon, gradient, category,
    build: () => {
      const built = members.map((m) => createAgentMember(m)).map(enrichMemberForContext);
      return createAgentCombo({
        name,
        description,
        mode,
        headAgentId: built.find((m) => m.role === 'head')?.id ?? built[0]?.id ?? '',
        members: built,
      });
    },
  };
}

export const WORKFLOW_TEMPLATES: WorkflowTemplate[] = [
  tpl(
    'full-stack-team', 'Full Stack Team',
    'Codex leads · Antigravity reasons · Flash iterates · Claude reviews',
    'parallel', '⚡', ['#0ea5e9', '#8b5cf6'], 'engineering',
    [
      { name: 'Codex Lead', role: 'head', providerId: 'openai.codex', providerName: 'OpenAI Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Architecture & synthesis', systemPrompt: 'Lead engineer. Decompose tasks, synthesize team output, confirm with user.' },
      { name: 'Antigravity', role: 'worker', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus 4.6 Thinking', brandColor: '#8b5cf6', taskAssignment: 'Deep reasoning & refactors', systemPrompt: 'Think deeply. Handle complex logic and architectural decisions.' },
      { name: 'Gemini Flash', role: 'worker', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini 3.6 Flash', brandColor: '#4285f4', taskAssignment: 'Fast UI & iteration', systemPrompt: 'Move fast. Polish UI, fix small bugs, iterate quickly.' },
      { name: 'Claude Code', role: 'reviewer', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet 4.6', brandColor: '#d97706', taskAssignment: 'Review, tests, docs', systemPrompt: 'Review all changes. Write tests and documentation.' },
    ],
    'Production-grade parallel team — head coordinates, specialists execute'
  ),
  tpl(
    'rapid-prototype', 'Rapid Prototype',
    'All minds on deck — collaborative burst for MVPs',
    'collaborative', '🚀', ['#f97316', '#ec4899'], 'creative',
    [
      { name: 'Codex', role: 'worker', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Scaffold & structure', params: { temperature: 0.9, maxTokens: 4096, thinkingLevel: 'low', topP: 0.95, strictness: 'relaxed', stream: true } },
      { name: 'Antigravity Flash', role: 'worker', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash', brandColor: '#4285f4', taskAssignment: 'UI & polish', params: { temperature: 0.85, maxTokens: 4096, thinkingLevel: 'low', topP: 0.95, strictness: 'relaxed', stream: true } },
      { name: 'Claude', role: 'worker', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#d97706', taskAssignment: 'Edge cases & cleanup', params: { temperature: 0.8, maxTokens: 4096, thinkingLevel: 'medium', topP: 0.95, strictness: 'normal', stream: true } },
    ],
    'Collaborative sprint — every agent works the same prompt simultaneously'
  ),
  tpl(
    'security-audit', 'Security Audit',
    'Reviewer-first parallel pipeline for hardening code',
    'parallel', '🛡️', ['#dc2626', '#7c3aed'], 'ops',
    [
      { name: 'Security Lead', role: 'head', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#d97706', taskAssignment: 'Threat model & final report', systemPrompt: 'Security lead. Synthesize findings, prioritize CVEs, surface critical fixes.' },
      { name: 'Codex Scanner', role: 'specialist', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Static analysis & code patterns', systemPrompt: 'Scan for injection, auth bypass, secrets in code.' },
      { name: 'Deep Reasoner', role: 'worker', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus Thinking', brandColor: '#8b5cf6', taskAssignment: 'Logic flaws & race conditions', systemPrompt: 'Deep analysis of auth flows, concurrency, data handling.' },
    ],
    'Security-focused team with Claude as head reviewer'
  ),
  tpl(
    'research-build', 'Research → Build',
    'Research agent gathers context, builders implement',
    'parallel', '🔬', ['#06b6d4', '#22c55e'], 'research',
    [
      { name: 'Research Head', role: 'head', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/claude-opus-4-6-thinking', modelLabel: 'Opus Thinking', brandColor: '#8b5cf6', taskAssignment: 'Research synthesis & plan', systemPrompt: 'Synthesize research, create implementation plan, delegate build tasks.' },
      { name: 'Web Researcher', role: 'specialist', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash', brandColor: '#4285f4', taskAssignment: 'Docs, APIs, examples', systemPrompt: 'Find documentation, API references, and working examples.' },
      { name: 'Builder', role: 'worker', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Implementation', systemPrompt: 'Build from research findings. Write production code.' },
    ],
    'Research phase then build — head coordinates both'
  ),
  tpl(
    'pair-programming', 'Pair Programming',
    'Driver + navigator collaborative duo',
    'collaborative', '👥', ['#6366f1', '#a855f7'], 'engineering',
    [
      { name: 'Driver', role: 'worker', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Write code', systemPrompt: 'You drive — write the implementation.' },
      { name: 'Navigator', role: 'reviewer', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#d97706', taskAssignment: 'Review & guide', systemPrompt: 'You navigate — spot issues, suggest better patterns, catch bugs.' },
    ],
    'Classic pair programming — two agents, one codebase'
  ),
  tpl(
    'ops-deploy', 'Ops & Deploy',
    'Build, test, deploy pipeline team',
    'parallel', '🚢', ['#0d9488', '#0369a1'], 'ops',
    [
      { name: 'Ops Lead', role: 'head', providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex', brandColor: '#10a37f', taskAssignment: 'Deploy orchestration', systemPrompt: 'Coordinate build, test, deploy. Surface status to user.' },
      { name: 'Test Runner', role: 'specialist', providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet', brandColor: '#d97706', taskAssignment: 'Tests & CI', systemPrompt: 'Run and fix tests. Ensure CI passes.' },
      { name: 'Infra', role: 'worker', providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash', brandColor: '#4285f4', taskAssignment: 'Docker, configs, scripts', systemPrompt: 'Handle Docker, env configs, deployment scripts.' },
    ],
    'DevOps-style parallel team for shipping'
  ),
  tpl(
    'design-studio', 'Design Studio',
    'Codex leads · Designer ships UI from default scaffold + Figma',
    'parallel', '🎨', ['#ec4899', '#8b5cf6'], 'creative',
    [
      {
        name: 'Design Lead', role: 'head', archetype: 'core', categoryId: 'cat-core',
        providerId: 'openai.codex', providerName: 'OpenAI Codex', modelId: 'codex-latest', modelLabel: 'Codex',
        brandColor: '#10a37f', taskAssignment: 'Coordinate design + implementation',
        systemPrompt: 'Head agent. Decompose UI requests, assign designer sub-agent, synthesize code + preview steps for user.',
      },
      {
        name: 'UI Designer', role: 'worker', archetype: 'designer', categoryId: 'cat-design',
        providerId: 'google.antigravity', providerName: 'Antigravity', modelId: 'antigravity/gemini-3.6-flash-high', modelLabel: 'Gemini Flash',
        brandColor: '#ec4899', taskAssignment: 'UI/UX, layouts, Figma-to-code',
        systemPrompt: 'Designer sub-agent — use injected skill.md, design-skill.md, and default-ui.tsx scaffold.',
      },
      {
        name: 'Implementer', role: 'worker', archetype: 'tools', categoryId: 'cat-engineering',
        providerId: 'openai.codex', providerName: 'Codex', modelId: 'codex-latest', modelLabel: 'Codex',
        brandColor: '#0ea5e9', taskAssignment: 'Wire logic, APIs, state',
        systemPrompt: 'Integrate designer output with app logic, routes, and data layer.',
      },
      {
        name: 'Reviewer', role: 'reviewer', archetype: 'debugger', categoryId: 'cat-engineering',
        providerId: 'anthropic.claude-code', providerName: 'Claude Code', modelId: 'claude-sonnet-4-6', modelLabel: 'Claude Sonnet',
        brandColor: '#d97706', taskAssignment: 'A11y, responsive, polish review',
        systemPrompt: 'Review UI for accessibility, responsive breakpoints, and consistency with design tokens.',
      },
    ],
    'Design-first parallel workflow — designer gets skill pack + default UI scaffold automatically'
  ),
];

export function getTemplateById(id: string): WorkflowTemplate | undefined {
  return WORKFLOW_TEMPLATES.find((t) => t.id === id);
}
