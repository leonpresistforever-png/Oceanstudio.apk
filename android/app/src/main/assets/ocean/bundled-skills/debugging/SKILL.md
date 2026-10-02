---
id: debugging
name: Debugging & Incident Analysis
description: Systematic root-cause isolation, crash dump analysis, race condition triage, and diagnostic reproduction.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Debugging & Incident Analysis

## 1. Mission and Scope
Systematically isolate, reproduce, and resolve defects, unhandled exceptions, ANRs, deadlocks, and silent data corruptions. Debugging applies scientific hypothesis testing and differential diagnosis to eliminate bugs at their source rather than masking symptoms with defensive null-checks or swallow-catch blocks.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Investigating unhandled exceptions, crash logs, or native SIGSEGV/SIGABRT signals.
  - Diagnosing non-deterministic race conditions, UI freezes (ANRs), or thread deadlocks.
  - Tracing state corruption across activity lifecycles or background services.
- **Do NOT Invoke When**:
  - Writing greenfield features or normal feature iterations (use Deep Coding).
  - Conducting general code formatting or style refactorings (out of scope).

## 3. Inputs to Gather
1. Full stack trace, logcat transcript, or terminal error output with timestamps.
2. Exact steps to reproduce, user inputs, and device/environment state.
3. Relevant class files along the call stack and suspect threading contexts.
4. Recent commit history or git diff that introduced the regression.

## 4. Tool Policy for This Domain
- Inspect call stacks and line references with `view_file` at the exact line numbers reported.
- Reproduce crashes using targeted unit tests or terminal commands with `run_command`.
- Never suppress exceptions with empty `catch` blocks or silent fallbacks.

## 5. Step-by-Step Operating Procedure
1. **Triage & Reproduce**: Confirm the error with a minimal reproduction sequence or failing test case.
2. **Isolate Root Cause**: Walk the stack trace backward from the exception point to the source of invalid state.
3. **Formulate Falsifiable Hypothesis**: Articulate why the failure occurs (e.g., null reference, detached view, race between background thread and UI main thread).
4. **Inspect Concurrency & State**: Audit variable mutability, thread boundaries (`runOnUiThread`, `Handler`, coroutines), and lifecycle states (`onPause`, `onDestroy`).
5. **Construct Minimal Fix**: Apply the most direct, elegant correction that restores correct program invariants.
6. **Verify Resolution**: Re-run the reproduction sequence and confirm the exception no longer occurs.
7. **Regression Guard**: Add an automated unit or integration test that asserts the correct behavior and prevents regressions.

## 6. Domain-Specific Heuristics and Algorithms
- **Bisection**: When a bug appeared after multiple changes, use `git bisect` to locate the introducing commit.
- **Temporal Invariants**: Verify that asynchronous callbacks do not access destroyed activities or views after `onDestroy`.
- **First Exception Dominance**: In cascading failure logs, always focus on the very first exception in the chain.

## 7. Evidence Requirements
- Stack trace before the fix showing reproduction.
- Terminal log or test execution output demonstrating clean execution after the fix.
- Code diff showing invariant restoration.

## 8. Failure Modes and Recovery
- *Non-Reproducible Heisenbug*: Increase logging granularity with atomic event tracers or run thread-sanitizer builds.
- *Native Crash Without Stack*: Inspect tombstone dumps in `/data/tombstones/` and symbolicate addresses with `addr2line`.
- *Fix Breaks Existing Tests*: Re-examine assumptions; fix must satisfy both legacy requirements and edge cases.

## 9. Security and Permission Boundaries
- Redact user secrets, passwords, and private tokens when extracting logcat logs or stack dumps.
- Do not bypass security checks to silence permission denials.

## 10. Acceptance Tests
1. Crash or error condition is completely eliminated under identical reproduction steps.
2. Regression test fails before the patch and passes cleanly after the patch.
3. No defensive hacks, stubbed returns, or swallowed exceptions introduced.

## 11. Handoff Format
- **Root Cause Summary**: Concise explanation of the defect mechanism.
- **Patch Applied**: Description of code changes and preserved invariants.
- **Verification Proof**: Test execution logs confirming the fix.

## 12. Small Worked Examples
- *Example*: Resolving `IllegalStateException: The specified child already has a parent`: Traced view attachment in bottom sheet presenter, introduced proper view detachment check before re-adding to dynamic layout container, and verified across configuration changes.
