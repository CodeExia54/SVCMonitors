# SVCMonitors Repo Review & Floating-First Stability Strategy

## 1) What exists now (quick audit)

### Existing SVC app (`android/`)
- The current SVC app centers on `MainActivity` + `MainViewModel`, with polling and command/control logic directly tied to the activity lifecycle.
- `AndroidManifest.xml` in `android/` defines only one activity and no long-running `Service` component.
- This architecture is good for foreground/manual control, but weak for **"stay alive in background"** behavior.

### Existing MX app (`MX_APP/`)
- MX already implements the shape you want:
  - `FloatingWindowService` runs as a foreground service and returns `START_STICKY`.
  - It sets up notification channel + `startForeground(...)` early.
  - It has dedicated overlay/floating UI controllers and state synchronization.
- Manifest permissions and service configuration in `MX_APP` already include the important Android pieces for foreground floating behavior.

## 2) Core problem to solve for SVC

To make SVC "floating + stable" (reduced disconnect when app backgrounded), split responsibilities:

1. **Core monitoring service (always-on while enabled)**
2. **UI surfaces (main screen and optional floating overlay) that can attach/detach safely**

Right now, your SVC implementation has too much monitoring control flow living at activity level.

## 3) Recommended target architecture

Use a 3-layer model:

### A. Monitoring Engine Layer
- Keep KPM communication (`KpmBridge`) and parsing (`BinEventParser`, `StatusParser`) here.
- Move poll loop ownership from `MainViewModel` into a service-owned engine.
- Add state machine:
  - `IDLE`
  - `STARTING`
  - `RUNNING`
  - `DEGRADED` (fallback mode)
  - `STOPPING`
  - `ERROR_RETRYING`

### B. Foreground Service Layer (`SvcMonitorService`)
- New Android foreground service in `android/` module.
- Responsibilities:
  - own poll loop/job and all reconnection/retry logic
  - persist session config (UID, enabled NRs, preset)
  - expose event stream and health state via Binder + local IPC (or repository-backed DB + Flow)
  - restart resilience (`START_STICKY`) and optional boot-recovery policy

### C. UI Layer (Main + Floating)
- **Main screen** = full control and analytics panels.
- **Floating window** = quick controls/status (start/stop, target app, event count, last error, quick filter).
- Both UI surfaces consume service state; neither should directly own monitor lifetime.

## 4) Floating UI design recommendation

### Minimal floating panel (v1)
- Compact chip/icon states:
  - Gray = idle
  - Green = monitoring
  - Orange = degraded/fallback
  - Red = error
- Tap expands to mini card with:
  - Start/Stop toggle
  - Current target package + UID
  - Event throughput (events/s)
  - Last command result
  - "Open Full App" button

### Advanced floating panel (v2)
- Add quick preset switch (re_basic / net / file)
- Add quick "pause capture for 30s" action
- Add unread event badge and one-tap jump to event search in main app

## 5) Stability hardening checklist (important)

1. **Service-first lifecycle**
   - Monitoring never depends on activity alive state.
2. **Foreground notification quality**
   - persistent notification with real status text and action buttons.
3. **Backoff retries**
   - for KPM read failures, use exponential backoff with cap.
4. **Persistent ring buffer**
   - continue writing events to DB while UI detached.
5. **Heartbeat + self-heal**
   - detect stale polling loop and self-restart worker.
6. **Crash-safe session restore**
   - on service restart, restore prior UID/NR config and continue.
7. **Battery policy onboarding**
   - in-app guide for OEM battery optimization exemptions.
8. **Observable health metrics**
   - queue depth, parse failures, last successful poll timestamp.

## 6) Migration plan from current repo

### Phase 1 (low risk)
- Keep current SVC UI.
- Introduce `SvcMonitorService` and move polling from `MainViewModel` to service.
- `MainActivity`/`MainViewModel` become clients of service state.

### Phase 2 (floating integration)
- Reuse MX overlay patterns:
  - service bootstrap sequence
  - icon/fullscreen switch behavior
  - state manager pattern for UI-service sync
- Build a lightweight SVC-specific floating UI (do not copy all MX features).

### Phase 3 (product polish)
- Unify "main screen" and "floating" settings model.
- Add profiles and auto-restore of last monitoring session.
- Add reliability dashboard and export diagnostic bundle.

## 7) Concrete implementation suggestion (first code tasks)

1. In `android/app/src/main/AndroidManifest.xml`, add a new foreground service declaration for SVC monitor runtime.
2. Add `SvcMonitorService` that encapsulates:
   - command queue
   - polling engine
   - DB writer
   - health monitor
3. Refactor `MainViewModel.startPolling()` into repository/service subscription.
4. Add notification actions: Start, Stop, Open App.
5. Add optional floating permission onboarding and a minimal overlay card.

## 8) UX placement recommendation (your specific question)

You asked whether options should be on floating or main screen.

Recommended split:

- **Floating (quick operations only)**
  - start/stop
  - target app switch (recent list)
  - status + error
  - quick preset
- **Main screen (all heavy options)**
  - full NR selection
  - advanced filters/search/history
  - export, maps/symbol analysis, thread relation graph

This keeps floating usable and safe while preserving full power in the main app.

## 9) Why this design fits your repo now

- SVC side already has robust parser/DB/event model primitives.
- MX side already proves a practical floating-service implementation model.
- Reusing the **service + state manager + controller** pattern from MX will reduce risk and speed up delivery.
