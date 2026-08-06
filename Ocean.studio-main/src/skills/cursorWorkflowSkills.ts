import type { SkillDefinition } from './types';

/** Cursor-inspired workflow skills — installable from Tools → Agent Workflows */
export const CURSOR_WORKFLOW_SKILLS: SkillDefinition[] = [
  {
    id: 'builtin.cursor-codebase-chat',
    name: 'Cursor Codebase Chat',
    description: '@codebase-style workspace context with file citations',
    category: 'coding',
    scope: 'main',
    tags: ['cursor', 'codebase', 'context'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-codebase-chat
description: Read full workspace and answer with file citations like Cursor @codebase
---

# Cursor Codebase Chat

## When to use
- User asks about "the codebase", architecture, or "where is X defined"
- Need cross-file understanding before editing

## Instructions
1. Call \`buildAgentContext({ workspacePath, activeFile })\` for full context
2. Read relevant files via \`window.ocean.fs.readFile\` — don't guess paths
3. Cite paths as \`src/foo/bar.ts:42\` in responses
4. Prefer grep/search over loading entire large files
5. Respect contextDepth param: active | folder | full workspace

## Output
- Answer with 2–5 file citations minimum when referencing code
- Suggest next files to open if context is incomplete`,
  },
  {
    id: 'builtin.cursor-composer',
    name: 'Cursor Composer',
    description: 'Multi-file edits in one agent pass — scaffold features across the repo',
    category: 'coding',
    scope: 'main',
    tags: ['cursor', 'composer', 'multi-file'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-composer
description: Plan and edit multiple files in one session like Cursor Composer
---

# Cursor Composer

## Workflow
1. **Plan** — list files to create/modify (max per param)
2. **Dependencies** — order edits: types → utils → components → routes
3. **Implement** — write all files; keep imports consistent
4. **Verify** — run build/test if runBuildAfter is true

## Rules
- One coherent feature per composer pass
- Don't leave TODO stubs — ship working code
- Match existing project conventions (read 2–3 similar files first)
- Report all changed paths at end`,
  },
  {
    id: 'builtin.cursor-bugbot',
    name: 'Cursor Bugbot Review',
    description: 'PR-style review — bugs, security, edge cases, missing tests',
    category: 'coding',
    scope: 'main',
    tags: ['cursor', 'bugbot', 'review'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-bugbot
description: Review code changes like Cursor Bugbot
---

# Bugbot Review

## Checklist
- [ ] Logic bugs and off-by-one errors
- [ ] Null/undefined handling
- [ ] Race conditions and async pitfalls
- [ ] Security: injection, auth, secrets in code
- [ ] Missing error handling
- [ ] Test coverage gaps

## Output format
\`\`\`
## Critical
- file:line — issue — fix suggestion

## Warning
...

## Suggestion
...
\`\`\`

Strictness scales depth: normal = obvious bugs; strict = edge cases; maximum = security + perf`,
  },
  {
    id: 'builtin.cursor-design-preview',
    name: 'Cursor Design Preview',
    description: 'UI design with Ocean preview viewports — mobile, website, desktop virtual screen',
    category: 'figma',
    scope: 'main',
    tags: ['cursor', 'design', 'preview', 'viewport'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-design-preview
description: Design UI and validate in preview viewports
---

# Design + Preview Workflow

## Preview formats (Ocean.studio Preview panel)
| Mode | Size | Use |
|------|------|-----|
| website | full width | Responsive layout |
| mobile | 390×844 | Phone frame |
| desktop | 1280×800 | Virtual monitor |

Set: \`useAppStore.getState().setPreviewViewportMode('mobile')\`

## Design steps
1. Start from \`agent/templates/default-ui.tsx\`
2. Implement component; use CSS variables
3. Tell user to open Preview tab and switch viewport
4. Verify touch targets (mobile) and max-width (desktop)

## Figma
Use Figma MCP when connected; otherwise default-ui scaffold`,
  },
  {
    id: 'builtin.cursor-terminal',
    name: 'Cursor Terminal Agent',
    description: 'Shell commands with Ocean terminal routing (shell/cloud/native)',
    category: 'coding',
    scope: 'main',
    tags: ['cursor', 'terminal', 'shell'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-terminal-agent
description: Run terminal commands with correct backend routing
---

# Terminal Agent

## CRITICAL — read agent/skill.md first
- Electron desktop → \`shell\`
- Web browser → \`cloud\` (Google Cloud Shell)
- Android APK → \`native\`

\`\`\`typescript
const platform = await window.ocean.platform.get();
const terminalType = platform.preferredTerminal;
\`\`\`

## Workflow
1. Resolve terminal backend
2. Create session \`ocean-agent-terminal\`
3. Run commands; stream output to agent log
4. If confirmCommands: ask before rm, deploy, git push --force`,
  },
  {
    id: 'builtin.cursor-debug',
    name: 'Cursor Debug Loop',
    description: 'Reproduce → isolate → fix → verify systematic debugging',
    category: 'coding',
    scope: 'main',
    tags: ['cursor', 'debug', 'systematic'],
    source: 'builtin',
    verified: true,
    content: `---
name: cursor-debug-loop
description: Systematic debugging like Cursor agent debug mode
---

# Systematic Debug

## Loop
1. **Reproduce** — minimal steps; capture error verbatim
2. **Isolate** — narrow to file/function; add logging if verbose
3. **Hypothesize** — one theory at a time
4. **Fix** — smallest correct diff
5. **Verify** — run tests; confirm no regression

## Never
- Fix without reproducing (unless reproduceFirst=false)
- Change unrelated code in same pass
- Skip test run when runTestsAfter=true`,
  },
];

export const CURSOR_WORKFLOW_SKILL_COUNT = CURSOR_WORKFLOW_SKILLS.length;
