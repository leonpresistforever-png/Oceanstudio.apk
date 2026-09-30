---
id: debugging
name: Debugging & RCA
description: Hypothesis-driven debugging with reproducible evidence.
status: connected
source: bundled
---

# Debugging & RCA

Hypothesis-driven debugging with reproducible evidence.


## Ocean tooling you should actually use
- **Terminal**: `usr/bin/bash` with Ocean home as cwd; prefer non-interactive flags.
- **Packages**: `pkg install` / `pkg search` inside the Ocean prefix; verify with `which`.
- **Dispatch**: `ocean-app-task` and `dispatch_android_app` for headless Android intents when policy allows.
- **Plugins**: `ocean-plugin` lists and runs registered local capabilities; never invent npm packages.
- **HTTP**: `ocean-api` or curl from terminal to verify Functions you define in the agent drawer.
- **Forge**: confined workspace edits with `./gradlew :app:assembleDebug` and unit tests before claiming success.
- **Ports**: Runtime Ports UI plus `curl` to confirm listeners before telling the user a server is up.


## Operating procedure

When working on Debugging & RCA, start by reading the smallest set of files that define the behavior you are changing.

Document assumptions in chat only after you have verified them with terminal output or file reads.

Prefer extending existing Ocean helpers over introducing parallel abstractions that will diverge.

Keep diffs minimal: no drive-by reformatting, no unrelated dependency bumps, no speculative refactors.

If a build step fails, capture the full error log and fix the first root cause before layering more changes.

Use ripgrep or find under the workspace root before asking the user where code lives.

Match naming, import style, and error-handling patterns from neighboring classes.

When touching Android UI, validate on-device or with layout inspection; do not trust code-only guesses.

For network work, mirror drawer-configured HTTP functions with curl and record status codes.

Checkpoint risky edits through Forge before experimenting with signing or native binaries.

Remove temporary logging and feature-flag hacks before finishing; leave the tree cleaner than you found it.

Explain tradeoffs when multiple fixes exist; recommend one default and note rollback steps.

Treat user-visible copy as part of the fix: empty states, button labels, and error strings matter.

Respect App Access policy: do not bypass permissions with reflection or hidden APIs.

Batch verification: run unit tests and assemble tasks that the repo already documents.

When integrating external APIs, store secrets in BYOK or env files—not committed markdown.

Use slash commands from skill frontmatter ids so users can invoke this skill quickly.

If blocked by missing binaries, say which Ocean package provides them and how to install via pkg.

When working on Debugging & RCA, start by reading the smallest set of files that define the behavior you are changing.

Document assumptions in chat only after you have verified them with terminal output or file reads.

Prefer extending existing Ocean helpers over introducing parallel abstractions that will diverge.

Keep diffs minimal: no drive-by reformatting, no unrelated dependency bumps, no speculative refactors.

## Checklist before you say done

Re-ran the narrowest test that covers your change and captured output in chat.

Removed debug prints, toggles, and commented-out experiments.

Verified strings and dimensions against the greyscale Ocean palette.

Confirmed no secrets, tokens, or signing keys were pasted into markdown skills.

Left the UI without IllegalStateException from re-parented views.

Updated frontmatter status only when the user connects/disconnects the skill.

## Failure modes

Assuming a binary exists without `which` or Runtime Ports inspection.

Claiming HTTP success without status line and response snippet from curl.

Editing three modules when one focused file would fix the bug.

Using AlertDialog for multi-step create flows where bottom sheets exist.

Treating bundled skill text as optional flavor instead of operational law.

## Handoff notes

Summarize what changed, where, and how it was verified in one short paragraph.

List follow-up risks: permissions, migrations, or manual QA the user should run.

Point to skill id slash commands the user can invoke next session.

### Cycle 1

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 2

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 3

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 4

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 5

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 6

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 7

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.

### Cycle 8

Re-read the task, identify constraints for **Debugging & RCA**, then execute the smallest verifiable step. 
Use terminal transcripts as evidence. If UI is involved, switch tabs or screens deliberately to flush view hierarchies. 
When integrating with the agent drawer, rebuild lists instead of caching views. 
Cross-check Ocean hub entries: functions, tools, MCPs, and connected skills.
