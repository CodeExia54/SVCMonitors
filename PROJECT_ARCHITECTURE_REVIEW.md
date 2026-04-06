# SVCMonitors Project Architecture Review (KPM + Multiple Apps + Workflows)

This document summarizes the repository after a full structure pass.

## 1) What is in this repo

The repository is an end-to-end syscall monitoring stack for Android ARM64:

1. **KPM module** (`kpm/`) — kernel-side syscall hook and event producer.
2. **Android monitor app** (`android/`) — controller + collector + local event service.
3. **PC Viewer app** (`SVC_PC_View/`) — Flask/Socket.IO web dashboard and analysis UI.
4. **MX Android app** (`MX_APP/`) — separate Android application (Kotlin + Rust memory toolkit project).

Reference overview and stated architecture are consistent with the root/child README files.

## 2) App #1: Android monitor app (`android/`)

### Responsibilities
- Connect to KPM control interface (ctl0) through root shell/kpatch/ksud.
- Start/stop monitoring by commands (`enable`, `disable`), set UID and NR filter.
- Read events from binary stream first, fallback to JSON drain.
- Persist events into Room database for search/thread analysis.
- Optionally relay events to PC and/or run phone-side socket server.

### Main components
- `KpmBridge.kt`: command transport to kpatch/ksud (`status`, `sysnames`, `drain`, `set_nrs`, etc.).
- `MainViewModel.kt`: polling loop, command sequencing, DB write/search, monitoring state.
- `MainActivity.kt`: tabbed UI (Monitor/Filter/Events/Threads/Settings), app selection + filter ops.
- Room DB layer in `db/` (`SvcEventEntities.kt`, `SvcEventDao.kt`, mapper/db bootstrap).

## 3) App #2: PC Viewer (`SVC_PC_View/`)

### Responsibilities
- Receive event stream from Android app socket (or bridge/offline ingest).
- Normalize heterogeneous event payloads.
- Keep in-memory indexed event store for filtering/search/timeline.
- Serve browser UI via Flask static/templates + WebSocket push.

### Main components
- `server/app.py`: backend API + Socket.IO real-time feed + analytics helpers.
- `run_app_socket.sh`: one-command startup for adb forward + viewer launch.
- `android_bridge/adb_bridge.py`: optional ADB tail bridge mode.
- `static/index.html`: web dashboard front-end.


## 4) App #3: MX app (`MX_APP/`)

### What it is
- `MX_APP/` is also an Android app project in this repository.
- It is a separate codebase from `android/`, with its own Gradle config and app module.
- Based on `MX_APP/README.zh-CN.md`, it is a memory tool project with Kotlin + Rust components.

### Practical implication
- The repo is not only "KPM + 2 apps" in practice; it includes **at least 3 app projects** (`android/`, `SVC_PC_View`, and `MX_APP`).
- Current root workflow/docs are centered on `kpm + android + SVC_PC_View`; `MX_APP` appears parallel/adjacent.

## 5) KPM (`kpm/`)

### Responsibilities
- Hook ARM64 syscalls (inline/fp fallback), parse arguments deeply, and emit events.
- Maintain lock-free filters (`g_enabled`, target UID, NR bitmap).
- Keep ring-buffered events and support command-driven control/status/drain.
- Capture richer context (pid/tid/uid, args, desc, backtrace, clone metadata).

### Key notes
- Version marker in source: `8.2.0`.
- Supports wide syscall name table plus dynamic syscall name lookup fallback.
- Uses KernelPatch APIs and raw_syscall helpers for module control/output.

## 6) The two practical workflows you asked about

## Workflow A — Build/CI flow (KPM + APK)

- CI workflow file: `.github/workflows/build-kpm-apk.yml`.
- Shared local+CI script: `scripts/ci/build_kpm_and_apk.sh`.
- Steps:
  1. Setup JDK17 + Android SDK/NDK.
  2. Build KPM (`kpm/svc_monitor.kpm`).
  3. Build Android debug APK (`android/app-debug.apk`).
  4. Upload both artifacts in CI.

## Workflow B — Runtime data flow (capture + analysis)

1. KPM hooks syscalls and buffers events in kernel space.
2. Android app controls filters and reads event stream (bin preferred, JSON fallback).
3. Android app can expose a socket service on device.
4. PC script forwards port with ADB and starts Flask/Socket.IO server.
5. Browser dashboard receives incremental events through WebSocket.

In short: **KPM generates, Android orchestrates, PC visualizes/searches**.

## 7) Additional modules seen in repo

- `优化/` contains memory analyzer + detection reverser extensions (separate but related track).
- `KernelPatch/` is vendored framework/tooling dependency used to build/load KPM.
- `MX_APP/` is a separate Android app project (Kotlin + Rust) and should be treated as another first-class app in the monorepo.

## 8) Recommendations for team alignment

1. Keep documenting `android/ + kpm/ + SVC_PC_View/` as the primary syscall-monitor pipeline.
2. Document `MX_APP/` status explicitly in root README (scope, owner, release path).
3. Keep `scripts/ci/build_kpm_and_apk.sh` as the single source of build truth for the monitor pipeline.
4. Add one architecture diagram in root README mapping all entry points and modes.

