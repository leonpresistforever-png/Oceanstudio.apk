---
name: ocean-extensions
description: Ocean.studio Extensions — VS Code / Antigravity-style IDE extensions with agent usage guides.
---

# Ocean.studio Extensions Skill

Extensions are **IDE-style add-ons** (like VS Code or Antigravity) — distinct from Plugins (runtime tools) and Skills (agent markdown instructions).

## Access

- **Sidebar → Tools → Extensions**
- Installed extensions inject **agent usage guides** into every agent session via `buildAgentContext()`

## Extension vs Plugin vs Skill

| Type | Purpose | Example |
|------|---------|---------|
| **Extension** | IDE feature (formatter, linter, language, AI) | Prettier, ESLint, Python, GitLens, Antigravity |
| **Plugin** | Runtime tool / MCP bundle | Filesystem MCP, Python Runner |
| **Skill** | Agent instruction markdown | `provider-skill.md`, custom skill.md |

## Marketplace Sources

1. **Builtin catalog** — 54 curated extensions (Prettier, ESLint, Python, Rust, Docker, Cursor, Antigravity, etc.)
2. **OSS seed** — 2400+ extension metadata entries
3. **Open VSX** — live search from https://open-vsx.org (auto-populates on open)

## Agent Context Injection

When user installs an extension, agent receives:

```
## Installed Extensions
### Prettier (esbenp.prettier-vscode)
Publisher: esbenp · v1.0.0 · formatter
[agentGuide markdown — how to use format document, when to format]
**Commands:** `esbenp.prettier-vscode.activate` — Activate Prettier
```

Agent should:
1. Check if relevant extension is **enabled** before using its features
2. Prefer extension commands over manual CLI when equivalent exists
3. Use extension formatters/linters for code quality tasks
4. Fall back to terminal if extension unavailable

## Install Types

| Type | Source |
|------|--------|
| `openvsx` | Open VSX registry (VS Code compatible) |
| `builtin` | Ocean curated catalog |
| `npm` | npm package extension |
| `custom` | User-uploaded package.json manifest |
| `agent` | Agent-created usage guide |

## Custom Extension Upload

Upload `package.json` or VS Code extension manifest with:
- `name`, `displayName`, `publisher`, `version`, `description`
- `contributes.commands` — agent maps to available actions
- `activationEvents` — when extension activates

## Popular Extensions (builtin)

- **Formatters:** Prettier, Biome
- **Linters:** ESLint, Ruff, markdownlint
- **Languages:** Python, Rust, Go, TypeScript, Java, C#
- **SCM:** GitLens
- **AI:** Cursor, Antigravity, Copilot, Claude Dev
- **DevOps:** Docker, Kubernetes, Terraform, Dev Containers
- **Testing:** Playwright, Vitest

## Store API

```typescript
import { useExtensionsStore } from '@/extensions/extensionsStore';
import { serializeExtensionsForAgent } from '@/extensions/types';

const enabled = useExtensionsStore.getState().getEnabled();
const agentBlock = serializeExtensionsForAgent(enabled);
```

Persisted in `ocean-extensions-store` localStorage.
