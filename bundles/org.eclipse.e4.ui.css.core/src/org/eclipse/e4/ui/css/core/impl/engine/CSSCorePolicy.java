/*******************************************************************************
 * Copyright (c) 2026 Lars Vogel and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.e4.ui.css.core.impl.engine;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.Platform;

/**
 * Debug / tracing policy for the CSS core engine. Reads its booleans from the
 * standard Eclipse {@code .options} file at the bundle root (visible in the
 * Tracing tab of an Eclipse run configuration).
 *
 * <p>
 * When {@link #DEBUG_PERF} is true, the engine accumulates per-phase wall
 * times and prints a summary after each top-level applyStyles call. Tests may
 * also flip the flag programmatically via {@link #setPerfEnabled(boolean)}.
 * </p>
 */
public final class CSSCorePolicy {

	private static final String PLUGIN_ID = "org.eclipse.e4.ui.css.core"; //$NON-NLS-1$

	public static volatile boolean DEBUG = false;
	public static volatile boolean DEBUG_PERF = false;

	static {
		if (option("/debug")) { //$NON-NLS-1$
			DEBUG = true;
			DEBUG_PERF = option("/debug/perf"); //$NON-NLS-1$
		}
	}

	private static boolean option(String suffix) {
		return "true".equalsIgnoreCase(Platform.getDebugOption(PLUGIN_ID + suffix)); //$NON-NLS-1$
	}

	public static void setPerfEnabled(boolean enabled) {
		DEBUG = enabled || DEBUG;
		DEBUG_PERF = enabled;
	}

	// Counters and total nanoseconds for the instrumented hot paths.
	public static final AtomicLong applyStylesCount = new AtomicLong();
	public static final AtomicLong applyStylesNs = new AtomicLong();
	public static final AtomicLong getComputedStyleCount = new AtomicLong();
	public static final AtomicLong getComputedStyleNs = new AtomicLong();
	public static final AtomicLong selectorMatchCount = new AtomicLong();
	public static final AtomicLong selectorMatchNs = new AtomicLong();
	public static final AtomicLong applyStyleDeclarationCount = new AtomicLong();
	public static final AtomicLong applyStyleDeclarationNs = new AtomicLong();
	public static final AtomicLong applyCSSPropertyCount = new AtomicLong();
	public static final AtomicLong applyCSSPropertyNs = new AtomicLong();
	public static final AtomicLong parseStyleSheetCount = new AtomicLong();
	public static final AtomicLong parseStyleSheetNs = new AtomicLong();
	public static final AtomicLong resetCount = new AtomicLong();
	public static final AtomicLong resetNs = new AtomicLong();
	public static final AtomicLong applyInlineStyleCount = new AtomicLong();
	public static final AtomicLong applyInlineStyleNs = new AtomicLong();
	public static final AtomicLong applyStylesEntryNs = new AtomicLong();
	public static final AtomicLong pseudoCascadeNs = new AtomicLong();
	public static final AtomicLong childRecursionNs = new AtomicLong();
	public static final AtomicLong handlerInvokeCount = new AtomicLong();
	public static final AtomicLong handlerInvokeNs = new AtomicLong();
	public static final AtomicLong handlerLookupCount = new AtomicLong();
	public static final AtomicLong handlerLookupNs = new AtomicLong();
	public static final AtomicLong reapplyCount = new AtomicLong();
	public static final AtomicLong reapplyNs = new AtomicLong();
	public static final AtomicLong reskinCount = new AtomicLong();
	public static final AtomicLong reskinNs = new AtomicLong();

	public static void reset() {
		applyStylesCount.set(0);
		applyStylesNs.set(0);
		getComputedStyleCount.set(0);
		getComputedStyleNs.set(0);
		selectorMatchCount.set(0);
		selectorMatchNs.set(0);
		applyStyleDeclarationCount.set(0);
		applyStyleDeclarationNs.set(0);
		applyCSSPropertyCount.set(0);
		applyCSSPropertyNs.set(0);
		parseStyleSheetCount.set(0);
		parseStyleSheetNs.set(0);
		resetCount.set(0);
		resetNs.set(0);
		applyInlineStyleCount.set(0);
		applyInlineStyleNs.set(0);
		applyStylesEntryNs.set(0);
		pseudoCascadeNs.set(0);
		childRecursionNs.set(0);
		handlerInvokeCount.set(0);
		handlerInvokeNs.set(0);
		handlerLookupCount.set(0);
		handlerLookupNs.set(0);
		reapplyCount.set(0);
		reapplyNs.set(0);
		reskinCount.set(0);
		reskinNs.set(0);
	}

	public static String summary() {
		StringBuilder sb = new StringBuilder();
		sb.append("[CSS-PERF-TRACE]\n"); //$NON-NLS-1$
		line(sb, "reapply (top)    ", reapplyCount, reapplyNs); //$NON-NLS-1$
		line(sb, "  reskin         ", reskinCount, reskinNs); //$NON-NLS-1$
		line(sb, "parseStyleSheet  ", parseStyleSheetCount, parseStyleSheetNs); //$NON-NLS-1$
		line(sb, "engine.reset     ", resetCount, resetNs); //$NON-NLS-1$
		line(sb, "applyStyles      ", applyStylesCount, applyStylesNs); //$NON-NLS-1$
		line(sb, "getComputedStyle ", getComputedStyleCount, getComputedStyleNs); //$NON-NLS-1$
		line(sb, "selectorMatch    ", selectorMatchCount, selectorMatchNs); //$NON-NLS-1$
		line(sb, "applyStyleDeclar.", applyStyleDeclarationCount, applyStyleDeclarationNs); //$NON-NLS-1$
		line(sb, "applyCSSProperty ", applyCSSPropertyCount, applyCSSPropertyNs); //$NON-NLS-1$
		line(sb, "  handlerLookup  ", handlerLookupCount, handlerLookupNs); //$NON-NLS-1$
		line(sb, "  handlerInvoke  ", handlerInvokeCount, handlerInvokeNs); //$NON-NLS-1$
		line(sb, "applyInlineStyle ", applyInlineStyleCount, applyInlineStyleNs); //$NON-NLS-1$
		lineTotalOnly(sb, "applyStyles entry", applyStylesEntryNs); //$NON-NLS-1$
		lineTotalOnly(sb, "pseudoCascade    ", pseudoCascadeNs); //$NON-NLS-1$
		lineTotalOnly(sb, "childRecursion   ", childRecursionNs); //$NON-NLS-1$
		return sb.toString();
	}

	private static void lineTotalOnly(StringBuilder sb, String label, AtomicLong ns) {
		double totalMs = ns.get() / 1_000_000.0;
		sb.append(String.format("  %s                                    total=%9.2fms%n", label, totalMs)); //$NON-NLS-1$
	}

	private static void line(StringBuilder sb, String label, AtomicLong count, AtomicLong ns) {
		long c = count.get();
		long n = ns.get();
		double totalMs = n / 1_000_000.0;
		double avgUs = c == 0 ? 0 : n / 1000.0 / c;
		sb.append(String.format("  %s count=%9d total=%9.2fms avg=%9.2fus%n", label, c, totalMs, avgUs)); //$NON-NLS-1$
	}

	private CSSCorePolicy() {
		// statics only
	}
}
