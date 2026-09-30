# Ocean package policy (strict)

This document defines what may enter the public `Oceanstudio-packages` APT tree via the git-distribution publisher (`scripts/index_all_staged.py`). It complements `ocean-packages/README.md`.

## Allowed packages

| Class | Requirement |
| --- | --- |
| **Upstream binaries** | Pinned official source builds (`build-source-packages.py`) or forensically verified imports with AArch64/bionic payloads under the Ocean prefix. |
| **Ocean-owned tools** | Real executables or libraries with a documented CLI purpose (`ocean-distro`, `ocean-pkg`, `proot-distro`, `ocean-tools`, …). |
| **Shard / multiplier packages** | Composable infrastructure that extends agent/runtime architecture (routing, auth shards, tool multiplexers, orchestration, storage shards). Each package must ship a working entry point, tests, and a manifest row in `ocean-packages/sources/shard-multiplier-manifest.json`. |

## Rejected packages (hard gate)

The staging quality gate (`ocean-packages/scripts/package_quality.py`) blocks promotion when any rule matches:

1. **Junk naming** — e.g. `ocean-term-media-NNN`, `ocean-pack-###`, `placeholder-*`, `visual-only-*`, sequential `ocean_<n>` fillers.
2. **Placeholder metadata** — description or `SKILL.md` containing placeholder/stub/visual-only language.
3. **Empty skills** — `SKILL.md` under 80 bytes or missing when the package claims to be a skill bundle.
4. **No entry point** — no executable under `$PREFIX/bin/` and no `Provides:` field.
5. **Duplicates** — same `Package`+`Architecture` with conflicting bytes without a version bump (existing indexer rule).

Indexed duplicates and junk names are reported by:

```bash
bash scripts/sync-packages-index-audit.sh
python3 ocean-packages/scripts/audit_package_index.py --root /path/to/Oceanstudio-packages --json /tmp/audit.json
```

See `docs/ocean-terminal/expansion-1000-plan.md` for promotion and priority expansion workflow.

## Building shard / multiplier packages

Only the git-distribution builder is supported for these packages (not the retired Termux cache route):

```bash
python3 ocean-packages/scripts/build-ocean-shard-packages.py
```

Artifacts land in `ocean-packages/staging/shard-multiplier/` for promotion into the public repository staging tree.

Current multiplier set:

| Package | Role |
| --- | --- |
| `ocean-shard-router` | Prefix-based JSON route multiplexer |
| `ocean-shard-auth-store` | Integrity-tagged auth secret shards |
| `ocean-shard-tool-mux` | Fan-out to multiple `ocean-plugin` handlers |
| `ocean-shard-agent-flow` | Sequential JSON workflow runner |
| `ocean-shard-blob-split` | Content-addressed blob split/join |

## Promotion workflow

1. Copy built `.deb` files into `Oceanstudio-packages/staging/…` preserving pool subdirectories.
2. Run `python3 scripts/index_all_staged.py --plan /tmp/plan.json` in the public repo (rejects junk staging).
3. Publish with `OCEAN_REPOSITORY_SIGNING_KEY` via `.github/workflows/promote-staged-packages.yml`.

Indexed package counts alone do not prove device executability; use `ocean-package-smoke-test` on hardware for acceptance.
