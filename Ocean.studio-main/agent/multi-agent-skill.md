---
name: ocean-multi-agent
description: Parallel and collaborative multi-agent workflows — combine providers into teams with head agent coordination.
---

# Ocean.studio Multi-Agent Workflow

## Modes

### Parallel (head + sub-agents)
1. **Head agent** receives user prompt, decomposes tasks for the team
2. **Worker agents** execute sub-tasks with their own tools, skills, and provider APIs
3. **Head synthesizes** — confirms decisions, surfaces key results in chat
4. User sees head agent output; sub-agent work runs behind the scenes

### Collaborative (all at once)
1. All enabled agents receive the **same prompt and project context**
2. Each agent contributes from their provider's perspective simultaneously
3. Outputs **merge** into one unified response

## Creating a Combo

1. Sidebar → **Multi Agent**
2. Enable team toggle
3. Select or create a combo workflow
4. Add agents from connected providers (Codex, Antigravity, Claude Code, etc.)
5. Configure per-agent: role, task, system prompt, params, memory, tools, skills
6. Choose **Parallel** or **Collaborative** mode
7. Set head agent (parallel mode)

## Per-Agent Configuration

| Setting | Purpose |
|---------|---------|
| Provider + Model | Which API harnesses this agent |
| Role | head, worker, specialist, reviewer |
| Task assignment | Sub-task focus for parallel mode |
| System prompt | Agent-specific instructions |
| Parameters | temperature, maxTokens, thinkingLevel |
| Memory | contextWindow, maxMessages, postgres backend |
| Tools | Provider + MCP + plugin tools |
| Skills | Vault skills + injected skill.md files |

## Agent Context Injection

When multi-agent is enabled, `buildAgentContext()` includes:
```json
{
  "multiAgent": {
    "enabled": true,
    "comboName": "Full Stack Team",
    "mode": "parallel",
    "headAgent": { ... },
    "workers": [ ... ],
    "serializedCombo": "..."
  }
}
```

## Starter Combo: Full Stack Team

- **Codex Lead** (head) — architecture, synthesis
- **Antigravity** (worker) — deep reasoning, thinking mode
- **Gemini Flash** (designer) — UI polish; auto-fed skill.md + design-skill + default-ui.tsx
- **Claude Code** (specialist) — review, tests, docs

## Designer Sub-Agent Context Pack

Designer archetype sub-agents receive a **Cursor-style context pack** automatically:

| File | Purpose |
|------|---------|
| `agent/skill.md` | Platform workflow, terminal routing, preview |
| `agent/design-skill.md` | UI/UX principles, Figma MCP, handoff rules |
| `agent/templates/default-ui.tsx` | Default UI scaffold — extend, don't replace |

Plus builtin **Figma to Code** skill and `mcp.figma` assignment when MCP mode is assigned.

### When it applies

- **New sub-agents**: `createMemberFromArchetype('designer', …)` injects skill files into `member.skillFiles`
- **Runtime**: `enrichMemberForContext()` merges defaults before every sub-agent pass
- **Parallel routing**: prompts with ui/design/layout/figma keywords route to designer workers via `buildDesignerTask()`

### Design Studio workflow template

Use **Design Studio** template (Multi Agent → templates):
- Codex head coordinates
- UI Designer (designer archetype) ships from default scaffold
- Implementer wires logic
- Reviewer checks a11y and responsive behavior

## System Settings

- Max parallel workers
- Head synthesis prompt
- Collaborative merge prompt
- Global memory (session / vault / postgres / shared)
- Ocean proxy port for provider OAuth

## User Profile & Security

- Left sidebar footer shows Google profile picture (`user.photoURL`) when signed in with Google
- Profile settings: mask API keys, confirm destructive ops, session auto-lock
- Store: `useUserProfileStore` — persisted locally
