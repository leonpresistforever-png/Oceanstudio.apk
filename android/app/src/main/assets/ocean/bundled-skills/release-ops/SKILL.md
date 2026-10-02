---
id: release-ops
name: Release & CI
description: Release management, reproducible artifact assembly, cryptographic signing boundaries, and CI workflow validation.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Release & CI

## 1. Mission and Scope
Govern the preparation, validation, reproducible compilation, signing boundaries, and distribution of Ocean release artifacts. Ensure every build artifact is strictly verified against cryptographic checksums, passes automated preflight tests, maintains an auditable changelog, and adheres to zero-deception packaging standards.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Preparing an application release build (`assembleRelease` or `bundleRelease`).
  - Bumping semantic version numbers (`versionCode`, `versionName`) and updating changelogs.
  - Verifying package index manifests, SHA-256 integrity, or model download catalogs.
  - Designing or debugging GitHub Actions CI workflows and automated testing matrices.
- **Do NOT Invoke When**:
  - Developing features or fixing everyday source bugs (use Deep Coding).
  - Conducting threat modeling audits without release context (use Security Review).

## 3. Inputs to Gather
1. Semantic version bump target (major, minor, patch) and release notes delta.
2. Build configuration files (`build.gradle.kts`, `gradle.properties`, CI workflow YAMLs).
3. Checksum manifests and upstream asset hashes (e.g. `models-manifest.json`).
4. Signing configuration requirements (debug keystore vs production v2/v3 signing keys).

## 4. Tool Policy for This Domain
- Run build automation and verification scripts using `run_command`.
- Never commit private signing keys, keystore passwords, or release credentials to version control.
- Ensure all CI workflow scripts use deterministic dependency locking and verified action SHAs.

## 5. Step-by-Step Operating Procedure
1. **Preflight Health Check**: Validate that working tree is clean and all existing unit tests pass before initiating release steps.
2. **Catalog & Asset Verification**: Run preflight validation scripts (e.g. `preflight-model-catalog.py`) to confirm every bundled URL and SHA-256 checksum is live, valid, and authentic.
3. **Version Increment**: Update `versionCode` (monotonically increasing integer) and `versionName` (SemVer) in `build.gradle.kts`.
4. **Changelog Assembly**: Compile a user-facing changelog categorized into Features, Fixes, Security, and Breaking Changes.
5. **Clean Compilation**: Execute `./gradlew clean assembleRelease` in a reproducible build environment.
6. **Artifact Inspection**: Verify APK/AAB alignment via `zipalign -c` and check signature validity using `apksigner verify --verbose`.
7. **Rollback Plan Formulation**: Document immediate rollback criteria, tag points, and recovery procedures in case of deployment regression.

## 6. Domain-Specific Heuristics and Algorithms
- **Deterministic Reproducibility**: Ensure build outputs are invariant to build time, locale, and host directory layout.
- **Strict Checksum Enclosure**: Every binary, model, or toolchain archive distributed or referenced must have its SHA-256 pre-computed and hardcoded in the manifest.
- **Fail-Closed CI**: If any step in the verification pipeline fails, immediately halt the release pipeline and reject artifact staging.

## 7. Evidence Requirements
- Successful build execution output showing clean completion.
- Automated test logs and preflight verification scripts reporting 100% green.
- `apksigner` verification transcript confirming valid v2/v3 signatures.

## 8. Failure Modes and Recovery
- *APK Signing Failure*: Check keystore alias, validity dates, and algorithm requirements (RSA-2048+ with SHA-256).
- *Asset Checksum Mismatch*: Re-fetch upstream archive, verify vendor signature, and update manifest with genuine upstream hash.
- *CI Workflow Timeout*: Break monolithic test suites into parallelized shards or cache immutable build toolchains.

## 9. Security and Permission Boundaries
- Protect production signing credentials: sign artifacts strictly in isolated secure runners or offline signing machines.
- Prohibit inclusion of unverified third-party binaries or contaminated build trees.

## 10. Acceptance Tests
1. Gradle release build completes with zero errors and produces aligned APK artifacts.
2. `apksigner verify` confirms valid v2/v3 signatures on generated APKs.
3. Release preflight checks (including model and asset catalog URLs) pass with zero failures.
4. Git tag and changelog correspond precisely to release contents.

## 11. Handoff Format
- **Release Summary**: Version number, commit SHA, build artifact paths, and sizes.
- **Verification Matrix**: Checksum verification results and test suite passing status.
- **Deployment & Rollback**: Instructions for distribution and quick rollback steps.

## 12. Small Worked Examples
- *Example*: Validating `models-manifest.json` across 7 tiers before release: Executed `python3 scripts/preflight-model-catalog.py`, confirmed HTTP 206 range requests and valid GGUF magic bytes from HuggingFace, verified SHA-256 integrity, and confirmed zero 404 or broken links.
