---
id: local-models
name: Local Models
description: Local GGUF inference lifecycle, RAM sizing safeguards, real-time health checks, and offline agent routing.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Local Models

## 1. Mission and Scope
Oversee the lifecycle, verification, resource planning, and runtime execution of on-device GGUF language models. Provide zero-leakage, 100% offline inference by enforcing RAM headroom checks before loading, launching genuine native inference runtimes (llama.cpp/Bionic), executing live token-generation probes, and routing Ocean agent tasks through connected local endpoints.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Validating and updating the bundled model catalog (`models-manifest.json`).
  - Sizing available device RAM and selecting appropriate quantization/parameter tiers (~0.5B to ~4B).
  - Transitioning a downloaded model between DISCONNECTED and CONNECTED states.
  - Routing agent prompts to a local inference endpoint via `AuthStrategy.LOCAL`.
  - Verifying inference latency, token generation speed, and runtime memory footprint.
- **Do NOT Invoke When**:
  - Managing cloud provider API keys or cloud OAuth flows (use API Integration).
  - Writing general app UI layouts (use UX Design).

## 3. Inputs to Gather
1. Device hardware profile: Total RAM, currently available RAM (`ActivityManager.MemoryInfo`), CPU architecture (ARM64).
2. Selected model metadata: Parameter count, quantization format (Q4_K_M, Q8_0), disk footprint, and minimum RAM requirement.
3. Model file path and SHA-256 integrity status on disk.
4. Active local inference daemon status and local HTTP endpoint (`http://127.0.0.1:<port>`).

## 4. Tool Policy for This Domain
- Validate model catalog integrity via python or curl scripts before committing updates.
- Check real-time memory usage using system memory queries before triggering model loads.
- Strictly prohibit reporting a model as CONNECTED without verifying a live inference token probe.

## 5. Step-by-Step Operating Procedure
1. **Manifest Integrity Check**: Ensure all models in `models-manifest.json` have valid upstream URLs, licenses, SHA-256 hashes, and verified context sizes.
2. **RAM Sizing Preflight**: Query device available RAM. Compare available memory against `minRamBytes` specified in manifest. If available memory is insufficient (< 500 MB headroom remaining after load), abort and warn the user.
3. **Runtime Daemon Spawn**: Start the native llama.cpp server runtime bound strictly to `127.0.0.1:<port>`. Configure context length, thread count (matched to performance CPU cores), and memory mapping (`mmap`).
4. **Health & Readiness Probe**: Issue HTTP GET to `/health`. Ensure status 200 and readiness within timeout.
5. **Inference Verification Probe**: Execute a 1-token test prompt (`"Hi"`) via `/v1/chat/completions`. Verify that output tokens are generated and record roundtrip latency (`healthMs`).
6. **Provider Registration & Agent Routing**: Register the verified endpoint with `LocalModelManager` and `ProviderConnectionStore`. If `local_model_override` is enabled, route `OceanAgentRunner` tasks to the local model.
7. **Graceful Teardown**: Upon user disconnection, unload weights, terminate the runtime daemon, release native memory allocations, and update state to `INSTALLED` / `DISCONNECTED`.

## 6. Domain-Specific Heuristics and Algorithms
- **Dynamic Context Scaling**: On memory-constrained devices (< 4GB RAM), restrict context window to 2048 or 4096 tokens to avoid sudden OOM termination.
- **Thread Count Allocation**: Set inference threads equal to physical performance cores (`Runtime.getRuntime().availableProcessors() / 2`, clamped to 4) to prevent UI thread starvation.
- **Zero-Faking Contract**: Never switch UI badge to CONNECTED based purely on file existence on disk; a live inference probe must succeed.

## 7. Evidence Requirements
- Memory availability report before and after weight loading.
- Latency measurement of the 1-token health probe.
- Server log confirming successful GGUF weight mapping and thread configuration.

## 8. Failure Modes and Recovery
- *Low Memory (OOM Warning)*: Prohibit model launch, inform user of RAM shortfall, and recommend a smaller tier (e.g. 0.5B or 1.5B).
- *Corrupted Model File*: Verify SHA-256 against manifest; prompt user to re-download if hash verification fails.
- *Server Daemon Crash*: Capture process exit code, inspect stderr for unsupported GGUF tensor types, and clean up orphan sockets.

## 9. Security and Permission Boundaries
- Local inference must operate 100% offline with zero outbound network calls during token generation.
- Runtime server must bind strictly to `127.0.0.1` and reject non-loopback connections.

## 10. Acceptance Tests
1. Model files pass SHA-256 verification against the manifest.
2. RAM check rejects launch if available memory is insufficient.
3. Health check and 1-token inference probe succeed before status becomes CONNECTED.
4. Disconnect command fully frees native memory allocations and terminates server.

## 11. Handoff Format
- **Model Identified**: Model name, parameter size, and quantization type.
- **Inference Metrics**: Latency (ms), tokens/sec, and resident memory footprint.
- **Routing Status**: OceanAgentRunner local override status and endpoint configuration.

## 12. Small Worked Examples
- *Example*: Connecting `Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf`: Verified available RAM (3.2 GB available > 1.8 GB required), launched llama-server on port 8080, executed health check probe (latency 42ms), verified token generation, and activated Ocean agent local override.
