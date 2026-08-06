---
name: ocean-cursor-workflows
description: Cursor-style agent workflows for Ocean.studio — composer, codebase context, review modes, preview viewports, and param presets.
---

# Cursor-Style Agent Workflows (Ocean.studio)

Ocean.studio integrates Cursor-inspired workflows in **Tools → Agent Workflows**. Each workflow installs skills and sets agent params.

## Available Workflows

| Workflow | Mode | Key params |
|----------|------|------------|
| **Codebase Chat** | review | contextDepth, includeTests |
| **Composer Multi-file** | auto | maxFiles, runBuildAfter |
| **Bugbot Review** | review | strictness, includeSecurity |
| **Design + Preview** | auto | previewViewport (website/mobile/desktop) |
| **Terminal Agent** | bypass | confirmCommands, shellType |
| **Debug Systematic** | review | reproduceFirst, logLevel |

## Agent Modes (Ocean.studio)

| Mode | Behavior |
|------|----------|
| `review` | Agent proposes; user confirms before terminal/commands |
| `auto` | Agent runs tools and terminal after plan |
| `bypass` | Minimal guardrails — full terminal access |

Set via `workspaceConfig.agentMode` or Model Config panel.

## Preview Viewports

When testing UI, switch preview format in the Preview panel:

- **Website** — full-width responsive
- **Mobile** — 390×844 phone frame
- **Desktop** — 1280×800 virtual monitor

```typescript
useAppStore.getState().setPreviewViewportMode('mobile');
```

## Workflow Application

```typescript
import { applyWorkflow } from '@/tools/cursorWorkflows';

await applyWorkflow('design-preview', {
  previewViewport: 'mobile',
  includeA11y: true,
});
```

## Skills Bundled

Workflows auto-install from Skills Store when applied:

- `builtin.cursor-codebase-chat`
- `builtin.cursor-composer`
- `builtin.cursor-bugbot`
- `builtin.cursor-design-preview`
- `builtin.cursor-terminal`
- `builtin.cursor-debug`

## Gateway / Cursor API Bridge

When Ocean proxy is running:

- `POST http://127.0.0.1:20128/cursor/v1/chat/completions` — Cursor IDE bridge
- Connect provider `cursor.cloud` for remote Cursor Cloud Agent sessions

## Multi-Agent Integration

Combine workflows with Multi Agent combos:

- **Design Studio** + Design Preview workflow → mobile viewport + designer skill pack
- **Full Stack Team** + Composer → parallel file edits with head synthesis

## Params Reference

```json
{
  "temperature": 0.7,
  "thinkingLevel": "medium",
  "maxTokens": 8192,
  "strictness": "normal",
  "agentMode": "review",
  "previewViewport": "website"
}
```

Workflow params merge into agent context `modelConfig` on apply.
