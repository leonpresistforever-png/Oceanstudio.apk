---
id: deep-coding
name: Deep Coding
description: Production-grade software engineering, root-cause compiler triage, and minimal surgical patch lifecycle for Ocean repositories.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
  - write_to_file
optional_tools:
  - search_web
---

# Deep Coding

## 1. Mission and Scope
Deliver correct, auditable, and production-hardened code modifications. Deep Coding governs the entire code change lifecycle: codebase reconnaissance, symbol reference tracing, minimal patch construction, iterative compiler loop resolution, and non-regression verification. Stubs, visual simulations, and premature declarations of completion are strictly banned.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Fixing functional bugs, race conditions, memory leaks, or build failures.
  - Adding or refactoring core business logic, protocols, or storage systems.
  - Cross-compiling C/C++ or optimizing native Bionic / Android runtime components.
- **Do NOT Invoke When**:
  - Task is pure visual mockup without logic (reject or route to UX Design).
  - Writing superficial placeholder implementations without underlying operational logic.
  - Conducting broad exploratory literature surveys without repository changes (use Research & Synthesis).

## 3. Inputs to Gather
1. Target repository commit hash, branch, and working tree cleanliness (`git status -s`).
2. Exact compiler or runtime error logs, stack traces, and reproduction sequences.
3. Relevant architectural constraints (e.g., Android minSdk 28, Bionic libc restrictions, Termux decoupling).
4. Direct upstream specifications (RFCs, official provider APIs, protocol documentation).

## 4. Tool Policy for This Domain
- Prefer `view_file` with precise line ranges over indiscriminate whole-file dumps.
- Always use `replace_file_content` for surgical, verifiable modifications to avoid rewriting entire files.
- Run deterministic build and test commands after every contiguous change before declaring task completion.
- Never write credentials, tokens, or private keys to source or revision control.

## 5. Step-by-Step Operating Procedure
1. **Reconnaissance**: Read the smallest set of files defining the faulty behavior. Locate entry points, call graphs, and invariants.
2. **Root-Cause Analysis**: Distinguish root cause from symptoms. Verify hypotheses against source code and build logs.
3. **Surgical Patching**: Write the minimal contiguous patch necessary to resolve the root cause. Preserve existing coding conventions and comments.
4. **Compile & Triage**: Run the project's build system (`gradlew`, `make`, `cmake`, or test runners). If compilation fails, isolate the first error, examine its AST/symbol context, and resolve it without reverting to hacks.
5. **Regression Verification**: Execute existing unit and integration suites to ensure neighboring subsystems remain unaffected.
6. **Documentation**: Add context-rich comments explaining non-obvious design choices, invariants, and edge cases.

## 6. Domain-Specific Heuristics and Algorithms
- **Rule of Locality**: If a defect can be repaired within the declaring class, never pollute callers or global singletons with defensive workarounds.
- **Fail-Fast Boundary**: Validate arguments and preconditions at module entry points rather than allowing malformed states to propagate deeply.
- **Surgical Minimality**: If two solutions exist, choose the one with the smallest auditable diff that completely solves the problem without accumulating technical debt.

## 7. Evidence Requirements
- Compiler output showing clean compilation (`BUILD SUCCESSFUL` or exit code 0).
- Automated test logs demonstrating passing assertions on the modified code paths.
- Clean `git diff` review confirming zero extraneous reformatting or unintended line changes.

## 8. Failure Modes and Recovery
- *Compiler Missing Symbols*: Check import paths, target SDK level, and transitive dependency scopes in build manifests.
- *Concurrent Modification / Race Condition*: Protect shared mutable state with atomic primitives, immutable copy-on-write snapshots, or mutex synchronization.
- *Regressed Sibling Tests*: Immediately rollback to last clean commit, re-evaluate assumptions, and construct a targeted fix addressing both cases.

## 9. Security and Permission Boundaries
- Respect the target application's sandbox and permission model. Never invoke unauthorized system capabilities.
- Enforce strict Rule 5 Termux decoupling: never reference `/data/data/com.termux` in build configs, scripts, or runtime paths.

## 10. Acceptance Tests
1. Source compiles without warnings or errors under the project build configuration.
2. Unit test suite passes 100% green with zero skipped or suppressed assertions.
3. Git diff is minimal, documented, and free of mockups or placeholder functions.

## 11. Handoff Format
- **Summary**: Concise explanation of what was broken, the root cause, and the exact fix applied.
- **Files Modified**: Explicit list of altered files with line numbers and rationale.
- **Verification Output**: Exact terminal transcript and test run results proving stability.

## 12. Small Worked Examples
- *Example*: Resolving an `IllegalStateException` caused by unparented view reuse by creating a defensive detach sequence before attaching to container, verifying via layout inspector and running activity unit tests.
