# Expansion plan (~7.5k indexed packages)

The public `Oceanstudio-packages` repository holds the canonical APT pool. The APK
bundles a signed snapshot under `android/app/src/main/assets/ocean/repository/` and
pulls updates from the same identity at runtime.

## What “1k plan” means in this repo

This is **not** a claim to ship 1,000 new compiled packages in one step. It is a
three-track program:

1. **Index hygiene** — `package_quality.py` rejects junk staging (media fillers,
   placeholder skills, packages without entry points). `index_all_staged.py` applies
   the gate before promotion.
2. **Promotion automation** — staged `.deb` files merge into the live index via
   `publish-apt-index` / `.github/workflows/promote-staged-packages.yml` on the APK
   repo, using `OCEAN_REPOSITORY_SIGNING_KEY`.
3. **Priority expansion list** — `sources/expansion-1000/target-packages.json` tracks
   upstream names still missing from the index (user-facing audio/X11/TTS, compilers,
   cloud CLIs). Each name needs an official Ocean source build before publication.

## Commands (APK repo)

```bash
# Build composable shard packages into local staging
python3 ocean-packages/scripts/build-ocean-shard-packages.py
python3 ocean-packages/tests/test-package-quality.py

# Audit live index + staging (sparse clone of public repo)
bash scripts/sync-packages-index-audit.sh

# Refresh priority gap list from live Packages.gz
OCEAN_PACKAGES_ROOT=/path/to/Oceanstudio-packages \
  python3 ocean-packages/scripts/plan-expansion-packages.py --write
```

## Commands (Oceanstudio-packages repo)

```bash
python3 scripts/index_all_staged.py --plan /tmp/plan.json
python3 scripts/index_all_staged.py   # requires OCEAN_REPOSITORY_SIGNING_KEY
python3 scripts/scan-package-index-issues.py --output /tmp/issues.txt
```

## Priority batch 1 (bugs / UX)

`alsa-utils` (`aplay`), `libnotify-bin`, `x11-apps` (`xeyes`), `espeak-ng`, and
`ffprobe` as explicit installable packages — see staged media bridge debs in the
public repo staging tree.

## Evidence labels

Follow `docs/HANDOFF_STATE.md`: indexed counts are `PUBLISHED`, not
`DEVICE-EXECUTED`. Promotion and catalogue sync must stay green in CI before raising
`--min-packages` on release builds.
