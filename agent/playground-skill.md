---
name: ocean-playground
description: Ocean.studio Playground — agents, research, media (image/video/3D), upscale, music, TTS, Active Bot, recording, MCP/plugin stores. Read before playground tasks.
---

# Ocean Playground Skill

Access: **Sidebar → Tools → Playground** or `setCenterView('playground')`.

## Categories (14)

| Category | Key tools | Platform notes |
|----------|-----------|----------------|
| Active Bot | Screen control, game companion, mood | EXE full; APK accessibility; web sandbox |
| Recording | 4K screen capture | EXE + APK only |
| Agent Tasks | Filesystem, batch jobs, watch folder | All platforms |
| Research | Web scrape, crawl, PDF, code search | Firecrawl MCP/plugin |
| Image Gen | Imagen, DALL·E, Flux, SDXL | Cloud providers |
| Video Gen | Veo, Runway, Pika, Kling | OAuth providers need proxy |
| Upscale | Real-ESRGAN, Topaz | Replicate / local |
| Image Editor | Inpaint, bg remove, style transfer | Provider APIs |
| **3D Studio** | Text→3D, 2D→3D | **EXE: open-source local GPU**; **APK: cloud free-tier** |
| TTS | Google, ElevenLabs, OpenAI, Azure | MCP + providers |
| Music Studio | Lyria, Suno, beat research, stems | Full instrument config |
| Schedule | Cron, reminders | Agent task queue |
| MCP Store | 130+ connectors, feature-tagged | Custom add with env template |
| Plugin Store | 110+ plugins, feature-tagged | Custom add with install guide |

## 3D Studio Platform Split

**Electron EXE (local):** TripoSR, InstantMesh, Shap-E, TRELLIS, Blender, Open3D, etc.

**Android APK (cloud only):** Meshy, Tripo, Luma, Replicate, Fal, HuggingFace — free tiers.

## Running Playground Tasks

```typescript
import { runPlaygroundAgentTask, runPlaygroundMediaJob } from '@/lib/playgroundRunner';

await runPlaygroundAgentTask({ title, prompt, scope: 'filesystem' }, workspacePath, activeFile);
await runPlaygroundMediaJob({ type: 'text-to-3d', prompt, provider: 'meshy' }, workspacePath);
```

## Agent Timeout

Right sidebar: **Agent Timeout** slider (30s–15m) + anti-timeout toggle. Enforced in Electron agent + provider inference.

## Marketplace Custom Add

MCP Store / Plugin Store → **Custom** button:
- Env requirements template
- Feature tag selection (agents, research, image, 3D, etc.)
- Persists in `playgroundStore.customMarketplaceTools`

## Active Bot

- Risk warning required before start
- Continuous sessions with heartbeat
- **Game Companion** — full configuration panel:
  - Game presets (Minecraft, Roblox, Fortnite, WoW, Idle)
  - Process/window/launcher identity
  - Control profiles (FPS, MMORPG, strategy, sandbox, idle)
  - Key bindings editor
  - Anti-AFK: interval, jitter, action chips, custom macro
  - Vision: provider + screenshot interval
  - Safety: pause on user input, max session hours, emergency stop key
  - Screen regions + gameplay.md auto-learning
- Config persists in `gameCompanionStore` (`ocean-game-companion`)
- Activity log: greetings, jokes, mood checks, game events

## Recording

- Hardware bypass encoding on Electron
- **Full config**: resolution, FPS, bitrate, codec/format, capture mode, region crop, audio source, mic gain
- **Manual start** button — user triggers recording explicitly
- **Background mode**: minimize to tray, keep process alive, power save blocker on Electron
- Start delay, auto-stop timer, custom output folder + filename template
- Android: notification panel controls (no overlay ball)
- Config: `recordingStore` (`ocean-recording`)

## Skills Store

- **Main workspace**: Sidebar → Tools → Skills Store
- **Playground**: Skills Store category (playground-scoped skills)
- 51+ builtin installable skills + 2000+ OSS marketplace seed
- Auto-populates on open; GitHub search for additional OSS skills
- Upload `skill.md` with YAML frontmatter
- Agent-created custom skills
- Installed skills inject into `buildAgentContext()` via `skillsStore`
