---
id: terminal-runtime
name: Terminal & Runtime
description: Ocean autonomous runtime environment, process supervision, custom prefix isolation, and CLI diagnostics.
version: 2.0.0
required_tools:
  - run_command
  - view_file
optional_tools:
  - search_web
---

# Terminal & Runtime

## 1. Mission and Scope
Oversee process execution, shell lifecycle management, package management within the dedicated Ocean application filesystem prefix, and runtime port diagnostics. Enforce strict isolation from third-party runtime trees (Rule 5 Termux Decoupling) and ensure deterministic execution of native Android Bionic binaries.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Managing child process lifecycles, background daemon supervision, or PTY terminal sessions.
  - Inspecting runtime ports, network sockets, and localhost listener processes.
  - Configuring environment variables (`PATH`, `LD_LIBRARY_PATH`, `HOME`, `PREFIX`) for the Ocean application sandbox.
  - Debugging native binary execution issues, dynamic linker paths (`/system/bin/linker64`), or missing shared libraries.
- **Do NOT Invoke When**:
  - Writing Java/Kotlin UI layouts or Activities (use UX Design or Deep Coding).
  - Executing basic code edits inside the Android app project that do not involve the runtime environment.

## 3. Inputs to Gather
1. Target command line, process arguments, working directory, and environment variable requirements.
2. Active Ocean prefix directory (e.g., `/data/data/<app_package_id>/files/usr/`).
3. Process stdout/stderr output, exit codes, and signal codes (SIGSEGV, SIGABRT, SIGKILL).
4. Socket listening status and port allocations via `ss`, `netstat`, or internal runtime port checkers.

## 4. Tool Policy for This Domain
- Execute commands using `run_command` with non-interactive flags (`-y`, `--batch`, `PAGER=cat`).
- Never run unbounded blocking commands without timeouts or asynchronous task tracking.
- Do not hardcode paths to `/data/data/com.termux` under any circumstances (Rule 5 compliance).

## 5. Step-by-Step Operating Procedure
1. **Environment Initialization**: Establish required environment variables: `PREFIX`, `HOME`, `PATH` pointing to the Ocean binary directories, and `TMPDIR`.
2. **Package Lookup & Verification**: When a tool is required, verify presence via `which <cmd>`. If absent, inspect the package manifest and install the genuine upstream package into the application prefix.
3. **Execution & Supervision**: Launch processes with explicit argument arrays (avoiding shell escaping vulnerabilities). Monitor child process PID and handle I/O streams safely.
4. **Port & Socket Auditing**: If running a local daemon (e.g. `ocean-authd` or mock test servers), verify binding to `127.0.0.1` and probe the listening port via HTTP or socket ping.
5. **Clean Termination**: Ensure child processes respond to SIGTERM, falling back to SIGKILL on timeout to prevent zombie processes.
6. **Diagnostics & Reporting**: Capture exit status, elapsed execution time, and any dynamic linker diagnostic messages (`LD_DEBUG=all` when diagnosing linking issues).

## 6. Domain-Specific Heuristics and Algorithms
- **Bionic RPATH Enforcement**: Ensure all native binaries in the runtime utilize `$ORIGIN/../lib` or explicit RPATHs targeting the Ocean prefix, preventing dependency on system libc overrides.
- **Non-Interactive Execution**: Always pass `--noprofile --norc` and disable color escape sequences when piping CLI output to automated processing pipelines.
- **Process Orphan Prevention**: Track all spawned child PIDs in a runtime registry to ensure clean termination on app pause or crash.

## 7. Evidence Requirements
- Process exit code (must be 0 for successful operations).
- Terminal stdout/stderr transcripts demonstrating execution and output correctness.
- Socket binding verification proving localhost-only listener security.

## 8. Failure Modes and Recovery
- *Dynamic Linker Error (Library Not Found)*: Inspect library dependencies with `objdump -p` or `readelf -d` and ensure dependent `.so` files are located in the Ocean `usr/lib` path.
- *Port Already in Use (EADDRINUSE)*: Query active listeners, locate the conflicting PID, and terminate stale processes before re-binding.
- *Process Killed (Signal 9 / OOM)*: Inspect Android low-memory killer (LMK) status and reduce process memory footprint or worker concurrency.

## 9. Security and Permission Boundaries
- Confine all file system operations strictly within the application's private sandbox and external storage permissions.
- Absolute prohibition of Termux binaries, package archives, or environment references.

## 10. Acceptance Tests
1. Process launches, executes expected logic, and exits with expected status code.
2. Environment variables do not leak sensitive credentials or unauthorized paths.
3. Local network listeners bind strictly to loopback (`127.0.0.1`) and release sockets upon termination.

## 11. Handoff Format
- **Runtime Command Executed**: Full command string with sanitized parameters.
- **Exit Code & Timing**: Termination status code and elapsed execution time.
- **I/O Transcript**: Summary of stdout and stderr diagnostics.

## 12. Small Worked Examples
- *Example*: Verifying local auth helper daemon: Spawning `ocean-authd` on dynamic loopback port, verifying HTTP 200 response on `http://127.0.0.1:<port>/health`, executing callback test, and verifying graceful socket teardown.
