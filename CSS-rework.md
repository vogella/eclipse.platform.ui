# CSS Engine Rework Plan

## Status (2026-06-11)

- **Phase 0 — mechanical cleanups: all merged.**
  - `BootstrapTheme3x` removal (#3975).
  - `IOException` removal from `CSSEngine` String overloads (#3976).
  - Dead `SACConstants` parser entries removed (#3977).
  - Unused CSS serializer classes and dead color converter config (#3978).
- **Phase 1 — test safety net: all merged.**
  - Phase 1 selector matching tests (#3970).
  - Phase 2 parser round-trip tests (#3974).
  - Padding gap-fill (#3979). Remaining handler gaps from the Phase 3
    audit in `css-testing.md` are scoped down; revisit only on regression.
  - CSS selector integration tests for tab selection, `.active` class,
    and preference pseudo (#3983, in review): pins three engine
    behaviours that the matcher / parser unit tests do not exercise
    end-to-end.
- **Phase 2 + Phase 3 step 1 — in flight on a single branch
  (`vogella/css-engine-rework`).** Seven commits stacked, intended to
  be pushed out as individual PRs once stable.
  - `AbstractCSSEngine` merged into `CSSEngineImpl`.
  - `AbstractCSSSWTEngineImpl` merged into `CSSSWTEngineImpl`.
  - `ICSSPropertyHandler2` and `ICSSPropertyHandler2Delegate` folded
    into `ICSSPropertyHandler` via Java 21 default methods.
  - Unused `PropertyHelper` and its self-test deleted.
  - The vendored 3,205-line `URI` copy replaced with a Require-Bundle
    on `org.eclipse.emf.common`.
  - Internal CSS Selector AST + matcher introduced (Phase 3 step 1
    foundation).
  - **Phase 3 step 1 wiring (this commit):** SAC→`Selectors`
    translator added at the parser-output boundary
    (`CSSDocumentHandlerImpl.startSelector`); `CSSEngine.matches` and
    `parseSelectors` switched to the internal `Selectors.Selector`
    type; `CSSEngineImpl.matches` delegates to `SelectorMatcher`;
    `applyConditionalPseudoStyle` rewritten to walk the internal AST;
    parser configured with Batik's stock `DefaultSelectorFactory` /
    `DefaultConditionFactory`; 23 vendored `impl/sac/*` selector and
    condition wrappers deleted; the dead
    `ExtendedDocumentCSS.queryConditionSelector` /
    `querySelector` (and the `SAC_*_CONDITION` int constants behind
    them) removed; `CSSEngineTest` and `SelectorTest` rewritten on
    the internal AST. `CSSDocumentHandlerImpl`,
    `DocumentHandlerFactoryImpl`, and `SACParserFactoryImpl` are the
    only `impl/sac/*` classes still standing; they go in step 2.
  Net ~−4,750 LOC plus +711 LOC of Selector AST scaffolding plus
  +160 LOC of translator; all existing tests pass.
  Phase 2 leftovers (helper consolidation, SAC-bound Abstract-class
  merges, factory layer collapse) deferred: helper consolidation
  is high blast radius, the SAC-bound work is Phase 4 collateral.
- **Phase 3 step 1 — drop SAC types from the engine:** complete on
  `css-engine-rework`. Engine has zero SAC selector/condition types
  in its public API or matcher; SAC selectors only appear at the
  parser boundary inside `CSSDocumentHandlerImpl.startSelector`,
  where the translator hands them to the internal AST.
- **Phase 3 step 2 — replace Batik with hand-written tokenizer:**
  complete on `css-engine-rework`, two commits as planned. Commit 1
  adds an internal `impl/parser/` package (tokenizer, `CssParser`,
  `LexicalUnitImpl`, `CssParseException`) not yet wired; it emits the
  same model the SAC/Batik path produced and was validated by a
  differential comparison against Batik over all shipped Eclipse
  themes (byte-for-byte identical) plus `CssParserTest`. Commit 2
  wires `CssParser` into `CSSEngineImpl`, replaces the `InputSource`
  overloads with a `parseStyleSheet(InputStream, uri)` method, and
  deletes the Batik `Require-Bundle`, the vendored SAC plumbing
  (`impl/sac`, `core/sac`), `SacTranslator`, `SACConstants`, and the
  SAC parser factories. `@media` / `@font-face` / `@page` are now
  fully discarded. The only remaining SAC type is the `LexicalUnit`
  interface used by the `impl/dom` value model; Phase 4 removes it.
  Gated by the full css.core (119) and css.swt (210) suites. Parser
  benchmark recorded below.
- **Phase 4 — DOM mirror replacement: complete on
  `css-engine-rework`** (three commits on top of the value-model
  records).
  - Earlier slices: removed the mirror classes the parser cutover
    orphaned (`CSSMediaRuleImpl`, `CSSPageRuleImpl`,
    `CSSFontFaceRuleImpl`, `CSSUnknownRuleImpl`, `CounterImpl`,
    `RectImpl`, `CSSPropertyListImpl`) and the dead `CSS2Properties`
    facade behind `CSSStylableElement.getStyle()`. Introduced
    `CssValues`, a sealed record hierarchy the parser builds directly,
    deleting `Measure`, `RGBColorImpl`, `CSSValueListImpl`,
    `CSSValueFactory` and `LexicalUnitImpl`. **Zero SAC dependency.**
  - **Consumer migration:** the ~96 property handlers, converters, and
    SWT helpers now pattern-match on the records instead of reading
    through `getCssValueType()` / `getPrimitiveType()` /
    `getFloatValue()` etc. The records got their final internal shape:
    a `CssUnit` enum and a `CssNumeric` interface replace the raw W3C
    type shorts, `CssText` carries an explicit `Kind`.
    `CSS2FontProperties` / `CSSBorderProperties` store `CssPrimitive`,
    retiring the `CSS2PrimitiveValueImpl` / `CSS2RGBColorImpl` shims.
    The old "widget-derived font size has null cssText" quirk that
    font definitions depended on is now an explicit `sizeFromCSS` flag.
  - **Cascade replacement:** `ViewCSSImpl` / `DocumentCSSImpl` /
    `StyleSheetListImpl` / `CSSRuleImpl` / `CSSRuleListImpl` /
    `MediaListImpl` / `AbstractCSSNode` / `ExtendedCSSRule` /
    `ExtendedDocumentCSS` / `CSSValueImpl` deleted. The engine holds
    the stylesheet list and rule cache itself;
    `CSSEngine.computeStyle(Element, pseudo)` replaces
    `getViewCSS().getComputedStyle(...)` (a deprecated `getViewCSS()`
    default remains as a binary-compatibility bridge).
    `CSSStyleSheetImpl` is a plain rule list over a new sealed
    `CssRule` (`CSSStyleRuleImpl` | `CSSImportRuleImpl`).
  - **Revised endpoint (W3C facade stays):** the original plan wanted
    the W3C value interfaces dropped from `CssValues` entirely. That
    is blocked by frozen public API: `IStylingEngine.getStyle` and
    `IThemeEngine.getStyle` return W3C `CSSStyleDeclaration`, whose
    `getPropertyCSSValue` hands values to external callers, and
    downstream `propertyHandler` contributions receive values as W3C
    `CSSValue`. So `CSSStyleDeclarationImpl` and the value records
    keep implementing the W3C interfaces as a thin, documented
    compatibility facade; everything inside the engine reads the
    records. Removing the facade would need an API-breaking revision
    of `IStylingEngine` / `IThemeEngine`, out of scope for this
    rework.
- **Phases 5–6:** not started.

Goal: trim the e4 CSS stack (`org.eclipse.e4.ui.css.core`,
`org.eclipse.e4.ui.css.swt`, `org.eclipse.e4.ui.css.swt.theme`,
~30k LOC across 263 classes) to roughly two thirds of its current size
while keeping shipped Eclipse themes working. The shipped CSS subset is
small (see `css-testing.md`), and most of the bulk is dead-API plumbing,
parser wrapping, and one-class-per-property handler files.

## Current state

- Three bundles, ~30k LOC, 263 classes. Bundle `css.core` is internal
  (`Export-Package: ...;x-friends:=` and `x-internal:=true` everywhere),
  so signatures can change.
- Parser today: Apache Batik 1.9.x (2017) accessed through W3C SAC
  (`org.w3c.css.sac` 1.3.0, last revised ~2003). 26 classes under
  `impl/sac/*` plus 3 façade classes under `core/sac/*` adapt SAC into
  the engine. ~40 files across the three bundles plus tests import SAC
  types directly.
- A second mirror layer (`impl/dom/*`, ~32 classes) reimplements W3C
  DOM-CSS types (`CSSStyleSheet`, `CSSStyleRule`, `CSSValue`, etc.) for
  internal use only. Nothing outside the engine creates or mutates those
  objects.
- A registry-driven handler dispatch (`RegistryCSSPropertyHandlerProvider`,
  ~1.1k LOC) reads two extension points (`elementProvider`,
  `propertyHandler`) that no in-tree contributor outside `css.swt` uses.
- 74 property-handler classes, most of them 40 to 50 line wrappers around
  one setter call (`CSSPropertyMaximizeVisibleSWTHandler`, etc.).
- Phase 1 of `css-testing.md` is merged: `CSSEngineTest` covers
  selector matching. Phases 2 (parser round-trip) and 3 (property
  handlers) are the safety net for the rest of this plan.

## Guiding constraints

- Preserve the high-level engine contract used by callers:
  `CSSEngine.applyStyles`, `matches`, `parseStyleSheet`. Internal types
  (`impl/dom/*`, `impl/sac/*`, registry providers) are fair game.
- Limit semantic scope to what shipped Eclipse stylesheets use: type /
  class / id selectors, `@import`, attribute selectors `=` and `~=`,
  child / descendant combinators, `:selected` and `:disabled`. Out:
  `@media`, `@font-face`, `:hover` / `:focus` / `:active`, `!important`.
- No regressions in shipped themes (`platform`, `dark`,
  `org.eclipse.ui.themes` contributions). Each phase ships behind the
  Phase 1 + 2 + 3 tests as the gate.
- Each phase ships as one (or a small number of) PR. No mega-PRs.

## Phases

The phases are roughly in landing order. Earlier phases unblock later
ones; risk grows as we move down.

### Phase 0 — small mechanical cleanups (independent)

Ship now, no design needed.

- Drop `throws IOException` from `CSSEngine.parseSelectors(String)`,
  `parsePropertyValue(String)`, and any other engine method whose String
  overload only throws because of a `StringReader` hop. The signature
  comes from SAC's `Parser.parseSelectors(InputSource)` and is a checked
  exception that can never fire. Bundle is internal; callers fix in one
  pass.
- Delete dead parser entries from `SACConstants` (`SteadyState`, the
  unused Flute CSS3 variant, etc.).
- ~~Drop the `BootstrapTheme3x` shim and any other Eclipse 3.x compatibility
  glue in `css.swt.theme`. The 3.x bridge runtime is no longer the
  expected target.~~ Done (#3975).
- Audit and delete unused converter / serializer classes under `css.swt`
  (the `converter` and `serializer` subpackages each have a couple of
  entries with no in-tree caller).

Effort: ~1 day. ~500 LOC removed. Low risk.

### Phase 1 — finish the test safety net (from css-testing.md)

- Phase 2: `StyleSheetStructureTest` for parser round-trip.
- Phase 3a: property-handler audit (no PR, just the gap list).
- Phase 3b: fill in handler tests using
  `EclipsePreferencesHandlerTest` / `CSSSWTTestCase` patterns.

Effort: 3 to 4 days, 2 PRs. ~1,000 LOC added. Without this in place the
later phases are flying blind.

### Phase 2 — flatten engine and helper hierarchies (low risk, no API impact)

Pure refactor. No behaviour change. Each bullet is its own PR.

- Merge `AbstractCSSEngine` (1,113 LOC) and `CSSEngineImpl` (~95 LOC)
  into a single class. Same for `AbstractCSSSWTEngineImpl` /
  `CSSSWTEngineImpl`. These hierarchies have one concrete subclass each.
- Collapse `Abstract*Selector` and `Abstract*Condition` classes whose
  only subclass is the concrete `*Impl`. Several of these go away
  naturally during the SAC swap; do whichever ones can be done now
  without touching SAC, leave the rest for Phase 4.
- Collapse the factory layers (`CSSSelectorFactoryImpl`,
  `CSSConditionFactoryImpl`, `DocumentHandlerFactoryImpl`) where there
  is only one concrete factory.
- Consolidate the 9 SWT helpers into 3 cohesive ones: `SwtCssColors`
  (current `CSSSWTColorHelper`), `SwtCssFonts`, `SwtCssWidgets`. Delete
  `PropertyHelper`, `CSSSWTHelpers`, `SWTStyleHelpers` after their
  callers move.
- Merge `ICSSPropertyHandler2` and `ICSSPropertyHandler2Delegate` into
  `ICSSPropertyHandler` using Java 21 default methods. The bundle uses
  `BREE: JavaSE-21`; this is mechanical.

Effort: 4 to 5 days, 4 to 5 PRs. ~2,000 LOC removed, ~25 classes
removed. Low risk.

### Phase 3 — drop SAC, then drop Batik

The original plan named "swap Batik" and "drop SAC" as one phase. They
are not the same change: empirically, Eclipse depends on Batik for
exactly one runtime artifact (`org.apache.batik.css.parser.Parser`,
loaded reflectively); everything else under our `impl/sac/*` is a
22-year-old vendored copy of Batik selector wrappers. So Phase 3
splits cleanly into two steps that are individually shippable.

**Step 1 — drop SAC types from the engine, keep Batik.**

The engine API today exposes SAC types
(`org.w3c.css.sac.SelectorList parseSelectors(...)`,
`boolean matches(Selector, Object, String)`), and the 26 vendored
classes under `impl/sac/*` implement SAC interfaces
(`ExtendedSelector`, `ExtendedCondition`) extended with
`match(Element, pseudo)` and `getSpecificity()`. Replace this with:

- An internal `Selector` AST as a sealed interface plus records:
  `TypeSelector`, `ClassSelector`, `IdSelector`, `AttributeSelector`,
  `PseudoClassSelector`, `CompoundSelector`, `DescendantSelector`,
  `ChildSelector`, `SelectorList`. Engine-internal, no SAC.
- A `SelectorMatcher` service with `boolean matches(Selector, Element,
  String pseudo)` that walks the new AST.
- A small translator that consumes the SAC selector trees the Batik
  parser still produces and emits the new internal AST. Lives at the
  parser-output boundary; called once per stylesheet load. Uses
  Batik's stock SAC `DefaultSelectorFactory` /
  `DefaultConditionFactory` instead of our vendored copies.
- `CSSEngine.matches` and `parseSelectors` change to return / accept
  the internal `Selector` type. Internal API only (`x-friends`); no
  external API break.
- Delete the 26 vendored classes under `impl/sac/*` and the 3 façade
  classes under `core/sac/*` once they are no longer reachable.

The Phase 1 selector-matching tests (`CSSEngineTest`, merged via
#3970) already assert through `engine.matches(...)`. They get rewritten
to drop the SAC `Selector` import and use the new internal type
directly. The Phase 2 round-trip tests do not touch selectors and stay
unchanged.

Effort: 4 to 5 days, 1 PR. ~600 LOC added (AST + matcher + translator)
and ~2,000 LOC removed (the 26 vendored wrappers and the 3 façades).
Net ~−1,400 LOC. Medium risk: specificity calculation must match
current behaviour exactly so cascade ordering does not shift.

**Step 2 — replace Batik with a hand-written CSS3 tokenizer.**

After step 1 the only Batik touch-point is the reflectively-loaded
`org.apache.batik.css.parser.Parser` class plus a thin SAC layer
around it (3 remaining `impl/sac/*` parser-plumbing classes, 6
`core/sac/*` façades). Replace all of that with a small hand-written
tokenizer + recursive-descent parser scoped to the CSS subset Eclipse
and downstream RCP applications use. That subset is not as small as
`css-testing.md` originally suggested: in addition to type / class /
id / `:pseudo` / attribute / child / descendant selectors, the engine
machinery actively supports `:focus` (Control), `:active` (Shell),
arbitrary pseudo-classes through `isPseudoInstanceOf`, and parses
(without applying) `!important`, `@media`, `@font-face`. The new
tokenizer must accept all of this even though no in-repo stylesheet
uses the dormant features.

Rough sizing:

| Piece | LOC |
|---|---|
| Tokenizer | 250–350 |
| Selector parser | 150–250 |
| Declaration / value parser | 150–250 |
| AST records | 100–200 |
| Specificity calculation | ~30 |
| **Total** | **~700–1,100** |

Two commits, each shippable as its own PR.

1. **Add the new parser, not yet wired.** New package
   `impl/parser/` with the tokenizer, selector parser (emits
   `Selectors.SelectorList` directly — no SAC, no translator),
   declaration/value parser (emits the existing `impl/dom/*` types
   so Phase 4 can replace those types in a focused follow-up),
   stylesheet parser (emits `CSSStyleSheetImpl` /
   `CSSStyleRuleImpl` / `CSSImportRuleImpl`; parses `@media` /
   `@font-face` / `!important` and discards them), plus a
   `CssParseException` replacing `org.w3c.css.sac.CSSException`.
   Inline unit tests for tokenizer and parser. Pure addition;
   reviewable in isolation.

2. **Cut over and delete.** Wire the new parser into
   `CSSEngineImpl.makeCSSParser`, drop
   `Require-Bundle: org.apache.batik.css` and the SAC
   `Import-Package`, delete the 3 remaining `impl/sac/*` classes,
   the 6 `core/sac/*` façades, `SacTranslator`, `SACConstants`,
   `core/dom/parsers/CSSParser` + `CSSParserFactory` +
   `ICSSParserFactory`, `impl/dom/parsers/AbstractCSSParser` +
   `CSSParserFactoryImpl`, and the four `InputSource` overloads on
   `CSSEngine` (`Reader` / `InputStream` overloads remain).
   Migrate the few non-engine callers off `InputSource`:
   `ThemeEngine`, `ParserTestUtil`, `ImportTest`, the css.swt
   margin/padding handlers' `CSSException` references. Bump
   `org.eclipse.e4.ui.css.core` Bundle-Version (no API baseline
   failure since the bundle's exports are all `x-internal` /
   `x-friends`).

No fallback: the legacy parser is removed in commit 2 in one go,
not behind a system property. No parallel-parser smoke harness:
the existing test suite (CSSEngineTest, SelectorMatcherTest,
StyleSheetStructureTest, CascadeTest, ValueTest, ImportTest,
MediaRulesTest, FontFaceRulesTest, InheritTest, ViewCSSTest) is
the gate. The Phase 0 test gap-fill (`!important` parse-and-pin,
`:hover`/`:focus`/`:active` parser tolerance, string escapes in
attribute selectors, expanded `url()` forms, `rgba`,
trailing-semicolon-optional, etc.) was considered and skipped as
not critical: if the new parser is wrong about any of these, the
shipped themes will fail visibly rather than silently.

Effort: 3 to 5 days, 2 PRs. Net ~+900 / −3,400 across the two
commits, ~−2,500 net.

Combined Phase 3 (step 1 + step 2): 2 PRs landed for step 1, 2
PRs for step 2, net ~−3,900 LOC, drops one external runtime
dependency, leaves zero SAC types in the codebase.

### Phase 4 — replace the W3C DOM mirror with internal POJOs (re-ordered, was Phase 3)

`impl/dom/*` (~32 classes, 3,500 to 4,500 LOC) implements `CSSStyleSheet`,
`CSSStyleRule`, `CSSImportRule`, `CSSValueList`, `RGBColor`, `Measure`,
etc., for an SWT consumer that never asks for DOM compliance. After
Phase 3 the new `BatikStyleSheetParser` is the only producer of these
types and the SAC layer is gone, so the mirror can be replaced with
plain rule, selector, and value records:

```java
record StyleSheet(List<Rule> rules) {}
sealed interface Rule permits StyleRule, ImportRule {}
record StyleRule(List<Selector> selectors, Map<String, CssValue> declarations) {}
record ImportRule(URI href) {}
sealed interface CssValue permits ColorValue, NumberValue, ListValue, KeywordValue, StringValue {}
```

Phase 3's parser now emits the POJOs instead of the wrapper classes;
engine and property handlers shift to read the new types directly. The
Phase 1 + Phase 2 tests in `css-testing.md` need to be rewritten on
the new types as part of this phase, since they currently assert
against the W3C DOM-CSS interfaces.

Risk: medium. Cascade ordering, specificity, and `@import` resolution
all live here. Drop them carefully.

Effort: 8 to 12 days, 1 large PR or 2 medium PRs. ~3,500 LOC removed,
20 to 25 classes removed.

### Phase 5 — collapse trivial property-handler classes

Today: `RegistryCSSPropertyHandlerProvider` reads the
`org.eclipse.e4.ui.css.core.propertyHandler` extension point, builds a
map keyed by element-class + property name, and dispatches into one of
74 handler classes. Most handlers are stateless one-liners — 15 to 20
of them are near-identical boolean / int / color setters wrapped in
boilerplate.

The registry-based dispatch stays. Clients override our handlers by
contributing to the same extension point, so we cannot bypass it for
our own handlers without breaking the override contract. The
consolidation happens inside the handler classes:

- One `GenericBooleanSWTHandler` registered in `plugin.xml` for every
  boolean SWT property (`maximize-visible`, `minimize-visible`,
  `mru-visible`, ...). Its `applyCSSProperty(element, property, value,
  ...)` dispatches on `property` to a small lookup map of
  `BiConsumer<Widget, Boolean>`.
- Same shape for `GenericIntSWTHandler`,
  `GenericColorSWTHandler`, etc., where the property-to-setter
  mapping is regular.
- The non-trivial appliers (margins, paddings, preferences,
  CTabFolder visual rendering) keep their dedicated classes; their
  logic does not collapse cleanly.

Plugin.xml contribution shape stays one entry per (element-class,
property): the registry still finds external overrides at the same
granularity. We just gain one bit of property dispatch inside the
shared handler. Net effect: ~30 wrapper classes deleted, ~3 to 5
generic handlers added, no schema or contract change.

Out of scope: removing or deprecating the `propertyHandler` /
`elementProvider` extension points, or `RegistryCSSPropertyHandlerProvider`
itself. They stay public and functional for downstream RCP products
that contribute custom handlers, including overrides of the new
generic handlers. This caps the LOC delta but keeps external
contracts intact.

Effort: 5 to 7 days across 2 to 3 PRs. ~1,500 to 1,800 LOC removed,
~30 wrapper classes removed. Medium risk; the override path is
exercised by external contributors, so the new generic handlers must
not change observable behaviour for any single (element, property)
pair.

### Phase 6 — merge `css.swt.theme` into `css.swt`

`css.swt.theme` is 7 classes / ~1,100 LOC of theme manager wiring. It
does not justify its own bundle, MANIFEST, feature.xml entry, p2 IU, and
test bundle. Inline as an internal package of `css.swt`.

This is logistics-heavy (feature.xml, target platform updates,
downstream build files reference the bundle by name) more than
code-heavy. Do it last, when no other phase is touching the bundle
boundary.

Effort: 2 to 3 days. ~200 LOC net. Medium risk (build-system blast
radius).

## Order of work

| Order | Phase | LOC delta | Risk |
|---|---|---|---|
| 1 | Phase 0 — mechanical cleanups | ~-500 | Low |
| 2 | Phase 1 — finish test safety net | ~+1,000 | Low |
| 3 | Phase 2 — flatten hierarchies and helpers | ~-2,000 | Low |
| 4 | Phase 3 step 1 — drop SAC types, keep Batik | ~-1,400 | Medium |
| 5 | Phase 3 step 2 — replace Batik with hand-written tokenizer | ~-2,500 | Medium |
| 6 | Phase 4 — replace DOM mirror with POJOs | ~-3,500 | Medium |
| 7 | Phase 5 — collapse trivial property-handler classes | ~-1,700 | Medium |
| 8 | Phase 6 — merge `css.swt.theme` into `css.swt` | ~-200 | Medium |
| **Total** | | **~-10,800** | |

Phases 3 and 4 swapped relative to the original plan: the parser is the
only producer of the W3C DOM mirror types, so replacing the parser
first leaves a single, well-scoped change to delete the mirror in
Phase 4. The original "DOM-mirror first, parser second" ordering would
have required a temporary W3C → POJO conversion layer to keep the SAC
parser feeding into a new model, plus rewriting the Phase 2 round-trip
tests twice (once when the model changes, again when the parser does).

Roughly a third of the current LOC, in line with the upper end of the
analyses in `temp1`, `temp2`, `temp3`.

## Performance & Optimization Benchmarks

Following the integration of Phase 3 Step 1 (internal AST and matcher), profiling identified a performance regression during CSS theme swaps. A detailed optimization effort was carried out to establish a new performance baseline.

### Bottlenecks Identified
1. **Dynamic Parent Resolution**: `SelectorMatcher` dynamically traversed parent nodes by invoking `element.getParentNode()`. In SWT, this triggers costly Map lookups and potential DOM element adapter instantiations.
2. **Traversal Overlap**: Redundant styling passes (`Shell.reskin(SWT.ALL)` followed immediately by recursive `CSSEngine.applyStyles`) caused elements to be styled multiple times in a single theme swap.

### Optimizations Implemented
1. **Pre-computed Ancestor Hierarchy**: The matching path was overloaded to accept a pre-computed array of ancestor elements, avoiding dynamic `getParentNode()` lookups.
2. **Styling Sessions**: A thread-local `styledElements` session was introduced to track and prevent duplicate styling of widgets within a single theme swap operation.

### Benchmark Results
The optimizations were verified using the stress test `CssThemeSwapPerformanceTest` (which styles a workbench containing 4,000+ SWT widgets and 20 Java editors).

| Metric | Unoptimized Baseline (Regression) | Optimized Implementation | Improvement |
| :--- | :--- | :--- | :--- |
| **Median Time** | 2091.30 ms | 1322.55 ms (up to 1136.51 ms) | ~36.8% (up to ~45.6%) |
| **Mean Time** | 2119.23 ms | 1339.35 ms | ~36.8% |
| **Minimum Time** | 1890.94 ms | 1129.77 ms | ~40.3% |
| **Maximum Time** | 2425.58 ms | 2050.99 ms | ~15.4% |

### Parser cutover (Phase 3 Step 2): Batik vs internal parser

After replacing Batik with the hand-written parser, `CssThemeSwapPerformanceTest` was re-run on the same machine at the pre-cutover commit (Batik) and at the cutover commit (internal parser), 10 measured dark/light swaps each.

| Metric | Batik | Internal parser | Δ |
| :--- | :--- | :--- | :--- |
| Theme swap median | 2651.60 ms | 2252.74 ms | -15.0% |
| Theme swap mean | 2669.86 ms | 2253.92 ms | -15.6% |
| Theme swap min | 2505.62 ms | 2057.74 ms | -17.9% |
| Theme swap p95/max | 2887.47 ms | 2400.66 ms | -16.9% |
| parseStyleSheet total (108 parses) | 17.62 ms | 13.78 ms | -21.8% |
| parseStyleSheet avg | 163.19 us | 127.60 us | -21.8% |

Interpretation: the parser itself is about 22% faster, which is real and directly attributable to dropping Batik/SAC.
That gain is small in absolute terms, since parsing is under 1% of a theme swap (about 14 ms out of about 2250 ms); the swap is dominated by selector matching and property application, which this change did not touch.
The roughly 15% end-to-end swap improvement is therefore mostly run-to-run variance (single run per state, within-run spread already about 350 ms) rather than a true effect of the parser swap.
The defensible conclusion is that the cutover did not regress theme-swap performance and made parsing in isolation about 22% faster.

## Risks worth calling out

- **Test coverage is thin.** Phase 1 (the test net) is non-negotiable.
  Skipping it roughly doubles iteration counts on every later phase.
- **Theme regressions.** Add a smoke test that parses every `.css` under
  `bundles/**/css/` with both old and new parser during Phase 4 and
  compares selector text + declaration counts. Fail on divergence.
- **Closed-source RCP consumers.** Custom `CSSEngine` subclasses likely
  have external users; the `propertyHandler` and `elementProvider`
  extension points might. Both extension points stay public (see Phase
  5 — only our in-tree contributions move to a static dispatch). Phase
  6 (`css.swt.theme` inlining) is the only phase that removes a public
  surface; ship it with a deprecation cycle.
- **Pseudo-element semantics.** The current SAC matcher has a quirk:
  `CSSPseudoClassConditionImpl.match` returns
  `!isStaticPseudoInstance(value)` when `pseudoE == null`. Lock this in
  a Phase 1 test before Phase 4 starts, or define the new behaviour
  explicitly and migrate the few `ElementAdapter` subclasses that depend
  on the static-pseudo registration.
- **Specificity calculation.** Cascade order depends on it. Phase 2
  test additions must include a specificity case before Phase 3 lands.
- **Batik tightening.** Going from a SAC façade to direct Batik usage
  couples us more to Batik. Acceptable in exchange for ~2k LOC removed
  today; revisit if Batik itself ever needs to be replaced.

## Out of scope

- Replacing Batik with ph-css or a hand-written tokenizer. Defer until
  after Phase 5 if at all. Mixing the parser swap with a dependency swap
  is what makes /temp3 estimate Phase 4 at "high risk".
- Adding new CSS features (`:hover`, `!important`, `@media`).
- Deprecating or removing the `elementProvider` and `propertyHandler`
  extension points. They stay public so downstream RCP products can
  keep contributing custom handlers. Phase 5 only collapses our own
  in-tree contributions; the registry-based dispatch path stays alive
  for external contributors.
- Replacing the engine for non-SWT clients. The engine is SWT-only in
  practice; treat that as an invariant.

## Low-priority follow-ups

- **Replace `CSSSWTTestCase` inheritance with a JUnit 5 extension.**
  About 25 widget tests in `tests/org.eclipse.e4.ui.tests.css.swt`
  currently extend `CSSSWTTestCase` to inherit a `display` field, an
  engine factory, and a tearDown that disposes shells. Convert it to a
  `BeforeEachCallback`/`AfterEachCallback` extension registered via
  `@RegisterExtension CssSwtEngine css = new CssSwtEngine()`, which
  drops the protected mutable state, lets tests use multiple engines
  or stylesheets per test, and frees the test classes to extend other
  bases. Defer until after Phase 3 of `css-testing.md` lands so this
  migration does not merge-conflict against the gap-fill PRs.
