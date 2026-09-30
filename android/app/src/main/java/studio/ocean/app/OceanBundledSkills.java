package studio.ocean.app;

/** Production skill packs written to disk on first hub seed. */
final class OceanBundledSkills {
    static final String[][] PACKS = {
            {"deep-coding", skill(
                    "deep-coding", "Deep Coding", "connected", "bundled",
                    "Production-grade software engineering inside Ocean repos.",
                    body(
                            "## When to use\nApply for code changes, refactors, reviews, and build fixes.\n\n",
                            "## Workflow\n1. Read surrounding files before editing; match naming, imports, and error handling.\n",
                            "2. Keep diffs minimal—no drive-by reformatting or unrelated cleanup.\n",
                            "3. Run real verification (`./gradlew`, tests, curl) and cite output before claiming success.\n",
                            "4. Prefer extending existing helpers over new abstractions.\n\n",
                            "## Anti-patterns\n- Guessing APIs without reading sources.\n",
                            "- Placeholder skills or lorem instructions.\n",
                            "- Skipping tests because the change \"looks small\".\n"))},
            {"ux-design", skill(
                    "ux-design", "Design & UX", "connected", "bundled",
                    "Greyscale premium UI critique and implementation guidance.",
                    body(
                            "## Principles\nHierarchy first: one primary action per screen, generous 16–20dp padding, hairline dividers.\n\n",
                            "## Palette\nInk `#111111`, muted labels 11sp, body 14–15sp, titles 17sp. No teal Material defaults.\n\n",
                            "## Components\nThin outlined pill buttons—not full-width black slabs in empty space.\n",
                            "Inline panels over modal AlertDialogs for create flows on mobile.\n\n",
                            "## Deliverables\nConcrete layout tweaks, copy, empty states, and motion notes tied to existing Ocean components.\n"))},
            {"debugging", skill(
                    "debugging", "Debugging & RCA", "connected", "bundled",
                    "Hypothesis-driven debugging with reproducible evidence.",
                    body(
                            "## Process\n1. Define expected vs actual behavior.\n",
                            "2. Reproduce with the smallest steps; capture logs and stack traces.\n",
                            "3. Form hypotheses; add temporary instrumentation only when needed.\n",
                            "4. Fix root cause, not symptoms; re-run the failing path.\n\n",
                            "## Ocean specifics\nUse terminal output, `adb logcat` when on-device, and Forge checkpoints before risky edits.\n",
                            "Remove debug logging before finishing.\n"))},
            {"automation", skill(
                    "automation", "Automation Architect", "connected", "bundled",
                    "Reliable automations with idempotent steps and human checkpoints.",
                    body(
                            "## Design\n- Triggers: cron, git events, agent slash commands, or hub functions.\n",
                            "- Each step logs intent and result; failures stop the chain unless marked optional.\n",
                            "- Destructive actions require explicit user confirmation in UI or chat.\n\n",
                            "## Implementation\nPrefer shell scripts in Ocean home, versioned in git, with dry-run flags.\n",
                            "Document rollback and how to disable the automation quickly.\n"))},
            {"android-device", skill(
                    "android-device", "Android Device Ops", "connected", "bundled",
                    "Responsible use of Ocean device tools and App Access profiles.",
                    body(
                            "## Scope\nList apps, dispatch headless intents when appropriate, open UI activities when user must see state.\n\n",
                            "## Permissions\nNever assume Accessibility, MediaProjection, or camera—request only when a task needs them.\n",
                            "Respect `AppAccessPolicy` restrictions the user enabled.\n\n",
                            "## Safety\nPrefer read-only inspection before mutating device settings or installed packages.\n"))},
            {"terminal-runtime", skill(
                    "terminal-runtime", "Terminal & Runtime", "connected", "bundled",
                    "Operate Ocean bash, packages, and localhost services.",
                    body(
                            "## Commands\nUse non-interactive flags (`-y`, `--yes`) for long installs; background servers with logged PIDs.\n\n",
                            "## Packages\n`pkg install` inside Ocean prefix; verify with `which` and version flags.\n\n",
                            "## Ports\nInspect via Runtime Ports; confirm HTTP/noVNC with curl or browser tools before telling the user a service is up.\n"))},
            {"security-review", skill(
                    "security-review", "Security Review", "disconnected", "bundled",
                    "Threat modeling for mobile agents and local runtimes.",
                    body(
                            "## Focus areas\nSecrets in repos, IPC/intent surfaces, WebView bridges, plugin command injection, and forge signing keys.\n\n",
                            "## Method\nEnumerate trust boundaries, data flows, and attacker-controlled inputs.\n",
                            "Recommend least-privilege fixes with severity ordering.\n\n",
                            "## Output\nShort findings list with file references and concrete remediation—not generic checklists.\n"))},
            {"data-pipeline", skill(
                    "data-pipeline", "Data & Files", "connected", "bundled",
                    "Structured file workflows under Ocean home.",
                    body(
                            "## Practices\nSearch with ripgrep/find, diff before overwrite, validate JSON/CSV schemas.\n\n",
                            "## Artifacts\nKeep outputs under `files/home/` or workspace roots; avoid `/sdcard` unless user asks.\n\n",
                            "## Integrity\nChecksum large downloads; note encoding when transforming text files.\n"))},
            {"api-integration", skill(
                    "api-integration", "API Integration", "connected", "bundled",
                    "Compose HTTP tools, auth, and terminal verification.",
                    body(
                            "## Hub functions\nStore method, URL, params, headers, body, and auth in the agent drawer Functions tab.\n\n",
                            "## Testing\nMirror requests with curl from terminal; capture status, latency, and response shape.\n\n",
                            "## Errors\nDocument rate limits, auth refresh, and idempotency for write endpoints.\n"))},
            {"forge-self", skill(
                    "forge-self", "Ocean Forge", "connected", "bundled",
                    "Self-modify OceanStudio via confined Forge workspace.",
                    body(
                            "## Flow\nCheckpoint → edit in workspace → run tests/build → verify signing → report installability.\n\n",
                            "## Constraints\nPaths stay inside forge workspace; never exfiltrate signing material into chat logs.\n\n",
                            "## Validation\n`./gradlew :app:assembleDebug` and unit tests when touching Java/Kotlin.\n"))},
            {"research", skill(
                    "research", "Research & Synthesis", "connected", "bundled",
                    "Evidence-backed summaries for decisions.",
                    body(
                            "## Rules\nSeparate facts (tool output, files read) from inference.\n\n",
                            "## Structure\nQuestion → findings → options → recommendation with tradeoffs.\n\n",
                            "## Brevity\nDecision-ready bullets; link paths and commands used.\n"))},
            {"release-ops", skill(
                    "release-ops", "Release & CI", "disconnected", "bundled",
                    "Release hygiene for APK and package repos.",
                    body(
                            "## Checklist\nVersion codes, changelog, workflow triggers, artifact checksums, and rollback notes.\n\n",
                            "## CI\nConfirm green unit tests and assemble tasks before tagging.\n\n",
                            "## Communication\nState what shipped, what was skipped, and follow-up risks.\n"))},
    };

    private static String skill(String id, String name, String status, String source, String description, String markdownBody) {
        return "---\n"
                + "id: " + id + "\n"
                + "name: " + name + "\n"
                + "description: " + description + "\n"
                + "status: " + status + "\n"
                + "source: " + source + "\n"
                + "---\n\n"
                + "# " + name + "\n\n"
                + markdownBody;
    }

    private static String body(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) b.append(p);
        return b.toString();
    }
}
