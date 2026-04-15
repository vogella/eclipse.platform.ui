# Renderer Performance Improvements

This document catalogs performance opportunities discovered in the
`org.eclipse.e4.ui.workbench.renderers.swt` bundle, ranked by **measured impact**
from a real ~2 minute IDE session (1,286 trace events).

## Measurement Summary

| Rank | Hotspot | Count | Total ms | Max ms | Verdict |
|------|---------|------:|--------:|---------:|---------|
| 1 | H10 — `showTab` without `setRedraw` batching | 9 | **1,566** | 977 | **Fix first** |
| 2 | H07 — Per-item context creation in menu show | 227 | **568** | 262 | **Fix** |
| 3 | H06 — Unbatched `scheduleManagerUpdate` | 537 | (count only) | — | **Fix** (537 fires in 2 min) |
| 4 | H05 — `ToolItemUpdater` linear scan | 238 | 17 | 3.4 | Low — only 12 items |
| 5 | H03 — Unbatched `updateWidget`/`requestLayout` | 115 | 16 | 3.1 | Low per call |
| 6 | H01 — `findElements` for active CSS class | 30 | 2.4 | 0.24 | Negligible |
| 7 | H02 — `findElements` for placeholders | 21 | 1.9 | 0.23 | Negligible |
| 8 | H09 — `findElements` on window activate/deactivate | 54 | 1.1 | 0.14 | Negligible |
| 9 | H14 — Uncoalesced `RunAndTrack` callbacks | 51 | (count only) | — | Low volume |
| 10 | H11 — Limbo reparenting | 1 | 0.2 | 0.2 | Single event |
| 11 | H13 — Full model scan at startup | 1 | 0.2 | 0.17 | One-time |
| 12 | H12 — `synchCTFState` recursive walk | 2 | 0.1 | 0.1 | Negligible |

**Key finding:** Two hotspots account for **98%** of all measured renderer
overhead: `showTab` (72%) and menu context creation (26%). The `findElements`
tree scans (H01, H02, H09) that looked expensive in code review turned out to
be fast in practice with the measured model size (70 placeholders).

---

## Priority 1: `showTab` Without `setRedraw` Batching (H10)

**1,566 ms total — 72% of all hotspot time**

**File:** `StackRenderer.java` / `LazyStackRenderer.java`

The `showTab` path performs multiple widget mutations (`setParent`, `setControl`,
`requestLayout`, `setSelection`, `adjustTopRight` → `pack()` + `requestLayout()`)
without suppressing intermediate repaints. The worst single call took **977 ms**
(nearly 1 second of frozen UI during startup). Five of nine calls exceeded 18 ms
(i.e., longer than a 60 fps frame).

**Recommendation:** Wrap the `showTab` body in `setRedraw(false)` /
`setRedraw(true)` on the target `CTabFolder`.

---

## Priority 2: Per-Item Eclipse Context Creation During Menu Show (H07)

**568 ms total — 26% of all hotspot time**

**File:** `MenuManagerRendererFilter.java` (lines ~190–230)

`updateElementVisibility()` fires 227 times in a 2-minute session. Inside the
loop over `menuModel.getChildren()`, a new `IEclipseContext` is allocated and
disposed for every `MHandledMenuItem` / `MDirectMenuItem`. Two calls at startup
took **258–262 ms each**, dominating early menu rendering. Menus with 42–46
children showed ~4 ms per call even after warm-up.

**Recommendation:** Create one scratch context before the loop and reset / reuse
it per item.

---

## Priority 3: Unbatched `scheduleManagerUpdate` (H06)

**537 calls in ~2 minutes — not time-measured but very high frequency**

**File:** `MenuManagerRenderer.java` (lines ~1144–1179)

The batching mechanism (bug 467000 workaround) is disabled by default. Each of
the 537 calls performs an immediate `mgr.update(false)`, walking and reconciling
the widget tree synchronously. With batching enabled, these would collapse into
far fewer actual updates.

**Recommendation:** Enable batching unconditionally and remove the system-property
guard.

---

## Lower Priority Items

The following showed measurable but modest impact in the trace:

### `ToolItemUpdater` Linear Scan (H05)

17 ms total across 238 calls. Only 12 toolbar items were registered, so the
ArrayList-based lookup is not yet painful. **Will scale poorly** with more
toolbar items (e.g., via many plugins). Switch to `LinkedHashSet` as a
preventive measure.

### Unbatched `updateWidget` / `requestLayout` (H03)

16 ms total across 115 calls. Individual calls are fast (~140 µs avg) but the
redundant layout pattern (especially the double `requestLayout` in
`childRendered`) could compound on slower machines.

### `findElements` Tree Scans (H01, H02, H09)

Despite looking expensive in code review, all three combined total only **5.4 ms**
across 103 calls. The model size (70 placeholders, ~30 active elements) is small
enough that the tree walks are sub-millisecond. These would become important with
**hundreds** of open editors.

---

## Platform-Specific SWT Workarounds

### W-1. Synchronous `layout(true, true)` on Windows in SashRenderer

**File:** `SashRenderer.java` (lines ~77–90)
**SWT Bug:** [558392](https://bugs.eclipse.org/bugs/show_bug.cgi?id=558392)

On Windows, `forceLayout()` uses `sashForm.layout(true, true)` — a synchronous,
recursive full layout — instead of the asynchronous `requestLayout()` used on
other platforms. Not triggered in this Linux trace session, but expected to be
the **dominant hotspot on Windows** based on code analysis.

**What SWT should fix:** `Composite.requestLayout()` must correctly schedule a
deferred layout on Windows.

### W-2. Menu Manager Batching Guarded by Bug-467000 System Property

**File:** `MenuManagerRenderer.java` (lines ~1144–1179)
**Eclipse Bug:** [467000](https://bugs.eclipse.org/bugs/show_bug.cgi?id=467000)

SWT `Menu` does not support deferred / coalesced updates, forcing each
`MenuManager.update(false)` to immediately reconcile the widget tree (537 times
in this session).

**What SWT should fix:** Provide a built-in batching mechanism for menu updates.
