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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight performance tracer for renderer hotspots.
 * <p>
 * Output file defaults to {@code $HOME/renderer-perf-trace.csv} and can be
 * overridden with {@code -Declipse.renderer.perf.trace.file=<path>}.
 * <p>
 * The CSV format is:
 * {@code timestamp_ms,hotspot_id,duration_ns,detail}
 * <p>
 * Trace records are queued lock-free on the producer side and drained by a
 * single daemon flusher thread every 500 ms. The flusher holds one open
 * {@link BufferedWriter} for the lifetime of the JVM; a shutdown hook drains
 * the queue and closes the writer so the last events are not lost.
 */
public final class RendererPerfTracer {

	/** Master switch. Always enabled in this debug build. */
	public static final boolean ENABLED = true;

	// Hotspot IDs matching the items in docs/performance.md.
	// H08 is intentionally unused: an earlier draft reserved it for a
	// ContentProvider hotspot that turned out to be negligible during analysis.
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
	private static final Path OUTPUT_FILE;
	private static final long START_TIME = System.nanoTime();
	private static final Object WRITER_LOCK = new Object();
	private static final BufferedWriter WRITER;
	private static final ScheduledExecutorService FLUSHER;

	static {
		String fileProp = System.getProperty("eclipse.renderer.perf.trace.file"); //$NON-NLS-1$
		if (fileProp != null) {
			OUTPUT_FILE = Path.of(fileProp);
		} else {
			OUTPUT_FILE = Path.of(System.getProperty("user.home"), "renderer-perf-trace.csv"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		BufferedWriter writer = null;
		if (ENABLED) {
			try {
				writer = Files.newBufferedWriter(OUTPUT_FILE,
						StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING);
				writer.write("timestamp_ms,hotspot_id,duration_ns,detail\n"); //$NON-NLS-1$
				writer.flush();
			} catch (IOException e) {
				System.err.println("RendererPerfTracer: failed to open " + OUTPUT_FILE + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
				writer = null;
			}
		}
		WRITER = writer;

		FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "RendererPerfTracer-Flusher"); //$NON-NLS-1$
			t.setDaemon(true);
			return t;
		});
		if (ENABLED && WRITER != null) {
			FLUSHER.scheduleWithFixedDelay(RendererPerfTracer::flush, 500, 500, TimeUnit.MILLISECONDS);
			Runtime.getRuntime().addShutdownHook(
					new Thread(RendererPerfTracer::shutdown, "RendererPerfTracer-Shutdown")); //$NON-NLS-1$
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
		long elapsedMs = (System.nanoTime() - START_TIME) / 1_000_000L;
		String line = elapsedMs + "," + hotspotId + "," + durationNs + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				+ (detail != null ? detail : "") + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		QUEUE.add(line);
	}

	/**
	 * Record a count-only event (no duration).
	 *
	 * @param hotspotId one of the H* or W* constants
	 * @param detail    short context string
	 */
	public static void count(String hotspotId, String detail) {
		long elapsedMs = (System.nanoTime() - START_TIME) / 1_000_000L;
		String line = elapsedMs + "," + hotspotId + ",0," //$NON-NLS-1$ //$NON-NLS-2$
				+ (detail != null ? detail : "") + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		QUEUE.add(line);
	}

	private static void flush() {
		synchronized (WRITER_LOCK) {
			if (WRITER == null) {
				return;
			}
			try {
				String line;
				while ((line = QUEUE.poll()) != null) {
					WRITER.write(line);
				}
				WRITER.flush();
			} catch (IOException e) {
				// Silently drop. Tracing must not break the workbench.
			}
		}
	}

	private static void shutdown() {
		FLUSHER.shutdown();
		try {
			FLUSHER.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		flush();
		synchronized (WRITER_LOCK) {
			if (WRITER != null) {
				try {
					WRITER.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}
}
