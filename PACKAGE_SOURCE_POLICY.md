# OceanStudio Package Repository & Boundary Policy

## 1. Single Authoritative Source of Truth
The canonical source of truth for all Ocean packages, recipes, package builders, source manifests, APT index generation, and package-owned test suites is:

**Repository**: `https://github.com/leonpresistforever-png/Oceanstudio-packages`  
**Authoritative Machine Pointer**: [`ocean-packages/CANONICAL_PACKAGE_SOURCE.json`](ocean-packages/CANONICAL_PACKAGE_SOURCE.json) (Current pinned commit: `7750ea037b9162c7da4f237e40416e3439f1181d`)

---

## 2. Ownership & Responsibility Separation

| Domain | Canonical Repository | Prohibited Actions in Other Repositories |
| :--- | :--- | :--- |
| **Package Recipes (`packages/*`)** | `Oceanstudio-packages` | NEVER author or edit recipes in `Oceanstudio.apk` |
| **Package Builders (`scripts/build-*.py`)** | `Oceanstudio-packages` | NEVER create package build tools in `Oceanstudio.apk` |
| **APT Index Generators (`scripts/index_all_staged.py`)** | `Oceanstudio-packages` | NEVER generate APT indices in `Oceanstudio.apk` |
| **Package Quality Gates (`scripts/package_quality.py`)** | `Oceanstudio-packages` | NEVER maintain quality gate logic in `Oceanstudio.apk` |
| **Android App, Agent, UI, Terminal Activity, Browser** | `Oceanstudio.apk` | App-only domain; consumes pre-built package catalogues |
| **Runtime Prefix & Bootstrap Integration** | `Oceanstudio.apk` | Consumes verified signed assets from canonical package releases |

---

## 3. Policy on In-Repo Package Snapshots
1. The `ocean-packages/` directory in `Oceanstudio.apk` is a **FROZEN, READ-ONLY GENERATED SNAPSHOT** retained strictly for offline self-rebuilding in Ocean Forge.
2. Under no circumstances may engineers or autonomous coding agents edit recipes, manifests, or builders inside `Oceanstudio.apk/ocean-packages/`.
3. Any required modifications to packages must be performed in `Oceanstudio-packages`, verified through its test suite, and then synced as a pinned release/snapshot.
4. Continuous Integration and pre-commit checks will reject commits that introduce unapproved package recipes into `Oceanstudio.apk`.

---

## 4. Architectural Rules (Non-Negotiable)
- **Zero Termux Contamination**: `/data/data/com.termux`, `TERMUX_PREFIX`, and Termux package repositories are strictly forbidden. All packages must use the dedicated Android prefix `/data/data/studio.ocean.app/files/usr`.
- **Authentic Upstream Provenance**: No dummy archives, placeholder binaries, or synthetic mocks. All packages must be compiled from verified upstream open-source codebases with SHA-256 integrity verification.
- **Autonomous & Independent**: The Ocean runtime is 100% self-contained and decoupled from third-party application ecosystems.
