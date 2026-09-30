
# Ocean bundled skills

Skills live on disk at `files/home/.ocean/skills/<id>/SKILL.md` after first seed.

## Agent package CLIs (real, not npm fiction)

Ocean agents should use installed Ocean tooling:

| CLI | Purpose |
|-----|---------|
| `ocean-app-task` | Dispatch in-app tasks and headless Android workflows |
| `ocean-api` | Call configured HTTP surfaces from terminal |
| `ocean-plugin` | List/run registered local plugin capabilities |
| `pkg` | Install/search Termux-style packages in Ocean prefix |
| `ocean-apk-lab` | APK inspect/rebuild when installed |

Bundled markdown is copied from `assets/ocean/bundled-skills/` on seed version bumps.
