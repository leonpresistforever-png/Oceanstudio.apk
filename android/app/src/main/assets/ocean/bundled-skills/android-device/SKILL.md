---
id: android-device
name: Android Device & Hardware Integration
description: Android platform API integration, hardware sensors, battery/thermal management, and system capability routing.
version: 2.0.0
required_tools:
  - run_command
  - view_file
  - replace_file_content
optional_tools:
  - search_web
---

# Android Device & Hardware Integration

## 1. Mission and Scope
Integrate with native Android platform capabilities, system services, hardware sensors, battery state monitors, and filesystem providers targeting Android API 28+ (minSdk 28). Ensure strict adherence to modern Android permission models, background execution limits, and hardware resource constraints.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Interfacing with Android system services (`ActivityManager`, `BatteryManager`, `ConnectivityManager`).
  - Handling device lifecycle events, screen orientation, memory pressure callbacks (`onTrimMemory`).
  - Managing scoped storage, SAF (Storage Access Framework), or native Bionic hardware acceleration (NNAPI, GPU, NEON).
  - Monitoring CPU thermal throttling and adjusting local inference workloads accordingly.
- **Do NOT Invoke When**:
  - Writing pure user interface styling without hardware interactions (use UX Design).
  - Executing standard Unix shell scripts inside the terminal (use Terminal & Runtime).

## 3. Inputs to Gather
1. Target Android API level (minSdk 28, targetSdk 34+).
2. Hardware profile: CPU cores, ABI (arm64-v8a), total RAM, available disk space, thermal status.
3. Required Android permissions (`Manifest.permission.*`) and user grant status.
4. Active system power save mode and battery state.

## 4. Tool Policy for This Domain
- Inspect Android manifest and Java/Kotlin system service wrappers using `view_file`.
- Check device hardware properties using `adb` or device shell commands where available.
- Never use non-SDK interface reflection blocked by Android hidden API restrictions (greylist/blacklist).

## 5. Step-by-Step Operating Procedure
1. **Capability Detection**: Check hardware feature availability via `PackageManager.hasSystemFeature()` before querying hardware sensors.
2. **Permission Verification**: Check `ContextCompat.checkSelfPermission()`. If missing, initiate runtime permission request with clear user rationale.
3. **Hardware Resource Check**: Inspect battery percentage and thermal status via `PowerManager.isPowerSaveMode()` and thermal listener callbacks.
4. **Service Binding & Lifecycle**: Bind to Android system services with lifecycle awareness. Unregister listeners in `onPause` or `onStop` to prevent battery drain.
5. **Memory Management**: Listen for `ComponentCallbacks2.onTrimMemory()`. When trim level is `TRIM_MEMORY_RUNNING_CRITICAL`, gracefully scale down cache sizes and suspend non-critical background processing.
6. **Hardware Acceleration**: Configure native libraries to detect ARM NEON or GPU compute capabilities at runtime.

## 6. Domain-Specific Heuristics and Algorithms
- **Thermal-Aware Throttling**: If device thermal status indicates throttling (`THERMAL_STATUS_SEVERE`), dynamically reduce local model inference thread counts by 50% or pause background batch tasks.
- **Battery Conservation**: Suspend heavy model downloads and continuous indexing when device battery falls below 15% and is not charging.
- **Storage Scoped Routing**: Prefer app-specific private storage (`getExternalFilesDir()`) to avoid complex runtime permission prompts for common cache and model files.

## 7. Evidence Requirements
- Logcat entries demonstrating clean service registration and unregistration.
- Verification that `onTrimMemory` events are properly handled without crash.
- Absence of hidden API reflection warnings in Android system logs.

## 8. Failure Modes and Recovery
- *SecurityException (Permission Denied)*: Gracefully degrade functionality, explain required capability to the user, and direct to app settings if permanently denied.
- *Memory Pressure Kill (LMK)*: Intercept `TRIM_MEMORY` and reduce resident memory footprint before the kernel terminates the process.
- *Sensor Unavailable*: Return a structured unsupported state instead of returning null or throwing exceptions.

## 9. Security and Permission Boundaries
- Never request permissions that are not strictly necessary for stated application features.
- Strictly adhere to Android platform security: never attempt root elevation or SELinux bypasses.

## 10. Acceptance Tests
1. System service calls operate cleanly on API 28 through the latest Android versions.
2. Listeners cleanly detach on activity lifecycle transitions without leaking memory.
3. Low memory and battery callbacks trigger appropriate resource preservation actions.

## 11. Handoff Format
- **Hardware Feature**: Subsystem integrated (RAM, thermal, battery, storage).
- **API Level & Constraints**: Minimum SDK requirements and permission status.
- **Verification Evidence**: Lifecycle and resource test results.

## 12. Small Worked Examples
- *Example*: RAM headroom verification in `LocalModelManager`: Queried `ActivityManager.MemoryInfo.availMem`, verified available RAM against model `minRamBytes`, and prevented model loading when remaining memory would trigger system OOM.
