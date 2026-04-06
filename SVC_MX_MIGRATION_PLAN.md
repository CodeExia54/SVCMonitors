# SVC → MX Migration Plan (Use MX stability, replace MX feature set with SVC monitoring)

## What I understood from your request

You want to **stop relying on the current `android/` SVC app UI/runtime**, and instead:

1. Keep the **SVC backend capability** (KPM control + syscall collection + parsing + relay/log features).
2. Use **MX app infrastructure** (`MX_APP/`) as the host because it is stable in:
   - background execution,
   - service lifecycle,
   - connection/session stability.
3. Replace/remove MX-specific memory-tool features (search/modify/etc.) and build an **SVC-focused app** on MX layout.
4. Add/keep a **floating log view** in MX style so SVC logs can be viewed while app is backgrounded.

In short: **port SVC features into MX architecture/UI shell** rather than fixing SVC app in place.

---

## Proposed implementation strategy

## Phase 0 — Freeze requirements (before coding)

- Define MVP for "SVC-in-MX":
  - Start/stop monitor
  - UID target selection
  - NR include/exclude management
  - real-time event list + search
  - background collection + reconnect
  - floating log overlay
  - optional PC relay / device socket server
- Decide if old `android/` app stays as legacy module or is deprecated.

Deliverable: migration spec + feature parity checklist.

## Phase 1 — Build an SVC Core module inside MX

Create MX-side SVC domain package (new code, not copy-paste monolith):

- `svc/bridge/` → KPM command bridge (from SVC `KpmBridge` logic, adapted to MX root shell infra)
- `svc/parser/` → status/event parser + binary parser
- `svc/data/` → Room tables/DAO equivalent for syscall events
- `svc/repo/` → polling, buffering, retry, fallback (bin→json drain)

Goal: make SVC collection independent from current `android/` app classes.

## Phase 2 — Integrate with MX lifecycle/background model

Hook into MX stable service model:

- run SVC collector in MX foreground/background service layer
- persist last offsets/state safely
- add robust reconnect/backoff
- ensure no UI-bound polling dependency (collector continues when Activity gone)

Goal: background logging remains alive and resilient.

## Phase 3 — Replace MX screens with SVC screens (or SVC tab set)

Implement SVC UI using MX architecture/style:

- Monitor screen (status/enable/disable)
- Filter screen (UID + NR list/preset)
- Events screen (live list + search)
- Thread/trace view (if parity desired)
- Settings (superkey/relay/socket/tier options)

Goal: SVC functionality in MX UI framework.

## Phase 4 — Floating log view in MX

Use MX floating service/controller to host an SVC log panel:

- compact overlay widget (latest N events)
- expand/collapse full log stream
- quick actions (pause/resume, filter, clear)
- throttled rendering to avoid ANR/jank

Goal: stable floating logs while target app is foreground.

## Phase 5 — Decommission MX memory-tool functions

Gradually disable/remove:

- memory search/modify entry points,
- driver-specific pages/actions unrelated to SVC,
- unused adapters/controllers/dialogs.

Keep changes staged and reversible until parity is verified.

## Phase 6 — Validation and hardening

- long-run background soak test (2h/8h)
- service restart/device sleep/wake tests
- root shell failure/recovery tests
- high-volume syscall stress tests
- PC relay stability test

---

## Risk points to manage

1. **Architecture mismatch**: SVC app is currently tightly coupled to its own ViewModel/UI assumptions.
2. **Root command compatibility**: kpatch/ksud behavior must be normalized through MX shell layer.
3. **Performance**: floating overlay can degrade performance without batching/throttling.
4. **Data model migration**: SVC Room schema and MX repositories may conflict.
5. **Scope creep**: replacing all MX features at once is risky; do phased cutover.

---

## What I will do next (execution order)

1. Create a file-level mapping doc: **exact SVC classes → target MX classes**.
2. Implement `svc-core` package in `MX_APP` (bridge/parser/repo/db).
3. Wire background collector into MX service lifecycle.
4. Build SVC main screens in MX UI.
5. Add floating log overlay in MX service.
6. Remove/hide non-SVC MX modules after parity checks.

