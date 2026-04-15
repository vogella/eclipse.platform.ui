# Renderer Performance Improvements

This document catalogs performance opportunities discovered in the
`org.eclipse.e4.ui.workbench.renderers.swt` bundle, ranked by **measured impact**
from real IDE sessions.

## Trace Sessions

| Property | Session 1 (baseline) | Session 2 (2026-04-16) |
|----------|---------------------:|------------------------:|
| Duration | ~120 s | 92.23 s |
| Trace events | 1,286 | 1,808 |
| Total hotspot time | ~2,170 ms | 2,824 ms |
| H10 fix applied? | No | No |

Session 2 was recorded on the same machine with a similar workload.  The H10
`setRedraw` batching fix (commit `dcb99d75` on branch
`wip-fix-showTab-setRedraw-batching`) was **not yet merged** into the trace
branch, so H10 numbers represent the unfixed baseline.

## Measurement Summary

Sorted by Session 2 total time descending.  Session 1 values in parentheses for
comparison.

| Rank | Hotspot | Count | Total ms | Max ms | P95 ms | Verdict |
|------|---------|------:|---------:|---------:|-------:|---------|
| 1 | H10 — `showTab` without `setRedraw` batching | 8 (9) | **1,817** (1,566) | 1,209 (977) | 901 | **Fix landed** (pending validation) |
| 2 | H07 — Per-item context creation in menu show | 403 (227) | **954** (568) | 373 (262) | 1.2 | **Fix next** |
| 3 | H03 — Unbatched `updateWidget`/`requestLayout` | 155 (115) | **25** (16) | 6.3 (3.1) | 0.76 | Low per call; new 6 ms spike |
| 4 | H05 — `ToolItemUpdater` linear scan | 309 (238) | **22** (17) | 3.3 (3.4) | 0.09 | Low — 12 items |
| 5 | H09 — `findElements` on window activate/deactivate | 9 (54) | **1.9**\* (1.1) | 0.15 (0.14) | 0.04 | Negligible |
| 6 | H01 — `findElements` for active CSS class | 19 (30) | **1.5** (2.4) | 0.23 (0.24) | 0.19 | Negligible |
| 7 | H02 — `findElements` for placeholders | 22+2 (21) | **1.5+0.3** (1.9) | 0.24 (0.23) | 0.09 | Negligible |
| 8 | H06 — Unbatched `scheduleManagerUpdate` | 753 (537) | 0 (count only) | — | — | **Fix** (753 fires in 92 s = 490/min) |
| 9 | H14 — Uncoalesced `RunAndTrack` callbacks | 39 (51) | 0 (count only) | — | — | Low volume |
| 10 | H13 — Full model scan at startup | 1 (1) | 0.15 (0.2) | 0.15 (0.17) | — | One-time |
| 11 | H12 — `synchCTFState` recursive walk | 2 (2) | 0.11 (0.1) | 0.10 (0.1) | — | Negligible |
| 12 | H11 — Limbo reparenting | 1 (1) | 0.09 (0.2) | 0.09 (0.2) | — | Single event |

\* H09 total appears high relative to per-call averages in the raw trace;
likely a Gemini summarization artifact.  Individual calls remain sub-ms.

**Key finding (unchanged):** H10 and H07 still account for **98%** of all
measured renderer overhead (64% and 34% respectively). The `findElements` tree
scans (H01, H02, H09) remain fast in practice.

### Notable ranking changes from Session 1

- **H07 moved closer to H10.** Total time rose 68% (568 ms -> 954 ms) with
  call count nearly doubling (227 -> 403). This is consistent with heavier
  menu interaction in Session 2.  H07 is now clearly the **dominant unfixed
  hotspot** once the H10 `setRedraw` fix is validated.
- **H03 showed a new 6 ms spike** (vs 3.1 ms max previously). Still a low
  total (25 ms) but worth monitoring — could indicate layout thrashing under
  specific tab arrangements.
- **H06 frequency increased** from 537 calls in ~120 s (268/min) to 753 calls
  in 92 s (490/min). The zero-duration measurement confirms these are
  trigger-only probes, but the rising cadence reinforces the case for enabling
  batching.
- **H09 count dropped sharply** (54 -> 9) because Session 2 involved fewer
  window activate/deactivate cycles. Time remains negligible.

### High-frequency hotspots (> 100 calls/min)

| Hotspot | Calls/min (Session 2) | Calls/min (Session 1) |
|---------|----------------------:|----------------------:|
| H06 `scheduleManagerUpdate` | 490 | 268 |
| H07 menu `contextPerItem` | 262 | 113 |
| H05 `toolItemUpdater` | 201 | 119 |
| H03 `updateWidget` | 101 | 58 |

H06 is zero-duration (trigger probe) but its 490 calls/min means 490
synchronous `mgr.update(false)` walks per minute when batching is off.

---

## Fix Status

### DONE: `showTab` `setRedraw` Batching (H10)

**Branch:** `wip-fix-showTab-setRedraw-batching` (commit `dcb99d75`)

The `showTab` body is now wrapped in `setRedraw(false)` / `setRedraw(true)` on
the target `CTabFolder`.  This was the #1 hotspot in both sessions
(1,566 ms / 1,817 ms total, worst single call 977 ms / 1,209 ms).

**Validation needed:** Re-run the trace with the fix merged to confirm H10
drops below the 16 ms frame budget.  Expected outcome: H10 total should drop
by 80-95%, making H07 the new #1.

---

## Priority 1 (next fix): Per-Item Eclipse Context Creation During Menu Show (H07)

**954 ms total — 34% of all hotspot time (up from 26%)**

**File:** `MenuManagerRendererFilter.java` (lines ~190-230)

`updateElementVisibility()` fired 403 times in 92 seconds.  Inside the loop
over `menuModel.getChildren()`, a new `IEclipseContext` is allocated and
disposed for every `MHandledMenuItem` / `MDirectMenuItem`.  The worst single
call in Session 2 took **373 ms** (children=18, ctxCreated=0), up from 262 ms.
Menus with 30-48 children dominate the tail latency.

**Top 5 slowest H07 events (Session 2):**

| Timestamp (ms) | Duration (ms) | Detail |
|----------------:|--------------:|--------|
| 2,803 | 373 | children=18, ctxCreated=0 |
| 2,800 | 367 | children=18, ctxCreated=0 |
| 19,057 | 63 | children=30, ctxCreated=2 |
| 19,049 | 49 | children=48, ctxCreated=0 |
| 19,043 | 24 | children=12, ctxCreated=0 |

**Recommendation:** Create one scratch `IEclipseContext` before the loop and
reset/reuse it per item, avoiding per-item allocation and disposal overhead.

---

## Priority 2: Unbatched `scheduleManagerUpdate` (H06)

**753 calls in 92 seconds (490/min) — not time-measured but very high frequency**

**File:** `MenuManagerRenderer.java` (lines ~1144-1179)

The batching mechanism (bug 467000 workaround) is disabled by default.  Each
call performs an immediate `mgr.update(false)`, walking and reconciling the
widget tree synchronously.  Frequency increased 83% between sessions.

**Recommendation:** Enable batching unconditionally and remove the
system-property guard.

---

## Lower Priority Items

### `updateWidget` / `requestLayout` (H03)

25 ms total across 155 calls. Average remains low (~162 us) but max jumped
from 3.1 ms to **6.3 ms**.  The new spike warrants investigation — likely a
layout cascade when multiple toolbar items update in quick succession.

### `ToolItemUpdater` Linear Scan (H05)

22 ms total across 309 calls. Still only 12 toolbar items registered. Numbers
track linearly with session activity.  Switch to `LinkedHashSet` as a
preventive measure.

### `findElements` Tree Scans (H01, H02, H09)

All three combined: **5.2 ms** across 52 calls (down from 5.4 ms / 103 calls).
Remain negligible at current model sizes.

---

## Platform-Specific SWT Workarounds

### W-1. Synchronous `layout(true, true)` on Windows in SashRenderer

**File:** `SashRenderer.java` (lines ~77-90)
**SWT Bug:** [558392](https://bugs.eclipse.org/bugs/show_bug.cgi?id=558392)

On Windows, `forceLayout()` uses `sashForm.layout(true, true)` — a synchronous,
recursive full layout — instead of the asynchronous `requestLayout()` used on
other platforms.  Not triggered in either Linux trace session, but expected to
be the **dominant hotspot on Windows** based on code analysis.

**What SWT should fix:** `Composite.requestLayout()` must correctly schedule a
deferred layout on Windows.

### W-2. Menu Manager Batching Guarded by Bug-467000 System Property

**File:** `MenuManagerRenderer.java` (lines ~1144-1179)
**Eclipse Bug:** [467000](https://bugs.eclipse.org/bugs/show_bug.cgi?id=467000)

SWT `Menu` does not support deferred/coalesced updates, forcing each
`MenuManager.update(false)` to immediately reconcile the widget tree (753 times
in Session 2, up from 537).

**What SWT should fix:** Provide a built-in batching mechanism for menu updates.
