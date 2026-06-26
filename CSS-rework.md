# CSS Engine Rework Plan

Tracking issue: [#3980](https://github.com/eclipse-platform/eclipse.platform.ui/issues/3980).

Goal: trim the e4 CSS stack (`org.eclipse.e4.ui.css.core`, `org.eclipse.e4.ui.css.swt`, `org.eclipse.e4.ui.css.swt.theme`) from ~30k LOC / 263 classes to roughly two thirds, with no change to styling behaviour and no regression in shipped Eclipse themes.
The removed bulk is dead-API plumbing, the SAC/Batik parser wrapping, a redundant W3C DOM mirror, and one-class-per-property handler files.
CSS is internal API (every export is `x-internal` / `x-friends`), so internal signatures change freely; the public `IStylingEngine` / `IThemeEngine` contract is frozen.

## Status (2026-06-26)

| Phase | Work | State |
|---|---|---|
| 0 | Mechanical cleanups | merged (#3975–#3978) |
| 1 | Test safety net | merged (#3970, #3974, #3979, #3983) |
| 2 | Flatten engine / helper hierarchies | merged (#4040, #4042, #4048, #4049) |
| 3 | Drop SAC, replace Batik with internal parser | merged (#4092) |
| 4a | Delete DOM mirror + CSS2Properties facade | merged (#4112, #4115) |
| 4b | Value-record model, as four one-commit PRs: | in progress |
| | · replace SAC value model with `CssValues` records | merged (#4117) |
| | · migrate ~96 value consumers to the records | merged (#4120) |
| | · replace W3C computed-style cascade | staged on `css-cascade-internal-types`, PR next |
| | · retire `CSSValueImpl`, pin the W3C bridge | still to write |
| 4c | Drop the stale `org.w3c.css.sac` test dependency | draft PR |
| 5 | Collapse trivial property-handler classes | not started |
| 6 | Merge `css.swt.theme` into `css.swt` | not started |

Phases 0–4a are merged. Phase 4b removes the final SAC type (`LexicalUnit`) and is landing as four separate one-commit PRs off `master`; each phase now merges on its own rather than through the earlier `css-engine-rework` integration stack. The value-record model is in (#4117), the consumer migration is in (#4120), the cascade commit is staged on `css-cascade-internal-types`, and the `CSSValueImpl` retirement is still to write.
**Next: push and open the cascade PR, then the `CSSValueImpl` retirement PR**, then Phase 5, then Phase 6.

## Background

### Constraints (still binding)

- Preserve the engine contract callers use: `CSSEngine.applyStyles`, `matches`, `parseStyleSheet`, and the W3C `CSSStyleDeclaration` / `CSSValue` returned by `IStylingEngine.getStyle` / `IThemeEngine.getStyle`. Everything internal is fair game.
- Scope to the CSS subset shipped Eclipse stylesheets use: type / class / id selectors, `@import`, attribute selectors `=` and `~=`, child / descendant combinators, `:selected`, `:disabled`. The parser must still *accept* and silently discard `@media`, `@font-face`, `!important`, and `:hover` / `:focus` / `:active` (the machinery references them), but no shipped theme uses them.
- Each phase ships as one or a few PRs; no mega-PRs.

### Why a W3C facade survives

The original plan wanted the W3C value interfaces gone entirely.
That is blocked by frozen public API: `IStylingEngine.getStyle` / `IThemeEngine.getStyle` return W3C `CSSStyleDeclaration`, and downstream `propertyHandler` contributions receive values as W3C `CSSValue`.
So the value records and `CSSStyleDeclarationImpl` keep implementing the W3C interfaces as a thin, documented compatibility facade; everything inside the engine reads the records directly.
Dropping the facade would require an API-breaking revision of `IStylingEngine` / `IThemeEngine`, out of scope here.

## Phases 0–4 in detail

Compressed; the code is the source of truth. Decisions that still constrain later work are called out. Per-PR landing state for 4a/4b is in the Status table above.

- **Phase 0 — mechanical cleanups.** Removed `BootstrapTheme3x`, the unreachable `IOException` on `CSSEngine` String overloads, dead `SACConstants` entries, and unused serializer / color-converter config.
- **Phase 1 — test safety net.** Added `CSSEngineTest` (selector matching), `StyleSheetStructureTest` (parser round-trip), `PaddingTest`, and three selector integration tests (tab selection, `.active`, preference pseudo). The property-handler gap-fill was deliberately scoped down: only padding got a dedicated test; border geometry, lines-visible, the six CTabFolder visual-rendering handlers, and theme-element-definition were judged low-value to lock in given the rework redesigns their surface anyway. All new tests use only `CSSEngine` / `CSSStyleSheet` / `CSSRule` / `CSSStyleDeclaration` / `TestElement`, no SAC types, so they survive the parser swap.
- **Phase 2 — flatten hierarchies.** Merged `AbstractCSSEngine` into `CSSEngineImpl` and `AbstractCSSSWTEngineImpl` into `CSSSWTEngineImpl`; folded `ICSSPropertyHandler2` / `ICSSPropertyHandler2Delegate` into `ICSSPropertyHandler` via Java 21 default methods; deleted the unused `PropertyHelper`; replaced the vendored 3,205-line `URI` copy with a Require-Bundle on `org.eclipse.emf.common`.
- **Phase 3 — drop SAC, replace Batik (PR #4092).** An internal `Selector` AST (sealed interface + records) plus a `SelectorMatcher` replace the SAC selector layer and the 26 vendored `impl/sac/*` wrappers; a hand-written tokenizer + recursive-descent parser in `impl/parser/` replaces Batik and drops the `org.apache.batik.css` Require-Bundle. `@media` / `@font-face` / `@page` are parsed and discarded. The only remaining SAC type is the `LexicalUnit` interface in the value model, which Phase 4 removes. Validated by a byte-for-byte differential comparison against Batik over all shipped themes plus the full css.core / css.swt suites. See [Performance](#performance).
- **Phase 4 — replace the W3C DOM mirror with records.** `impl/dom/*` (~32 classes) and the cascade classes (`ViewCSSImpl`, `DocumentCSSImpl`, `CSSValueImpl`, ...) give way to `CssValues`, a sealed record hierarchy the parser builds directly, plus a plain rule list (`CSSStyleSheetImpl` over a sealed `CssRule`). The ~96 property handlers, converters, and SWT helpers pattern-match on the records (`CssUnit` enum, `CssNumeric` interface, `CssText.Kind`) instead of reading W3C type shorts. `CSSEngine.computeStyle(Element, pseudo)` replaces `getViewCSS().getComputedStyle(...)`. The W3C facade is retained (see Background). Zero SAC dependency remains. This lands as the 4a deletions (merged) plus four one-commit 4b PRs; see the Status table for per-PR state.

## Remaining work

### Phase 4c — drop the stale SAC test dependency

The production bundles carry no SAC reference after Phase 4b; the only `org.w3c.css.sac` trace left in the repo is in the `org.eclipse.e4.ui.tests.css.swt` test bundle.
Its `tkuiTestsToRefactor/` folder (legacy tkui tests, ~43 files) still imports `org.w3c.css.sac`, and the bundle MANIFEST keeps an `Import-Package: org.w3c.css.sac`.
That folder is not on the build path (`build.properties` compiles only `src/`), so it is dead weight, not running tests.
Delete `tkuiTestsToRefactor/` and remove the SAC `Import-Package`; afterwards nothing in this repo references SAC or Batik, so the `org.w3c.css.sac` bundle can leave the target platform (provided no bundle outside `eclipse.platform.ui` still needs it).
Independent of the cascade PR (#4122); lands directly off `master`.

### Phase 5 — collapse trivial property-handler classes

`RegistryCSSPropertyHandlerProvider` reads the `propertyHandler` extension point and dispatches into 74 handler classes; 15 to 20 are near-identical boolean / int / color setters wrapped in boilerplate.
The registry dispatch stays: clients override our handlers through the same extension point, so we cannot bypass it.
The consolidation happens inside the handler classes:

- One `GenericBooleanSWTHandler` in `plugin.xml` for every boolean SWT property (`maximize-visible`, `minimize-visible`, `mru-visible`, ...), dispatching on the property name to a small `BiConsumer<Widget, Boolean>` lookup map.
- Same shape for `GenericIntSWTHandler`, `GenericColorSWTHandler`, etc., where the property-to-setter mapping is regular.
- Non-trivial appliers (margins, paddings, preferences, CTabFolder rendering) keep their dedicated classes; their logic does not collapse cleanly.

`plugin.xml` keeps one entry per (element-class, property), so external overrides still resolve at the same granularity.
Out of scope: removing or deprecating the `propertyHandler` / `elementProvider` extension points or `RegistryCSSPropertyHandlerProvider` itself; they stay public for downstream RCP products that contribute custom handlers, including overrides of the new generic handlers.

Effort: 5 to 7 days, 2 to 3 PRs. ~30 wrapper classes removed, ~3 to 5 generic handlers added.
Medium risk: the override path is exercised by external contributors, so the generic handlers must not change observable behaviour for any single (element, property) pair.

### Phase 6 — merge `css.swt.theme` into `css.swt`

`css.swt.theme` is 7 classes / ~1,100 LOC of theme-manager wiring that does not justify its own bundle, MANIFEST, feature.xml entry, p2 IU, and test bundle.
Inline it as an internal package of `css.swt`.
This is logistics-heavy (feature.xml, target platform, downstream build files reference the bundle by name) more than code-heavy.
It is the only phase that removes a public bundle, so ship it with a deprecation cycle, and do it last when no other phase touches the bundle boundary.

Effort: 2 to 3 days, ~−200 LOC net. Medium risk (build-system blast radius).

## Performance

Phase 3's internal matcher initially regressed theme-swap time.
Two bottlenecks, both now fixed within PR #4092:

- **Dynamic parent resolution.** For descendant / child combinators the new `SelectorMatcher` called `element.getParentNode()` on the fly; in SWT that resolves the parent widget through `engine.getElement(parent)`, a HashMap lookup plus possible wrapper instantiation, run across millions of selector checks. Fix: `SelectorMatcher.matches` takes a pre-computed `Element[] hierarchy` built once per `getComputedStyle` (as the old `ViewCSSImpl` did); the no-hierarchy overload stays for callers without one.
- **Redundant styling passes.** A theme swap runs `Shell.reskin(SWT.ALL)` (styling each widget via the skin listener) and then `reapply()` → `applyStyles(shell, true)`, styling every widget a second time. Fix: a thread-local styling session (`startStylingSession()` / `stopStylingSession()` around `reapply()`) skips widgets already styled in the same swap.

Anything touching the matcher or `applyStyles` must preserve both, or the regression returns.

Benchmark (`CssThemeSwapPerformanceTest`, ~4,000 SWT widgets + 20 Java editors):

| | Regression baseline | Optimized |
|---|---|---|
| Median swap | 2091 ms | 1323 ms (−37%) |
| Min swap | 1891 ms | 1130 ms (−40%) |

The parser cutover itself makes parsing ~22% faster in isolation (≈14 ms vs 18 ms per full theme parse), but parsing is under 1% of a swap, so it does not move the end-to-end number; the defensible claim is that the cutover did not regress swap time and sped up parsing.

Phase 4b (value records) was measured the same way, before (4a tip) and after.
The engine-internal `applyStyles` cost fell from about 270 µs to about 211 µs per call (~20% cheaper), with no theme-swap regression.
A `-Xlog:gc` run on each confirmed no memory leak: zero Full GCs, sub-second total stop-the-world GC over a ~6-minute run, and live heap under 100 MB, identical before and after.
The wall-clock swap median swings ±1.5 s run to run on this bench and cannot gate the comparison, so the benchmark was reworked to report the engine `applyStyles` time per swap and per call as the stable metric (counters reset after warmup so they cover only measured swaps) and to time dark and light swaps separately since they are bimodal.
`@RepeatedTest` was tried and rejected: re-running the heavy workbench setup per repetition folds run-to-run variance into the result rather than averaging it out.

## Risks

- **Theme regressions.** The shipped themes are the real acceptance test. Keep the differential-parse check (every `.css` under `bundles/**/css/`, compare selector text + declaration counts) available when changing the parser or value model.
- **Closed-source RCP consumers.** Custom `CSSEngine` subclasses and `propertyHandler` / `elementProvider` contributions likely have external users. Both extension points stay public; Phase 6 (`css.swt.theme` inlining) is the only phase that removes a public surface, so ship it with a deprecation cycle.
- **Pseudo-element semantics.** The historical matcher quirk (`CSSPseudoClassConditionImpl.match` returned `!isStaticPseudoInstance(value)` when `pseudoElement == null`) is preserved by the internal matcher; keep it when touching pseudo handling, since a few `ElementAdapter` subclasses depend on static-pseudo registration.

## Out of scope

- New CSS features (`:hover`, `!important`, `@media` application).
- Removing or deprecating the `elementProvider` / `propertyHandler` extension points; they stay public for downstream custom handlers.
- Replacing the engine for non-SWT clients. The engine is SWT-only in practice; treat that as an invariant.

## Test infrastructure (reuse, don't extend)

Scaffolding already covers what later phases need:

- `TestElement` — fake DOM element with tag / class / id / attributes / hierarchy (`tests/org.eclipse.e4.ui.tests.css.core/.../util/`).
- `ParserTestUtil.parseCss(String)` returns a `CSSStyleSheet`.
- `CSSSWTTestCase` — Display + engine lifecycle, `createEngine(...)`, `createTestLabel(...)`.
- `EclipsePreferencesHandlerTest` — reference pattern for handler tests with Mockito; use it when adding Phase 5 handler tests.

Low-priority follow-up: replace `CSSSWTTestCase` inheritance (~25 widget tests) with a JUnit 5 `@RegisterExtension CssSwtEngine` callback, dropping the protected mutable `display` field and freeing those tests to extend other bases.
