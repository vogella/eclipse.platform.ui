# Implementation Plan: URLImageDescriptor Delegates to IImageURLModifier Strategy

> **Status:** Steps 1 to 5 are implemented in the commit on top of this one, with three
> deviations from the plan below:
>
> - The modifier is applied in `URLImageDescriptor.getURL(String)`, the single point where the
>   descriptor turns its URL string into a URL, instead of at the three call sites of Step 3.
>   This also covers the file based fast path in `createImage` and `getAdapter(URL.class)`, both
>   of which Step 3 missed.
> - Registration uses the OSGi service of Step 7 Option A. No bundle id is hardcoded anywhere.
> - Step 6 (`JsonIconPackURLModifier`) is not implemented. The mapping format is icon pack policy
>   and would drag a `com.google.gson` dependency into `org.eclipse.ui.workbench`.
>
> Step 7b (CSS driven icon theme discovery) is still open.

This document describes the stepwise implementation of the "refined variant" from
[eclipse-platform/eclipse.platform.ui#3832](https://github.com/eclipse-platform/eclipse.platform.ui/pull/3832).

Instead of embedding path-rewriting logic in `URLImageDescriptor`, the rewriting is
delegated to a pluggable `IImageURLModifier` strategy interface. JFace owns only the
call site and the interface; all policy (path conventions, OSGi fragments, CSS lookups)
lives outside JFace.

---

## Context

**Key files:**

| File | Bundle |
|------|--------|
| `bundles/org.eclipse.jface/src/org/eclipse/jface/resource/URLImageDescriptor.java` | `org.eclipse.jface` |
| `bundles/org.eclipse.jface/src/org/eclipse/jface/internal/InternalPolicy.java` | `org.eclipse.jface` |
| `bundles/org.eclipse.jface/META-INF/MANIFEST.MF` | `org.eclipse.jface` |
| `tests/org.eclipse.jface.tests/src/org/eclipse/jface/tests/images/UrlImageDescriptorTest.java` | `org.eclipse.jface.tests` |

**Three resolution paths in `URLImageDescriptor`** that must call the modifier:

| Method | Zoom | Entry point |
|--------|------|------------|
| `getImageData(int zoom)` | 100 | `getImageData(tempURL, 100, zoom)` |
| `createURLImageFileNameProvider()` lambda | 100 | `getFilePath(tempURL, logIOException)` |
| `getZoomedImageSource(…)` | 150 / 200 | called before `getxURL()` |

**Design constraints:**
- JFace must not depend on OSGi, preference stores, or any Eclipse-specific APIs.
- The modifier is set once at application startup; thread safety via `volatile` is sufficient.
- A restart is acceptable; no cache invalidation of `ImageRegistry` is required.
- Do NOT add `Co-Authored-By` trailers to commits (Eclipse license check will fail).

**Coding conventions (apply to every file you produce):**
- **Indentation:** tabs, not spaces. All Java files in this codebase use a single tab per indent level.
- **Copyright year:** use `2026` (current year) in new file headers.
- **`@since` tags:** match the bundle's current minor version. JFace is currently `3.39.100.qualifier`, so new API is `@since 3.39`.
- **Javadoc links:** never `{@link}` a package-private type from a public type's Javadoc — the compiler treats it as an error. Use `{@code TypeName}` for forward references to types or methods that do not yet exist.
- **Verify compilation** after each step: `mvn clean compile -pl :<bundle-artifact-id> -Pbuild-individual-bundles -q`. Fix all errors and Javadoc warnings before moving to the next step.
- **OSGi imports:** before using any type from another bundle, confirm its package appears in `META-INF/MANIFEST.MF` under `Import-Package` or `Require-Bundle`.

---

## Step 1 — Define the `IImageURLModifier` interface in JFace ✅ DONE

**File to create:**
`bundles/org.eclipse.jface/src/org/eclipse/jface/resource/IImageURLModifier.java`

```java
/*******************************************************************************
 * Copyright (c) 2025 Eclipse contributors and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jface.resource;

import java.net.URL;

/**
 * Strategy that can rewrite an image URL before {@link URLImageDescriptor}
 * opens it. An implementation is set once at application startup via
 * {@link URLImageDescriptor#setURLModifier(IImageURLModifier)}.
 *
 * <p>
 * This interface allows RCP applications or icon-pack bundles to supply
 * theme-specific icon replacements without any changes to call sites or to
 * {@link ImageRegistry}.
 * </p>
 *
 * @since 3.37
 */
public interface IImageURLModifier {

    /**
     * Returns a (possibly different) URL to load instead of
     * {@code originalURL}, or {@code originalURL} itself if no substitution
     * is desired.
     *
     * @param originalURL the URL that {@code URLImageDescriptor} is about to
     *                    open; never {@code null}
     * @return the URL to use; must not be {@code null}
     */
    URL modifyURL(URL originalURL);
}
```

**Notes / fixes applied during implementation:**
- Indentation corrected to tabs (the original used 4-space indentation).
- `@since` changed from `3.37` to `3.39` (bundle version is `3.39.100.qualifier`).
- Javadoc cannot link to `URLImageDescriptor` (package-private) nor to
  `ImageDescriptor#setURLModifier` (not yet defined). Both were replaced with
  `{@code …}` / plain-text forward references. Once Step 2 is done, restore the
  `{@link ImageDescriptor#setURLModifier(IImageURLModifier)}` link.

**No other file changes in this step.**

---

## Step 2 — Add the static modifier field and registration method to `URLImageDescriptor`

**File to modify:**
`bundles/org.eclipse.jface/src/org/eclipse/jface/resource/URLImageDescriptor.java`

Add the following directly after the class declaration opening brace (before the existing
`createURLImageFileNameProvider()` method):

```java
/**
 * Optional strategy for rewriting image URLs before they are opened.
 * Set once at application startup; {@code null} means no rewriting.
 * Declared {@code volatile} so that a write from the startup thread is
 * visible to the UI thread without further synchronisation.
 */
private static volatile IImageURLModifier urlModifier;

/**
 * Sets the URL modifier that {@link URLImageDescriptor} will consult when
 * resolving every image URL. Must be called before any image is loaded
 * (typically from an {@code IStartup} or E4 lifecycle handler).
 *
 * @param modifier the modifier to install, or {@code null} to remove any
 *                 previously installed modifier
 * @since 3.37
 */
public static void setURLModifier(IImageURLModifier modifier) {
    urlModifier = modifier;
}

/**
 * Returns the currently installed URL modifier, or {@code null} if none.
 *
 * @return the modifier, or {@code null}
 * @since 3.37
 */
public static IImageURLModifier getURLModifier() {
    return urlModifier;
}

/**
 * Applies the installed modifier (if any) to {@code url}.
 *
 * @param url the original URL; must not be {@code null}
 * @return the (possibly rewritten) URL; never {@code null}
 */
private static URL applyModifier(URL url) {
    IImageURLModifier m = urlModifier;
    return (m != null) ? m.modifyURL(url) : url;
}
```

**Note:** `URLImageDescriptor` is package-private (`class URLImageDescriptor`), but the
static methods `setURLModifier` / `getURLModifier` need to be callable from outside
`org.eclipse.jface.resource`. Therefore change the class declaration to `public` — it is
already effectively public through `ImageDescriptor.createFromURL(URL)` and is exported
by the bundle. Alternatively, expose only the two static setter/getter methods through
a new public class `URLImageDescriptors` (see Step 3 option B). **Preferred:** keep
`URLImageDescriptor` package-private and expose the modifier field via `ImageDescriptor`
itself (a static method on the public API class), or — simplest — add a dedicated
public `ImageDescriptorURLModifier` utility class in `org.eclipse.jface.resource`.

**Simplest approach (recommended):** add `setURLModifier` / `getURLModifier` as static
methods directly on the public class `ImageDescriptor`:

```java
// In ImageDescriptor.java
/**
 * Sets the URL modifier consulted by {@link URLImageDescriptor} when
 * resolving every image URL. Must be called before any image is loaded.
 *
 * @param modifier the modifier, or {@code null} to clear
 * @since 3.37
 */
public static void setURLModifier(IImageURLModifier modifier) {
    URLImageDescriptor.urlModifier = modifier;
}

public static IImageURLModifier getURLModifier() {
    return URLImageDescriptor.urlModifier;
}
```

In that case `urlModifier` in `URLImageDescriptor` may remain package-private.

---

## Step 3 — Wire `applyModifier` into the three URL resolution paths

**File to modify:**
`bundles/org.eclipse.jface/src/org/eclipse/jface/resource/URLImageDescriptor.java`

### 3a — `getImageData(int zoom)` — zoom == 100 path

Current code (lines 104–113):

```java
@Override
public ImageData getImageData(int zoom) {
    URL tempURL = getURL(url);
    if (tempURL != null) {
        if (zoom == 100 || canLoadAtZoom(tempURL, zoom)) {
            return getImageData(tempURL, 100, zoom);
        }
        return getZoomedImageSource(tempURL, url, zoom, u -> getImageData(u, zoom, zoom));
    }
    return null;
}
```

Change to apply modifier immediately after `getURL`:

```java
@Override
public ImageData getImageData(int zoom) {
    URL tempURL = getURL(url);
    if (tempURL != null) {
        tempURL = applyModifier(tempURL);
        if (zoom == 100 || canLoadAtZoom(tempURL, zoom)) {
            return getImageData(tempURL, 100, zoom);
        }
        return getZoomedImageSource(tempURL, url, zoom, u -> getImageData(u, zoom, zoom));
    }
    return null;
}
```

### 3b — `createURLImageFileNameProvider()` lambda — zoom == 100 path

Current code (lines 56–70):

```java
private ImageFileNameProvider createURLImageFileNameProvider() {
    return zoom -> {
        URL tempURL = getURL(url);
        if (tempURL != null) {
            final boolean logIOException = zoom == 100;
            if (zoom == 100) {
                return getFilePath(tempURL, logIOException);
            }
            return getZoomedImageSource(tempURL, url, zoom, u -> getFilePath(u, logIOException));
        }
        return null;
    };
}
```

Change to apply modifier immediately after `getURL`:

```java
private ImageFileNameProvider createURLImageFileNameProvider() {
    return zoom -> {
        URL tempURL = getURL(url);
        if (tempURL != null) {
            tempURL = applyModifier(tempURL);
            final boolean logIOException = zoom == 100;
            if (zoom == 100) {
                return getFilePath(tempURL, logIOException);
            }
            return getZoomedImageSource(tempURL, url, zoom, u -> getFilePath(u, logIOException));
        }
        return null;
    };
}
```

### 3c — `getZoomedImageSource` — zoom != 100 path

`getZoomedImageSource` is a static helper called with an already-resolved `URL`.
The modifier must be applied **before** this helper is reached, i.e. at the call sites
in `getImageData` and `createURLImageFileNameProvider`. Steps 3a and 3b above already
apply the modifier to `tempURL` before passing it to `getZoomedImageSource`, so no
further changes to `getZoomedImageSource` itself are needed.

---

## Step 4 — Update `MANIFEST.MF` to export `IImageURLModifier`

`IImageURLModifier` is placed in `org.eclipse.jface.resource`, which is already exported
by `bundles/org.eclipse.jface/META-INF/MANIFEST.MF`:

```
Export-Package: …
 org.eclipse.jface.resource,
 …
```

No change needed in `MANIFEST.MF` for the interface itself. However, if `setURLModifier`
/ `getURLModifier` are added to `ImageDescriptor` (recommended in Step 2), verify that
`org.eclipse.jface.resource` is exported without the `x-internal:=true` qualifier — it
already is.

---

## Step 5 — Write unit tests for `IImageURLModifier` and `URLImageDescriptor`

**File to modify:**
`tests/org.eclipse.jface.tests/src/org/eclipse/jface/tests/images/UrlImageDescriptorTest.java`

Add a JUnit 5 test class (or test methods in the existing class) covering:

1. **No modifier installed** — `applyModifier` is a no-op; the original URL is returned
   unchanged.

2. **Modifier installed, URL matches** — `applyModifier` returns the alternate URL;
   verify that `getImageData(100)` and `createURLImageFileNameProvider()` both use the
   alternate URL.

3. **Modifier installed, URL does not match** — modifier returns the original URL; image
   loads from the original location.

4. **`setURLModifier(null)` clears the modifier** — subsequent calls behave as case 1.

5. **HiDPI handled automatically** — install a modifier that rewrites the base URL; check
   that `getZoomedImageSource` (zoom == 200 path) appends `@2x` to the *rewritten* URL,
   not the original one.

Use test image resources already present in
`tests/org.eclipse.jface.tests/src/org/eclipse/jface/tests/images/` or add a minimal
test PNG there.

---

## Step 6 — Implement `JsonIconPackURLModifier` driven by `icon-mapping.json`

The `/theme/` directory convention in earlier drafts was just an illustration.
The real implementation is driven by a JSON mapping file whose structure is:

```json
{
  "delete.svg": [
    "org.eclipse.ui/icons/full/etool16/delete.svg",
    "org.eclipse.ui/icons/full/etool16/delete_edit.svg"
  ],
  "warning.svg": [
    "org.eclipse.ui/icons/full/obj16/warn_tsk.svg",
    "org.eclipse.jface/icons/full/message_warning.svg"
  ]
}
```

- **Key** — filename of the replacement icon as it exists in the icon pack bundle (e.g. under `icons/`).
- **Values** — list of original icon paths in `"<bundleId>/<iconPath>"` format, exactly
  matching the `platform:/plugin/<bundleId>/<iconPath>` URL structure.

**What the modifier must do at construction time:**

1. Locate `icon-mapping.json` inside the icon pack bundle (via `Bundle.getEntry()`).
2. Parse the JSON and **invert** the map:
   `"bundleId/icon/path.svg"` → URL of replacement icon in the icon pack bundle.
3. Store the resulting `Map<String, URL>` for use in `modifyURL`.

**What the modifier does in `modifyURL(URL originalURL)`:**

1. Check that the protocol is `platform` and the path contains `/plugin/`.
2. Extract `bundleId/iconPath` from the URL's file component (strip the leading `/plugin/`).
3. Strip any `$nl$/` locale prefix from `iconPath` (e.g. `$nl$/icons/…` → `icons/…`).
4. Look up the extracted key in the inverted map.
5. Return the replacement URL if found; otherwise return `originalURL` unchanged.

**New file to create:**
`bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/resource/JsonIconPackURLModifier.java`

```java
package org.eclipse.ui.internal.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jface.resource.IImageURLModifier;
import org.osgi.framework.Bundle;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * An {@link IImageURLModifier} that rewrites {@code platform:/plugin/...} image
 * URLs according to a JSON icon-mapping file shipped inside an icon pack bundle.
 *
 * <p>
 * The JSON file has the structure:
 * </p>
 *
 * <pre>
 * {
 *   "replacement-icon.svg": [
 *     "org.eclipse.some.bundle/icons/path/original.svg",
 *     ...
 *   ],
 *   ...
 * }
 * </pre>
 *
 * <p>
 * The replacement icons must be present in the icon pack bundle under
 * {@code <iconsBasePath>/<replacement-icon.svg>}.
 * </p>
 */
public class JsonIconPackURLModifier implements IImageURLModifier {

	private static final String PLUGIN_SEGMENT = "/plugin/"; //$NON-NLS-1$
	private static final String NL_PREFIX      = "$nl$/";    //$NON-NLS-1$

	/** Inverted map: "bundleId/icon/path.svg" -> replacement URL */
	private final Map<String, URL> replacements;

	/**
	 * Constructs a modifier by loading and inverting the icon mapping.
	 *
	 * @param iconPackBundle the bundle that contains the mapping file and icons
	 * @param mappingPath    bundle-relative path to the JSON mapping file
	 *                       (e.g. {@code "icon-mapping.json"})
	 * @param iconsBasePath  bundle-relative path prefix for replacement icons
	 *                       (e.g. {@code "icons/"})
	 * @throws IOException if the mapping file cannot be read
	 */
	public JsonIconPackURLModifier(Bundle iconPackBundle, String mappingPath,
			String iconsBasePath) throws IOException {
		this.replacements = loadMappings(iconPackBundle, mappingPath, iconsBasePath);
	}

	private static Map<String, URL> loadMappings(Bundle bundle, String mappingPath,
			String iconsBasePath) throws IOException {
		URL mappingURL = bundle.getEntry(mappingPath);
		if (mappingURL == null) {
			throw new IOException("Icon mapping not found in bundle: " + mappingPath); //$NON-NLS-1$
		}
		Map<String, URL> map = new HashMap<>();
		try (InputStream in = mappingURL.openStream();
				Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				String replacementIcon = entry.getKey();
				URL replacementURL = bundle.getEntry(iconsBasePath + replacementIcon);
				if (replacementURL == null) {
					continue; // replacement icon missing from bundle — skip
				}
				JsonArray originals = entry.getValue().getAsJsonArray();
				for (JsonElement elem : originals) {
					// key format: "bundleId/icon/path.svg"
					map.put(elem.getAsString(), replacementURL);
				}
			}
		}
		return map;
	}

	@Override
	public URL modifyURL(URL originalURL) {
		if (!"platform".equals(originalURL.getProtocol())) { //$NON-NLS-1$
			return originalURL;
		}
		String file = originalURL.getFile();
		int pluginIdx = file.indexOf(PLUGIN_SEGMENT);
		if (pluginIdx < 0) {
			return originalURL;
		}
		// key = "bundleId/icon/path.svg"
		String key = file.substring(pluginIdx + PLUGIN_SEGMENT.length());
		// strip $nl$/ locale prefix if present
		if (key.contains(NL_PREFIX)) {
			key = key.replace(NL_PREFIX, ""); //$NON-NLS-1$
		}
		URL replacement = replacements.get(key);
		return (replacement != null) ? replacement : originalURL;
	}
}
```

**Dependencies to add to `bundles/org.eclipse.ui.workbench/META-INF/MANIFEST.MF`:**

```
Import-Package: …,
 com.google.gson;version="[2.8.0,3.0.0)"
```

`com.google.gson` is already part of the Eclipse Platform target platform.

**Notes:**
- The map is built once at construction; `modifyURL` itself is allocation-free and thread-safe.
- Icons missing from the bundle (no file at `iconsBasePath + replacementIcon`) are silently
  skipped — those originals continue to load from their own bundle.
- HiDPI is handled for free: `URLImageDescriptor` appends `@2x`/`@1.5x` to whichever URL
  `modifyURL` returns. Replacement icons that have `@2x` variants in the icon pack bundle
  will be found automatically via the existing zoom logic.

---

## Step 7 — Discover and register `JsonIconPackURLModifier` at workbench startup

### ⚠️ Design issue: do not hardcode the bundle ID

The workbench must not know about `org.eclipse.ui.iconpack.dualtone` specifically.
Icon pack discovery must be decoupled. Two viable approaches:

#### Option A — OSGi service (recommended)

Register `IImageURLModifier` as an OSGi DS service in the icon pack bundle:

```java
// In org.eclipse.ui.iconpack.dualtone — DS component:
@Component(service = IImageURLModifier.class)
public class DualToneIconPackModifier implements IImageURLModifier {
    // delegates to JsonIconPackURLModifier loaded from this bundle
}
```

The workbench picks up the highest-ranked registered service at startup:

```java
// In Workbench.java / registerIconPackModifier():
BundleContext ctx = WorkbenchPlugin.getDefault().getBundleContext();
ServiceReference<IImageURLModifier> ref = ctx.getServiceReference(IImageURLModifier.class);
if (ref != null) {
    ImageDescriptor.setURLModifier(ctx.getService(ref));
    // Note: do not unget the service — modifier must live for the process lifetime
}
```

This requires exporting `IImageURLModifier` from JFace so the icon pack bundle can
import it, and adding `IImageURLModifier.class` to the OSGi service registry contract.

#### Option B — Extension point

Declare an extension point `org.eclipse.ui.imageURLModifier` in
`org.eclipse.ui.workbench/plugin.xml`. Icon pack bundles contribute via `plugin.xml`.
The workbench reads the first registered extension at startup.

### Current implementation (proof-of-concept only)

The current `registerIconPackModifier()` in `Workbench.java` hardcodes the bundle ID
`"org.eclipse.ui.iconpack.dualtone"` — acceptable for initial testing, but **must be
replaced** with Option A or B before merging.

The `org.eclipse.ui.iconpack.dualtone` bundle must contain:

| Path in bundle | Source |
|----------------|--------|
| `icon-mapping.json` | `ui-best-practices/iconpacks/eclipse-dual-tone/icon-mapping.json` |
| `icons/<name>.svg` (130 files) | `ui-best-practices/iconpacks/eclipse-dual-tone/dual-tone-icons/<name>.svg` |

No Java source is needed — it is a pure resource bundle (no activator, no `plugin.xml`).

The `null` check in `registerIconPackModifier()` ensures the workbench starts normally
when no icon pack is installed.

---

## Step 7b — CSS-driven icon theme discovery (preferred replacement for Step 7 Option B)

Instead of an OSGi service or a plain extension point, the icon pack can declare itself
directly in a **theme stylesheet** using a new CSS property. The workbench has zero
knowledge of any specific icon pack bundle.

### How it works end-to-end

#### CSS syntax in the theme stylesheet

```css
/* contributed by the icon pack bundle via org.eclipse.e4.ui.css.swt.theme */
IconTheme {
    eclipse-icon-bundle: "org.eclipse.ui.iconpack.dualtone";
}
```

#### New DOM element — `IconThemeElement`

A lightweight marker element so the CSS engine can address the `IconTheme {}` selector.
No native SWT widget is involved.

**New file:**
`bundles/org.eclipse.e4.ui.css.swt/src/org/eclipse/e4/ui/css/swt/dom/IconThemeElement.java`

```java
package org.eclipse.e4.ui.css.swt.dom;

public class IconThemeElement extends AbstractCSSNodeAdapter {
    @Override
    public String getLocalName() { return "IconTheme"; }
}
```

Register via `org.eclipse.e4.ui.css.core.elementProvider` so the CSS engine can build a
DOM node for it.

#### New property handler — `CSSPropertyIconThemeSWTHandler`

Follows the pattern of `CSSPropertyThemesExtensionHandler` (same interface, same bundle).

**New file:**
`bundles/org.eclipse.e4.ui.css.swt/src/org/eclipse/e4/ui/css/swt/properties/custom/CSSPropertyIconThemeSWTHandler.java`

```java
package org.eclipse.e4.ui.css.swt.properties.custom;

import java.io.IOException;
import org.eclipse.e4.ui.css.core.dom.properties.ICSSPropertyHandler;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.internal.resource.JsonIconPackURLModifier;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSValue;

public class CSSPropertyIconThemeSWTHandler implements ICSSPropertyHandler {

	private static final String PROP_BUNDLE = "eclipse-icon-bundle"; //$NON-NLS-1$

	@Override
	public boolean applyCSSProperty(Object element, String property,
			CSSValue value, String pseudo, CSSEngine engine) throws Exception {
		if (!PROP_BUNDLE.equals(property)
				|| value.getCssValueType() != CSSValue.CSS_PRIMITIVE_VALUE) {
			return false;
		}
		String bundleId = ((CSSPrimitiveValue) value).getStringValue();
		Bundle iconPack = Platform.getBundle(bundleId);
		if (iconPack == null) {
			return false;
		}
		try {
			ImageDescriptor.setURLModifier(
				new JsonIconPackURLModifier(iconPack, "icon-mapping.json", "icons/")); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IOException e) {
			Platform.getLog(CSSPropertyIconThemeSWTHandler.class).error(
				"Failed to load icon pack: " + bundleId, e); //$NON-NLS-1$
		}
		return true;
	}

	@Override
	public String retrieveCSSProperty(Object element, String property,
			String pseudo, CSSEngine engine) throws Exception {
		return null;
	}
}
```

**Note:** `JsonIconPackURLModifier` is in `org.eclipse.ui.internal.resource` (workbench
internal). The handler lives in `org.eclipse.e4.ui.css.swt` which does not depend on
`org.eclipse.ui.workbench`. Two options:

- Move `JsonIconPackURLModifier` to `org.eclipse.e4.ui.css.swt` or a shared bundle, or
- The handler only calls `ImageDescriptor.setURLModifier(modifier)` via a service
  (`BundleContext.getServiceReference(IImageURLModifier.class)`) and lets the icon pack
  bundle register the modifier itself as an OSGi DS component.

#### Register in `plugin.xml` of `org.eclipse.e4.ui.css.swt`

```xml
<extension point="org.eclipse.e4.ui.css.core.propertyHandler">
    <handler
        adapter="org.eclipse.e4.ui.css.swt.dom.IconThemeElement"
        handler="org.eclipse.e4.ui.css.swt.properties.custom.CSSPropertyIconThemeSWTHandler">
        <property-name name="eclipse-icon-bundle"/>
    </handler>
</extension>
```

#### Icon pack contributes its stylesheet via `org.eclipse.e4.ui.css.swt.theme`

```xml
<!-- In org.eclipse.ui.iconpack.dualtone/plugin.xml -->
<extension point="org.eclipse.e4.ui.css.swt.theme">
    <stylesheet
        uri="css/icons-dark.css"
        themeId="org.eclipse.e4.ui.css.theme.e4_dark"/>
    <stylesheet
        uri="css/icons-light.css"
        themeId="org.eclipse.e4.ui.css.theme.e4_default"/>
</extension>
```

`css/icons-dark.css` (two lines):

```css
IconTheme {
    eclipse-icon-bundle: "org.eclipse.ui.iconpack.dualtone";
}
```

With this approach `registerIconPackModifier()` in `Workbench.java` can be deleted
entirely — the workbench has zero knowledge of any specific icon pack.

### Responsibilities after this change

| Concern | Where it lives |
|---------|---------------|
| CSS property name & parsing | `CSSPropertyIconThemeSWTHandler` in `org.eclipse.e4.ui.css.swt` |
| Which bundle provides icons | Icon pack's own CSS stylesheet |
| URL rewriting logic | `JsonIconPackURLModifier` |
| JFace hook | `URLImageDescriptor.applyModifier()` |
| `Workbench.java` hardcoding | **Deleted** |

### ⚠️ Timing constraint

CSS is applied by the theme engine after the E4 application context is set up. If any
images are loaded before the theme stylesheet is processed, those `ImageRegistry` entries
will already be cached with the original icons. The handler must run before
`WorkbenchImages` initialises — this must be verified against the E4 startup sequence
before committing to this approach.

**Files to add/modify for Step 7b:**

| File | Change |
|------|--------|
| `bundles/org.eclipse.e4.ui.css.swt/src/.../dom/IconThemeElement.java` | New |
| `bundles/org.eclipse.e4.ui.css.swt/src/.../properties/custom/CSSPropertyIconThemeSWTHandler.java` | New |
| `bundles/org.eclipse.e4.ui.css.swt/plugin.xml` | Register handler and element provider |
| `bundles/org.eclipse.e4.ui.css.swt/META-INF/MANIFEST.MF` | Add dependency on `org.eclipse.jface` if not present |
| `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/Workbench.java` | Remove `registerIconPackModifier()` |

---

## Step 8 — Verify OSGi `MANIFEST.MF` dependencies

**File to check:**
`bundles/org.eclipse.ui.workbench/META-INF/MANIFEST.MF`

`org.eclipse.ui.workbench` already has a `Require-Bundle` or `Import-Package` on
`org.eclipse.jface`. No new dependency is required. Confirm:

```
Require-Bundle: …, org.eclipse.jface, …
```

or

```
Import-Package: org.eclipse.jface.resource, …
```

If `IImageURLModifier` is in `org.eclipse.jface.resource` (already exported), no
`MANIFEST.MF` changes are needed.

---

## Step 9 — Build and verify

```bash
# Compile only JFace (fastest feedback)
mvn clean compile -pl :org.eclipse.jface -Pbuild-individual-bundles -q

# Run JFace image tests
mvn clean verify -pl :org.eclipse.jface.tests -Pbuild-individual-bundles

# Compile workbench
mvn clean compile -pl :org.eclipse.ui.workbench -Pbuild-individual-bundles -q
```

---

## Step 10 — Commit and push to vogella remote into the branch feature/css-replace-images

Commit message template (do NOT add `Co-Authored-By` trailers):

```
Add IImageURLModifier strategy to URLImageDescriptor for icon-pack support

Introduce IImageURLModifier, a pluggable strategy interface in
org.eclipse.jface.resource, that URLImageDescriptor consults before
opening any image URL. This allows RCP applications and icon-pack
bundles to transparently redirect platform:/plugin/... URLs to
theme-specific replacements without modifying JFace or ImageRegistry.

Provides a reference JsonIconPackURLModifier in org.eclipse.ui.workbench
that rewrites URLs to a /theme/ subdirectory inside the original bundle,
enabling OSGi fragment-based icon packs.

Fixes: #<issue-number>
```

---

## Summary of files changed

| Step | File | Change type |
|------|------|-------------|
| 1 | `bundles/org.eclipse.jface/src/org/eclipse/jface/resource/IImageURLModifier.java` | New |
| 2 | `bundles/org.eclipse.jface/src/org/eclipse/jface/resource/ImageDescriptor.java` | Modified — add `setURLModifier` / `getURLModifier` static methods |
| 2 | `bundles/org.eclipse.jface/src/org/eclipse/jface/resource/URLImageDescriptor.java` | Modified — add `urlModifier` field and `applyModifier()` |
| 3 | `bundles/org.eclipse.jface/src/org/eclipse/jface/resource/URLImageDescriptor.java` | Modified — call `applyModifier()` in 2 paths |
| 5 | `tests/org.eclipse.jface.tests/src/org/eclipse/jface/tests/images/UrlImageDescriptorTest.java` | Modified — add tests |
| 6 | `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/resource/JsonIconPackURLModifier.java` | New — JSON-driven modifier using `icon-mapping.json` |
| 7 | `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/Workbench.java` (or lifecycle handler) | Modified — register modifier at startup |
