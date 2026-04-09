# Issue Audit Report (2026-04-09)

## Scope checked
- Android old app: `android/app/.../MainActivity.kt`
- Android modified app: `android_modified/app/.../FloatingMonitorService.kt`
- Server UI + backend: `SVC_PC_View/static/index.html`, `SVC_PC_View/server/app.py`

## Findings

### 1) Android modified app multi-select reverted every ~2s
**Root cause:** `startStatusUpdater()` continuously reloaded kernel status and overwrote in-progress UI selection (`selectedNrs`) with `status.nrList`, even before pressing **Apply selected NRs**.

**Where:** `android_modified/app/src/main/java/com/svcmonitor/app/FloatingMonitorService.kt`

**Fix implemented:**
- Added `hasPendingNrSelectionChanges` guard to prevent status poll from clobbering draft selections.
- Marked drafts dirty from checkbox/category checkbox interactions.
- Reset dirty flag after apply/sync/explicit set operations.
- Changed NR list highlighting to use `selectedNrs` (draft view), not stale `currentNrList`.

### 2) Server event detail showed many `unmapped` entries
**Observed causes:**
1. Resolver only used `ev.backtrace` array and ignored alternate payload forms (`bt_hex`, `fp_chain`).
2. Resolver hard-limited resolution to first 24 frames.
3. UI did not explain `unmapped` semantics.

**Where:** `SVC_PC_View/static/index.html`

**Fix implemented:**
- Added `extractBacktraceAddrs(ev)` parser with fallbacks:
  - `backtrace` array/string
  - `bt_hex`
  - `fp_chain`
- Removed 24-frame cap; resolve all parsed addresses.
- Updated detail panel to show parsed count and added explicit note that `unmapped` can be due to live `/proc/<pid>/maps` mismatch (process exited/map changed/stale PID).

### 3) “Show all solved addresses” gap on server UI
**Prior behavior:** Partial/format-dependent resolution only.

**Fix implemented:** Resolution now processes all parsed backtrace addresses from multiple event formats and displays them in detail panel.

## Not changed (still expected behavior)
- Server map resolution is against **current** device `/proc/<pid>/maps` via ADB (`/api/maps`), not historical event-time snapshots. If mapping changed after event capture, some addresses can still be legitimately `unmapped`.

