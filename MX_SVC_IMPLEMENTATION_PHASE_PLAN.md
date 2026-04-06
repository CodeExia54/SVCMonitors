# MX → SVC Full Implementation Plan (Main UI + Floating UI + App Rename)

This is the concrete execution plan to make MX behave as the SVC app while keeping MX stability.

## Current baseline (already done)

- MX build is now reproducible in CI + local script:
  - root workflow has `build-mx-app` job
  - `scripts/ci/build_mx_app.sh` builds `MX_APP` debug APK
- Initial SVC core scaffold exists in MX (`svc/bridge`, `svc/parser`, `svc/repo`, `svc/model`).

---

## Phase 0 — Branding + package migration (first)

Goal: rename MX identity to SVC safely before deeper feature wiring.

## 0.1 App display name
- Change `@string/app_name` from `MX` to `SVC Monitor`.
- Update user-facing strings that still reference Mamu/MX branding.

## 0.2 Android package/app id migration strategy
Because this is high-risk, do it in two steps:

1. **Step A (safe intermediate):** keep Kotlin package `moe.fuqiuluo.mamu` for now, but change application label/branding only.
2. **Step B (full rename):** migrate namespace + applicationId to SVC package (e.g. `com.svcmonitor.app`) after feature parity tests pass.

Why two-step: a full package rename touches manifest, all imports, resources, service declarations, and data paths.

Deliverable: branded SVC APK with unchanged internal package first.

---

## Phase 1 — SVC domain completeness in MX (core parity)

Goal: finish backend feature parity with old SVC app.

## 1.1 KPM bridge parity
Implement/verify all commands from legacy SVC bridge:
- `status`, `sysnames`, `enable`, `disable`, `uid`, `preset`
- `set_nrs`, `enable_nr`, `disable_nr`, `enable_all`, `disable_all`
- `tier2 on/off`, `clear`, `drain`

## 1.2 Event ingestion parity
- Keep bin-first + JSON fallback collector behavior.
- Add resilient polling controls (pause/resume during command execution).
- Add persistent offset + clean reset behavior.

## 1.3 Local persistence parity
- Add Room entities/DAO for SVC events in MX app.
- Add search and thread-focused query support similar to SVC app.

Deliverable: stable SVC data pipeline inside MX with parity-tested commands.

---

## Phase 2 — Main UI replacement (MX shell, SVC functions)

Goal: make MX main UI behave like SVC UI.

## 2.1 Main screens to implement
- Monitor dashboard (status, enabled state, event counters)
- Filter screen (UID + NR selection + preset)
- Events screen (live list + search)
- Threads view (thread-focused tracking)
- Settings (superkey, relay, socket server, tier2)

## 2.2 Cleanup plan for old MX memory features (main UI)
- Hide memory-search specific tabs/features behind a feature flag first.
- Remove obsolete flows after SVC equivalents are stable.

Deliverable: users can operate full SVC workflow from MX main UI.

---

## Phase 3 — Floating window SVC implementation (critical)

Goal: full SVC functionality in floating mode with background reliability.

## 3.1 Floating SVC log panel
Inside `FloatingWindowService` framework:
- live event stream panel (newest first)
- quick controls: enable/disable, clear, pause, filter UID/NR
- fast keyword search on recent events

## 3.2 Floating stability requirements
- keep collector independent from activity lifecycle
- throttle UI updates to avoid ANR/jank
- preserve service state on activity death/restart
- reconnect polling after service restart

## 3.3 Floating advanced features
- optional compact mode (icon + counter)
- expandable details (desc, args, ret, tid/pid)
- jump from floating event to full-screen event detail

Deliverable: SVC logging works in background and floating overlay reliably.

---

## Phase 4 — PC relay/socket integration in MX

Goal: preserve SVC ecosystem compatibility.

- Add optional device socket server mode (for PC viewer connection)
- Add event relay settings (host/port, enable/disable)
- Keep wire format compatible with existing PC viewer parser expectations

Deliverable: MX-hosted SVC can drive current PC viewer stack unchanged.

---

## Phase 5 — Full package rename + hard cleanup

Goal: finalize migration.

- migrate namespace/applicationId to final SVC package
- rename classes/resources/themes from MX/Mamu wording to SVC naming
- remove deprecated memory-search subsystems not needed by SVC
- update CI artifact names and docs to reflect final branding

Deliverable: clean SVC-branded app based on MX architecture.

---

## Phase 6 — Validation matrix (must pass before cutover)

## Functional parity
- all SVC ctl0 command paths work
- event capture and search parity
- floating log parity

## Reliability
- 2h/8h background soak with no collector stall
- screen off/on, process kill/restart recovery
- root shell transient failure recovery

## Compatibility
- works on Magisk + KernelSU/APatch variants
- PC relay + viewer flow remains functional

---

## Recommended start order (what I will do first)

1. **Phase 0 Step A**: branding rename (`app_name` etc.) without namespace change.
2. **Phase 1**: complete SVC bridge + DB parity.
3. **Phase 3 (early)**: implement floating SVC log panel quickly to prove your biggest requirement.
4. **Phase 2**: finish main UI parity.
5. **Phase 4/5/6**: relay, full package rename, and hardening.

This order minimizes risk while delivering background/floating reliability earliest.
