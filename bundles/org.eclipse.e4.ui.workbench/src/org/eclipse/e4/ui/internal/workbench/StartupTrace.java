/*******************************************************************************
 * Local-only startup tracer for measuring workbench init phases.
 * NOT FOR UPSTREAM MERGE. Tracing is always on in this build.
 *******************************************************************************/
package org.eclipse.e4.ui.internal.workbench;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight startup tracer. No-op unless {@code -Declipse.startup.trace=true}
 * is set. Records phase durations into a concurrent queue; on JVM shutdown,
 * appends rows to {@code ${user.home}/.eclipse/startup-trace.csv} and prints a
 * sorted cumulative-time summary to stdout.
 */
public final class StartupTrace {

	public static final boolean ENABLED = true;

	private static final ConcurrentLinkedQueue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();
	private static final AtomicLong SEQ = new AtomicLong();
	private static final AtomicBoolean DUMPED = new AtomicBoolean();
	private static final String RUN_ID = Long.toHexString(System.currentTimeMillis()) + "-" //$NON-NLS-1$
			+ Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

	static {
		if (ENABLED) {
			Runtime.getRuntime().addShutdownHook(new Thread(StartupTrace::dump, "StartupTrace-dump")); //$NON-NLS-1$
			System.out.println("[StartupTrace] enabled, runId=" + RUN_ID); //$NON-NLS-1$
		}
	}

	private StartupTrace() {
	}

	/** Returns a start timestamp (ns). Returns 0 when tracing is disabled. */
	public static long begin() {
		return ENABLED ? System.nanoTime() : 0L;
	}

	/** Records a finished span. No-op when tracing is disabled. */
	public static void record(String phase, long startNanos) {
		if (!ENABLED) {
			return;
		}
		long end = System.nanoTime();
		ENTRIES.add(new Entry(SEQ.getAndIncrement(), phase, startNanos, end, Thread.currentThread().getName()));
	}

	/** Convenience: time a Runnable. */
	public static void time(String phase, Runnable r) {
		if (!ENABLED) {
			r.run();
			return;
		}
		long t = System.nanoTime();
		try {
			r.run();
		} finally {
			record(phase, t);
		}
	}

	/** Force a dump now (also runs automatically on JVM shutdown). */
	public static void dump() {
		if (!ENABLED || !DUMPED.compareAndSet(false, true)) {
			return;
		}
		try {
			Path dir = Path.of(System.getProperty("user.home"), ".eclipse"); //$NON-NLS-1$ //$NON-NLS-2$
			Files.createDirectories(dir);
			Path csv = dir.resolve("startup-trace.csv"); //$NON-NLS-1$
			boolean writeHeader = !Files.exists(csv);
			List<Entry> snapshot = new ArrayList<>(ENTRIES);
			snapshot.sort(Comparator.comparingLong(e -> e.seq));
			try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {
				if (writeHeader) {
					w.write("runId,seq,phase,startNs,endNs,durationUs,thread\n"); //$NON-NLS-1$
				}
				for (Entry e : snapshot) {
					long durUs = (e.endNs - e.startNs) / 1000L;
					w.write(RUN_ID);
					w.write(',');
					w.write(Long.toString(e.seq));
					w.write(',');
					w.write(csvEscape(e.phase));
					w.write(',');
					w.write(Long.toString(e.startNs));
					w.write(',');
					w.write(Long.toString(e.endNs));
					w.write(',');
					w.write(Long.toString(durUs));
					w.write(',');
					w.write(csvEscape(e.thread));
					w.write('\n');
				}
			}
			printSummary(csv, snapshot);
		} catch (IOException ex) {
			System.err.println("[StartupTrace] failed to dump: " + ex); //$NON-NLS-1$
		}
	}

	private static void printSummary(Path csv, List<Entry> snapshot) {
		Map<String, long[]> agg = new HashMap<>();
		for (Entry e : snapshot) {
			long dur = e.endNs - e.startNs;
			long[] a = agg.computeIfAbsent(e.phase, k -> new long[2]);
			a[0] += dur;
			a[1] += 1;
		}
		List<Map.Entry<String, long[]>> sorted = new ArrayList<>(agg.entrySet());
		sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		System.out.println("[StartupTrace] runId=" + RUN_ID + " entries=" + snapshot.size() + " csv=" + csv); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		System.out.println("[StartupTrace] top phases by cumulative time:"); //$NON-NLS-1$
		System.out.printf("  %10s  %5s  %s%n", "cum_ms", "count", "phase"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		int n = Math.min(40, sorted.size());
		for (int i = 0; i < n; i++) {
			Map.Entry<String, long[]> m = sorted.get(i);
			double ms = m.getValue()[0] / 1_000_000.0;
			long count = m.getValue()[1];
			System.out.printf("  %10.3f  %5d  %s%n", ms, count, m.getKey()); //$NON-NLS-1$
		}
	}

	private static String csvEscape(String s) {
		if (s == null) {
			return ""; //$NON-NLS-1$
		}
		if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) {
			return s;
		}
		return "\"" + s.replace("\"", "\"\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	private static final class Entry {
		final long seq;
		final String phase;
		final long startNs;
		final long endNs;
		final String thread;

		Entry(long seq, String phase, long startNs, long endNs, String thread) {
			this.seq = seq;
			this.phase = phase;
			this.startNs = startNs;
			this.endNs = endNs;
			this.thread = thread;
		}
	}
}
