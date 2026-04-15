/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.e4.ui.workbench.renderers.swt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.swt.widgets.Display;

/**
 * Lightweight performance tracer for renderer hotspots.
 * <p>
 * Output file defaults to {@code $HOME/renderer-perf-trace.csv} and can be
 * overridden with {@code -Declipse.renderer.perf.trace.file=<path>}.
 * <p>
 * The CSV format is:
 * {@code timestamp_ms,hotspot_id,duration_ns,detail}
 * <p>
 * Trace records are queued lock-free and flushed asynchronously to avoid
 * blocking the UI thread.
 */
public final class RendererPerfTracer {

	/** Master switch — always enabled in this debug build. */
	public static final boolean ENABLED = true;

	// Hotspot IDs matching the items in docs/performance.md
	public static final String H01_FIND_ACTIVE_ELEMENTS = "H01_findActiveElements"; //$NON-NLS-1$
	public static final String H02_FIND_PLACEHOLDERS_LABEL = "H02_findPlaceholders_label"; //$NON-NLS-1$
	public static final String H02_FIND_PLACEHOLDERS_ITEM = "H02_findPlaceholders_item"; //$NON-NLS-1$
	public static final String H03_TOOLBAR_UPDATE_WIDGET = "H03_toolbar_updateWidget"; //$NON-NLS-1$
	public static final String H04_DIRTY_ALL_SELECTOR = "H04_dirty_allSelector"; //$NON-NLS-1$
	public static final String H05_TOOL_ITEM_UPDATER = "H05_toolItemUpdater"; //$NON-NLS-1$
	public static final String H06_MENU_SCHEDULE_UPDATE = "H06_menu_scheduleUpdate"; //$NON-NLS-1$
	public static final String H07_MENU_CONTEXT_PER_ITEM = "H07_menu_contextPerItem"; //$NON-NLS-1$
	public static final String H09_WBW_FIND_STACKS = "H09_wbw_findStacks"; //$NON-NLS-1$
	public static final String H10_SHOW_TAB_NO_BATCH = "H10_showTab_noBatch"; //$NON-NLS-1$
	public static final String H11_LIMBO_REPARENT = "H11_limbo_reparent"; //$NON-NLS-1$
	public static final String H12_AREA_SYNCH_CTF = "H12_area_synchCTF"; //$NON-NLS-1$
	public static final String H13_TOOLCTRL_STARTUP_SCAN = "H13_toolCtrl_startupScan"; //$NON-NLS-1$
	public static final String H14_RAT_UNCOALESCED = "H14_runAndTrack_uncoalesced"; //$NON-NLS-1$
	public static final String W1_SASH_SYNC_LAYOUT = "W1_sash_syncLayout_win"; //$NON-NLS-1$

	private static final ConcurrentLinkedQueue<String> QUEUE = new ConcurrentLinkedQueue<>();
	private static final AtomicBoolean FLUSH_SCHEDULED = new AtomicBoolean(false);
	private static final Path OUTPUT_FILE;
	private static final long START_TIME = System.nanoTime();

	static {
		String fileProp = System.getProperty("eclipse.renderer.perf.trace.file"); //$NON-NLS-1$
		if (fileProp != null) {
			OUTPUT_FILE = Path.of(fileProp);
		} else {
			OUTPUT_FILE = Path.of(System.getProperty("user.home"), "renderer-perf-trace.csv"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (ENABLED) {
			try {
				Files.writeString(OUTPUT_FILE,
						"timestamp_ms,hotspot_id,duration_ns,detail\n", //$NON-NLS-1$
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				System.err.println("RendererPerfTracer: failed to open " + OUTPUT_FILE + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	private RendererPerfTracer() {
	}

	/** Capture start time. Call this at the beginning of the hotspot. */
	public static long begin() {
		return System.nanoTime();
	}

	/**
	 * Record a trace event.
	 *
	 * @param hotspotId one of the H* or W* constants
	 * @param startNano value returned by {@link #begin()}
	 * @param detail    short context string (e.g., element count, class name).
	 *                  May be {@code null}.
	 */
	public static void trace(String hotspotId, long startNano, String detail) {
		long durationNs = System.nanoTime() - startNano;
		long wallMs = (System.nanoTime() - START_TIME) / 1_000_000L;
		String line = wallMs + "," + hotspotId + "," + durationNs + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ (detail != null ? detail : "") + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		QUEUE.add(line);
		scheduleFlush();
	}

	/**
	 * Record a count-only event (no duration).
	 *
	 * @param hotspotId one of the H* or W* constants
	 * @param detail    short context string
	 */
	public static void count(String hotspotId, String detail) {
		long wallMs = (System.nanoTime() - START_TIME) / 1_000_000L;
		String line = wallMs + "," + hotspotId + ",0," //$NON-NLS-1$ //$NON-NLS-2$
				+ (detail != null ? detail : "") + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		QUEUE.add(line);
		scheduleFlush();
	}

	private static void scheduleFlush() {
		if (FLUSH_SCHEDULED.compareAndSet(false, true)) {
			Display display = Display.getDefault();
			if (display != null && !display.isDisposed()) {
				display.timerExec(500, RendererPerfTracer::flush);
			} else {
				flush();
			}
		}
	}

	private static void flush() {
		FLUSH_SCHEDULED.set(false);
		try (BufferedWriter writer = Files.newBufferedWriter(OUTPUT_FILE,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			String line;
			while ((line = QUEUE.poll()) != null) {
				writer.write(line);
			}
		} catch (IOException e) {
			// Silently drop — tracing must not break the workbench
		}
	}
}
