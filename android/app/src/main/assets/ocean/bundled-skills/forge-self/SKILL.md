---
id: forge-self
name: Forge Workspace & Self-Evolution
description: Confined self-modification, AST transformations, build verification loops, and workspace rollback protection.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
  - write_to_file
optional_tools:
  - search_web
---

# Forge Workspace & Self-Evolution

## 1. Mission and Scope
Safely execute autonomous modifications, structural refactoring, automated code upgrades, and self-evolution of the Ocean application codebase. Protect developer workspace integrity through mandatory pre-modification snapshots, incremental compiler gates, automated rollback checkpoints, and strict confinement to project boundaries.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Executing systematic architectural refactors affecting multiple packages.
  - Adding or modifying bundled skills, plugin templates, or built-in system tools.
  - Upgrading dependencies, SDK targets, or build toolchains across Gradle modules.
  - Creating automated code repair patches with compile-and-verify feedback loops.
- **Do NOT Invoke When**:
  - Modifying files outside the project repository directory (strictly forbidden).
  - Executing destructive file operations without prior version control checkpoints.

## 3. Inputs to Gather
1. Working repository directory and clean git status (`git status -s`).
2. List of target files, classes, and interfaces to modify.
3. Build verification target (`./gradlew assembleDebug` or test runner).
4. Rollback checkpoint (git stash or dedicated working branch).

## 4. Tool Policy for This Domain
- Inspect project structures with `view_file`.
- Apply targeted contiguous edits using `replace_file_content`.
- Never execute wholesale rewrites of complex files when surgical patches are viable.
- Run automated build checks via `run_command` immediately after modifying source files.

## 5. Step-by-Step Operating Procedure
1. **Workspace Safety Checkpoint**: Verify git cleanliness. If unstaged changes exist, ensure they are committed or stashed before starting a multi-file refactor.
2. **Impact Boundary Mapping**: Analyze symbol dependencies, interface implementations, and callers across the project.
3. **Atomic Modification Batch**: Apply modifications package by package in logical dependency order (interfaces first, then implementations, then callers).
4. **Compile Gate Execution**: Run `./gradlew compileDebugJavaWithJavac` or equivalent fast compile task to verify syntactic and type correctness.
5. **Test Gate Execution**: Run relevant unit test suites to detect regressions.
6. **Rollback on Unrecoverable Failure**: If a modification sequence breaks invariants and cannot be cleanly repaired, rollback to the checkpoint commit.
7. **Audit & Cleanup**: Remove scratch scripts, temporary files, and debug logging before marking the refactor complete.

## 6. Domain-Specific Heuristics and Algorithms
- **Least Blast Radius**: Structure refactoring steps so that each intermediate step leaves the codebase in a compilable state.
- **Interface Segregation**: Prefer creating new focused interfaces over bloating existing core interfaces.
- **Automated Rollback Trigger**: If a patch fails compilation after 3 consecutive repair iterations, trigger an automated rollback rather than compounding broken assumptions.

## 7. Evidence Requirements
- Git diff showing minimal, auditable changes.
- Terminal output confirming successful compilation and test pass.
- Proof of zero leftover temporary files in repository root.

## 8. Failure Modes and Recovery
- *Compilation Failure on Transitive Symbols*: Check for unexported packages or missed method signature updates across subclasses.
- *Workspace Contamination*: Use `git checkout -- <file>` or `git clean -fd` to restore clean state.
- *Build Cache Invalidation*: Execute `./gradlew clean` if incremental compilation produces stale class artifacts.

## 9. Security and Permission Boundaries
- Strictly confine all operations to the project repository root. Never access parent directories or system paths.
- Enforce Rule 5: absolutely zero references to Termux paths in modified code or build manifests.

## 10. Acceptance Tests
1. Project compiles cleanly with zero warnings or errors.
2. Unit and integration tests pass 100% green.
3. Working tree diff is clean, documented, and free of extraneous modifications.

## 11. Handoff Format
- **Refactoring Scope**: Modules and packages transformed.
- **Verification Gate**: Compile and test status.
- **Git Checkpoint**: Final commit SHA or diff summary.

## 12. Small Worked Examples
- *Example*: Updating bundled skills architecture: Added new skills (`mcp-integration`, `local-models`), updated `OceanBundledSkills.java` registry, verified asset loading in `OceanAgentHubStoreTest`, and confirmed clean compilation.
