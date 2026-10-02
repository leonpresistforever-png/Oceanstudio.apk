---
id: automation
name: Automation & Workflow Engine
description: Deterministic background workflows, cron scheduling, headless task dispatch, and script orchestration.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Automation & Workflow Engine

## 1. Mission and Scope
Design, execute, and monitor deterministic automated tasks, scheduled cron jobs, background pipeline workflows, and headless event dispatch within the Ocean application architecture. Ensure task idempotence, resource throttling, and state reconciliation across app lifecycles.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Scheduling recurring jobs (e.g., cron checks, log rotators, cache cleanups).
  - Orchestrating multi-step batch scripts, data migrations, or build automation pipelines.
  - Dispatching headless Android tasks via `ocean-app-task` or background WorkManager jobs.
- **Do NOT Invoke When**:
  - Building interactive user interface components (use UX Design).
  - Conducting interactive terminal debugging sessions (use Terminal & Runtime).

## 3. Inputs to Gather
1. Target workflow triggers: Schedule (cron expression), event trigger, or manual invocation.
2. Step dependencies, input parameters, and required output artifacts.
3. Execution constraints: Concurrency limits, maximum execution timeout, retry policies.
4. Error handling criteria: Fail-fast vs continue-on-error.

## 4. Tool Policy for This Domain
- Run automation scripts via `run_command` with deterministic flags and explicit timeouts.
- Store workflow state in structured formats (JSON, SQLite) with transaction safety.
- Never write infinite background polling loops in shell scripts; use native scheduling tools.

## 5. Step-by-Step Operating Procedure
1. **Workflow Definition**: Formalize task graph with clearly defined inputs, preconditions, and outputs.
2. **Idempotence Check**: Ensure re-running any step produces identical results without duplicate side effects.
3. **Execution Sandboxing**: Run scripts with isolated working directories and environment configurations.
4. **Progress & Health Monitoring**: Log structured progress events with timestamps and task IDs.
5. **State Persistence**: Record execution status, output hashes, and completion timestamps.
6. **Error Recovery & Cleanup**: On failure, execute rollback actions, release acquired locks, and notify the orchestrator.

## 6. Domain-Specific Heuristics and Algorithms
- **Exponential Backoff with Jitter**: When retrying network-bound automation steps, use exponential backoff with randomized jitter to prevent thundering herd problems.
- **Lease-Based Locking**: Use file or database locks with expiration timeouts to prevent concurrent execution of singleton tasks.
- **Atomic File Swaps**: Write output files to temporary paths and atomically rename (`renameat`) to guarantee partial writes are never observed.

## 7. Evidence Requirements
- Task execution transcript with timestamps for each step.
- Confirmation of state persistence and lock release.
- Proof of idempotence: duplicate execution completes safely without side effects.

## 8. Failure Modes and Recovery
- *Lock Staleness (Deadlock)*: Implement heartbeat checks; auto-expire locks held longer than maximum execution timeout.
- *Partial Failure in Multi-Step Job*: Roll back staged mutations or resume from last verified checkpoint using transaction logs.
- *Process Killed by Android OS*: Design jobs to resume seamlessly upon next app launch using persistent state trackers.

## 9. Security and Permission Boundaries
- Confine automation scripts to the application's assigned storage and runtime directories.
- Require user confirmation before executing workflows that delete files or alter security configurations.

## 10. Acceptance Tests
1. Script runs to completion and exits with code 0 on valid inputs.
2. Interrupted tasks successfully resume or cleanly abort without orphaned locks.
3. Multiple concurrent triggers do not cause data corruption or race conditions.

## 11. Handoff Format
- **Workflow Name & Trigger**: Identifier and activation conditions.
- **Execution Log**: Step-by-step transcript with timing and exit codes.
- **Artifacts Produced**: List of output files and persistent state records.

## 12. Small Worked Examples
- *Example*: Automated model cache cleanup: Defined daily job to scan downloaded model weights, compare against `models-manifest.json`, remove unreferenced temporary shards older than 48 hours, and record reclaimed disk space.
