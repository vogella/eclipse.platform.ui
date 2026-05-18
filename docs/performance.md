# Renderer Performance Improvements

This document catalogs performance opportunities discovered in the
`org.eclipse.e4.ui.workbench.renderers.swt` bundle, ranked by measured impact
from IDE sessions recorded with the `RendererPerfTracer` instrumentation on the
branch `wip-adding-performance-trace-code`.

## Methodology

Trace data is collected by the always-on `RendererPerfTracer` utility compiled
into the bundle on this branch. Each instrumented hotspot writes a CSV row
(`timestamp_ms, hotspot_id, duration_ns, detail`) to
`$HOME/renderer-perf-trace.csv`. On JVM shutdown the tracer also writes a
companion `renderer-perf-trace.csv.summary.txt` file with count, total ms,
max ms, p95 ms, and calls/min per hotspot, aggregated deterministically at the
source (no external script, no LLM summarization step).

All numbers in this document should be produced from that summary file or
directly from the CSV.

### Environment

Fill these in when publishing new trace results so sessions are comparable:

- Eclipse build (commit sha of `eclipse.platform.ui` branch)
- JDK version and vendor
- Operating system and version
- Display resolution and scale factor
- Workload description (what was opened, switched, resized, saved)
- Whether the first N seconds after startup are included or excluded

### Cold start vs steady state

The first few seconds after startup dominate several hotspots (H10, H13,
initial H12) and should be reported separately from steady-state interaction
so that the ranking does not conflate one-off startup cost with recurring
overhead. A simple convention: split the trace at the first user-initiated
event, or at a fixed boundary such as 10 s.

## Trace Sessions

| Property              | Session 1 (baseline) | Session 2 (2026-04-16) |
|-----------------------|---------------------:|------------------------:|
| Duration              | ~120 s               | 92.23 s                 |
| Trace events          | 1,286                | 1,808                   |
| Total hotspot time    | ~2,170 ms            | 2,824 ms                |
| Cold start separated? | No                   | No                      |

Session 1 and Session 2 pre-date the new summary pipeline. Both were
summarized by an external language model, and at least one anomaly
(an inflated H09 total relative to per-call averages) is visible in the raw
numbers. Future sessions should use the tracer's own `summary.txt` output.

## Measurement Summary

Sorted by Session 2 total time descending. Session 1 values in parentheses
for comparison.

| Rank | Hotspot                                     |     Count | Total ms      | Max ms        | P95 ms | Verdict                            |
|------|---------------------------------------------|----------:|--------------:|--------------:|-------:|------------------------------------|
| 1    | H10 `showTab` (now split into H10a lazy / H10b reparent) |    8 (9)  | **1,817** (1,566) | 1,209 (977) | 901    | Tail dominated by H10a cold first-show; `setRedraw` fix tried and reverted |
| 2    | H07 per-item context creation in menu show  |  403 (227)| **954** (568) | 373 (262)   | 1.2    | Top unfixed priority (see caveat)  |
| 3    | H03 unbatched `updateWidget` / `requestLayout` |  155 (115)| **25** (16)   | 6.3 (3.1)   | 0.76   | Low per call; new 6 ms spike       |
| 4    | H05 `ToolItemUpdater` linear scan           |  309 (238)| **22** (17)   | 3.3 (3.4)   | 0.09   | Low; 12 items                       |
| 5    | H09 `findElements` on window activate/deactivate | 9 (54) | **1.9**\* (1.1) | 0.15 (0.14) | 0.04 | Negligible                          |
| 6    | H01 `findElements` for active CSS class     |  19 (30)  | **1.5** (2.4) | 0.23 (0.24) | 0.19   | Negligible                          |
| 7    | H02 `findElements` for placeholders         | 22+2 (21) | **1.5+0.3** (1.9) | 0.24 (0.23) | 0.09 | Negligible                          |
| 8    | H06 unbatched `scheduleManagerUpdate`       |  753 (537)| 0 (count only)| n/a         | n/a    | Fix (753 fires in 92 s, 490/min)   |
| 9    | H14 uncoalesced `RunAndTrack` callbacks     |  39 (51)  | 0 (count only)| n/a         | n/a    | Low volume                          |
| 10   | H04 dirty-flag `ALL_SELECTOR` update        |  not reported in sessions 1/2 | count only | n/a | n/a | Add to next session's summary |
| 11   | H13 full model scan at startup              |   1 (1)   | 0.15 (0.2)    | 0.15 (0.17) | n/a    | One-time                            |
| 12   | H12 `synchCTFState` recursive walk          |   2 (2)   | 0.11 (0.1)    | 0.10 (0.1)  | n/a    | Negligible                          |
| 13   | H11 limbo reparenting                       |   1 (1)   | 0.09 (0.2)    | 0.09 (0.2)  | n/a    | Single event                        |

\* H09 total is inconsistent with per-call averages in the raw CSV. Suspected
language-model summarization artifact; individual calls remain sub-ms. This
is one of the reasons the next session should use the tracer's built-in
summary.

**Key finding (unchanged):** H10 and H07 account for roughly 98 percent of all
measured renderer overhead in these two sessions (64 percent and 34 percent
respectively). The `findElements` tree scans (H01, H02, H09) remain fast in
practice at current model sizes.

### Notable ranking changes from Session 1

- H07 moved closer to H10. Total time rose 68 percent (568 ms to 954 ms) with
  call count nearly doubling (227 to 403), consistent with heavier menu
  interaction in Session 2. With the H10 `setRedraw` fix ruled out, H07 is
  now the top actionable hotspot.
- H03 showed a new 6 ms spike (up from 3.1 ms max). Still a low total (25 ms)
  but worth monitoring; could indicate layout cascading when multiple toolbar
  items update in quick succession.
- H06 frequency rose from 537 calls in ~120 s (268/min) to 753 calls in 92 s
  (490/min). The zero-duration measurement confirms these are trigger-only
  probes, but the rising cadence reinforces the case for enabling batching.
- H09 count dropped sharply (54 to 9) because Session 2 involved fewer window
  activate and deactivate cycles. Time remains negligible.

### High-frequency hotspots (more than 100 calls per minute)

| Hotspot                       | Calls/min (Session 2) | Calls/min (Session 1) |
|-------------------------------|----------------------:|----------------------:|
| H06 `scheduleManagerUpdate`   | 490                   | 268                   |
| H07 menu `contextPerItem`     | 262                   | 113                   |
| H05 `toolItemUpdater`         | 201                   | 119                   |
| H03 `updateWidget`            | 101                   | 58                    |

H06 is zero-duration (trigger probe) but its 490 calls per minute means 490
synchronous `mgr.update(false)` walks per minute when batching is off.

---

## Fix Status

### H10: `showTab` `setRedraw` batching (abandoned)

A candidate fix on `wip-fix-showTab-setRedraw-batching` wrapped the `showTab`
body in `setRedraw(false)` and `setRedraw(true)` on the target `CTabFolder`.
It was merged into an aggregator build and measured. Result: **the fix did
not help and was reverted**.

| Metric            | No fix | Always batch | Reparent-only batch |
|-------------------|-------:|-------------:|--------------------:|
| Avg ms per call   | 24.6   | 34.0         | 54.2                |
| P95 ms            | 124    | 99           | 137                 |
| Max ms            | 695    | 1,087        | 1,462               |

Why it did not work: the tail in every recorded session (max 695 ms to
1,462 ms, P95 98 ms to 137 ms) is dominated by one or two very expensive
calls per session. Those calls happen on the **lazy-create path** inside
`showTab`, where `renderer.createGui(element)` constructs a new editor or
view and its initial layout. `setRedraw(true)` on exit forces that paint to
happen synchronously inside the method, which inflates worst-case time
rather than reducing it. On the reparent path (switching to an already
rendered tab) the common case is already in the single-digit ms range, so
there is no meaningful time for batching to eliminate.

### Revised instrumentation (this branch)

Because the lazy-create path and the reparent path have different cost
profiles and different causes, H10 has been split into two probes:

- `H10a_showTab_lazyCreate`: `showTab` calls where `element.getWidget()`
  is null and `renderer.createGui` is invoked. Expect a long right-tail
  governed by editor/view construction cost.
- `H10b_showTab_reparent`: `showTab` calls where the widget already
  exists and is being reparented or reselected. Expect low single-digit ms
  and stable P95.

Next session should report the two probes separately. `H10a` is not
actionable via `setRedraw` batching. `H10b` is the one to watch if we want
evidence that batching might help steady-state switching (though the
measurements so far suggest it does not).

---

## Priority 1: per-item Eclipse context creation during menu show (H07)

**954 ms total; 34 percent of all hotspot time (up from 26 percent).**

**Location:** `MenuManagerRendererFilter.updateVisibility(...)` (private
helper invoked from the public `updateElementVisibility` entry point).

`updateVisibility` fired 403 times in 92 seconds. The method iterates over
`menuModel.getChildren()` and for each `MHandledMenuItem` or `MDirectMenuItem`
performs work that includes creating and disposing an `IEclipseContext`.

**Top 5 slowest H07 events (Session 2):**

| Elapsed (ms) | Duration (ms) | Detail                        |
|-------------:|--------------:|-------------------------------|
|        2,803 |           373 | children=18, ctxCreated=0     |
|        2,800 |           367 | children=18, ctxCreated=0     |
|       19,057 |            63 | children=30, ctxCreated=2     |
|       19,049 |            49 | children=48, ctxCreated=0     |
|       19,043 |            24 | children=12, ctxCreated=0     |

**Caveat on the previously recommended fix.** An earlier draft of this
document proposed a shared scratch `IEclipseContext` reused across iterations,
based on the assumption that per-item context allocation dominated. The two
worst recorded calls (373 ms and 367 ms) both show `ctxCreated=0`, meaning
no contexts were created on that path. Allocation is therefore **not** the
dominant cost for the tail, and the scratch-context refactor is not
guaranteed to help. The actual hot code on the `ctxCreated=0` path is the
enablement evaluation loop around `ContributionsAnalyzer.populateModelInterfaces`,
the expression evaluation via `ExpressionContext`, and the handler service
lookups for each child. Root cause needs attribution (e.g., JFR or async
profiler) before committing to a fix shape.

---

## Priority 2: unbatched `scheduleManagerUpdate` (H06)

**753 calls in 92 s (490/min); count-only, not time-measured.**

**Location:** `MenuManagerRenderer.scheduleManagerUpdate(IContributionManager)`.

The batching mechanism introduced for bug 467000 is disabled by default via
the system property `eclipse.workaround.bug467000`. Without it, each call
performs an immediate `mgr.update(false)`, walking and reconciling the widget
tree synchronously. Frequency rose 83 percent between sessions.

**Recommendation:** enable batching unconditionally and remove the system
property guard. This is both a local fix (H06 in this doc) and a workaround
for an unresolved SWT gap (see "Cross-platform batching gaps" below).

---

## Lower Priority Items

### `updateWidget` / `requestLayout` (H03)

25 ms total across 155 calls. Average remains low (about 162 us) but max
jumped from 3.1 ms to 6.3 ms. The new spike warrants investigation; likely a
layout cascade when multiple toolbar items update in quick succession.

### `ToolItemUpdater` linear scan (H05)

22 ms total across 309 calls. Still only 12 toolbar items registered.
Numbers track linearly with session activity. Switching the backing
collection to `LinkedHashSet` is a preventive measure, not a current
bottleneck.

### `findElements` tree scans (H01, H02, H09)

All three combined: 5.2 ms across 52 calls (down from 5.4 ms across 103
calls). Remain negligible at current model sizes.

---

## Cross-platform batching gaps

### Menu manager batching guarded by bug-467000 system property (H06 / W2)

**Location:** `MenuManagerRenderer.scheduleManagerUpdate(IContributionManager)`.
**Eclipse Bug:** [467000](https://bugs.eclipse.org/bugs/show_bug.cgi?id=467000)

SWT `Menu` does not support deferred or coalesced updates, forcing each
`MenuManager.update(false)` to immediately reconcile the widget tree (753
times in Session 2, up from 537).

**What SWT should fix:** provide a built-in batching mechanism for menu
updates.

## Platform-Specific SWT Workarounds

### W-1. Synchronous `layout(true, true)` on Windows in `SashRenderer`

**Location:** `SashRenderer.forceLayout(...)`.
**SWT Bug:** [558392](https://bugs.eclipse.org/bugs/show_bug.cgi?id=558392)

On Windows, `forceLayout()` uses `sashForm.layout(true, true)`, a
synchronous, recursive full layout, instead of the asynchronous
`requestLayout()` used on other platforms. Not triggered in either Linux
trace session, but expected to be the dominant hotspot on Windows based on
code analysis.

**What SWT should fix:** `Composite.requestLayout()` must correctly schedule
a deferred layout on Windows.
