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
package org.eclipse.ui.tests.performance;

import static org.eclipse.ui.tests.harness.util.UITestUtil.processEvents;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.e4.ui.css.core.impl.engine.CSSCorePolicy;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.navigator.CommonNavigator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Benchmark for the workbench CSS theme engine. Opens a real workbench with a
 * Java project, 20 editors, a CSS stress view (~4000 mixed widgets), and a set
 * of standard views, then times repeated theme swaps between the e4 light and
 * dark themes.
 *
 * <p>The wall-clock swap medians are noisy (paint, layout, machine load). The
 * stable cross-run metric is the engine-internal applyStyles time, reported per
 * swap and per call from {@link CSSCorePolicy} counters over the measured swaps
 * only; compare that figure between commits, not the wall-clock median.</p>
 *
 * Not part of the default test run. Invoke explicitly via
 *   mvn verify -pl :org.eclipse.ui.tests.performance -Pbuild-individual-bundles \
 *       -Dtest=CssThemeSwapPerformanceTest -DskipTests=false
 */
@Tag("performance")
public class CssThemeSwapPerformanceTest {

	private static final String LIGHT_THEME = "org.eclipse.e4.ui.css.theme.e4_default";
	private static final String DARK_THEME = "org.eclipse.e4.ui.css.theme.e4_dark";
	private static final String THEME_ENGINE_KEY = "org.eclipse.e4.ui.css.swt.theme";

	private static final String CSS_STRESS_VIEW = "org.eclipse.ui.tests.performance.cssStressView";
	private static final String PDE_PERSPECTIVE = "org.eclipse.pde.ui.PDEPerspective";

	/**
	 * Views opened to make the workbench widget tree look like a real session.
	 * Some IDs may not be registered in the test runtime; failures are tolerated.
	 */
	private static final List<String> STANDARD_VIEWS = List.of(
			"org.eclipse.ui.navigator.ProjectExplorer",
			"org.eclipse.ui.views.ProblemView",
			"org.eclipse.ui.views.ContentOutline",
			"org.eclipse.ui.views.TaskList",
			"org.eclipse.ui.views.BookmarkView",
			"org.eclipse.ui.views.AllMarkersView",
			"org.eclipse.ui.views.ProgressView",
			"org.eclipse.ui.views.PropertySheet",
			"org.eclipse.search.ui.views.SearchView",
			"org.eclipse.ui.console.ConsoleView",
			"org.eclipse.pde.runtime.LogView",
			"org.eclipse.team.ui.GenericHistoryView",
			"org.eclipse.team.sync.views.SynchronizeView",
			"org.eclipse.help.ui.HelpView");

	private static final int EDITOR_COUNT = 20;
	private static final int WARMUP_ROUNDS = 5;
	private static final int MEASURE_ROUNDS = 10;
	private static final long PRE_SWAP_WAIT_MS = 5_000;

	private IWorkbenchWindow window;
	private final List<IViewPart> openedViews = new ArrayList<>();
	private IProject project;

	@AfterEach
	public void cleanup() throws Exception {
		if (window != null) {
			IWorkbenchPage page = window.getActivePage();
			if (page != null) {
				page.closeAllEditors(false);
				for (IViewPart v : openedViews) {
					page.hideView(v);
				}
				processEvents();
			}
		}
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	@Test
	public void themeSwap_realWorkbench() throws Exception {
		window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		assertNotNull(window, "Active workbench window must be available");
		window.getShell().setMaximized(true);
		IWorkbenchPage page = window.getActivePage();

		IPerspectiveDescriptor pde = PlatformUI.getWorkbench().getPerspectiveRegistry()
				.findPerspectiveWithId(PDE_PERSPECTIVE);
		if (pde != null) {
			page.setPerspective(pde);
			System.out.println("[CSS-PERF] switched to PDE perspective");
		} else {
			System.out.println("[CSS-PERF] PDE perspective not available; keeping current ("
					+ (page.getPerspective() == null ? "<none>" : page.getPerspective().getId()) + ")");
		}
		processEvents();

		List<IFile> files = createJavaProjectWithFiles("CssPerf", EDITOR_COUNT);
		for (IFile f : files) {
			IDE.openEditor(page, f);
		}
		System.out.println("[CSS-PERF] opened " + files.size() + " editors");

		IViewPart cssView = page.showView(CSS_STRESS_VIEW);
		openedViews.add(cssView);
		IViewPart projectExplorer = null;
		int viewsOpened = 1;
		for (String id : STANDARD_VIEWS) {
			try {
				IViewPart v = page.showView(id);
				openedViews.add(v);
				if ("org.eclipse.ui.navigator.ProjectExplorer".equals(id)) {
					projectExplorer = v;
				}
				viewsOpened++;
			} catch (Exception e) {
				System.out.println("[CSS-PERF] skipping view " + id + ": " + e.getMessage());
			}
		}
		System.out.println("[CSS-PERF] opened " + viewsOpened + " views");

		// keep the CSS stress view on top of its stack even after activating
		// the project explorer
		page.showView(CSS_STRESS_VIEW);

		if (projectExplorer != null) {
			if (projectExplorer instanceof CommonNavigator nav) {
				nav.getCommonViewer().expandToLevel(3);
			}
			page.activate(projectExplorer);
		}
		processEvents();

		Display display = window.getShell().getDisplay();

		System.out.println("[CSS-PERF] workbench populated; waiting "
				+ (PRE_SWAP_WAIT_MS / 1000) + "s for things to settle before the theme swap...");
		pumpEvents(display, PRE_SWAP_WAIT_MS);

		IThemeEngine themeEngine = (IThemeEngine) display.getData(THEME_ENGINE_KEY);
		assertNotNull(themeEngine, "IThemeEngine must be available on the workbench Display");

		System.out.println("[CSS-PERF] registered themes:");
		for (var t : themeEngine.getThemes()) {
			System.out.println("[CSS-PERF]   - " + t.getId() + " (" + t.getLabel() + ")");
		}
		System.out.println("[CSS-PERF] active theme: "
				+ (themeEngine.getActiveTheme() == null ? "<none>" : themeEngine.getActiveTheme().getId()));

		boolean hasDark = themeEngine.getThemes().stream().anyMatch(t -> DARK_THEME.equals(t.getId()));
		boolean hasLight = themeEngine.getThemes().stream().anyMatch(t -> LIGHT_THEME.equals(t.getId()));

		String originalTheme = themeEngine.getActiveTheme() == null
				? LIGHT_THEME
				: themeEngine.getActiveTheme().getId();

		CSSCorePolicy.setPerfEnabled(true);
		CSSCorePolicy.reset();
		if (hasDark && hasLight) {
			runThemeSwapBenchmark(themeEngine);
		} else {
			runApplyStylesBenchmark(themeEngine, window);
		}
		System.out.print(CSSCorePolicy.summary());
		CSSCorePolicy.setPerfEnabled(false);

		swap(themeEngine, originalTheme);
	}

	private List<IFile> createJavaProjectWithFiles(String name, int fileCount) throws Exception {
		IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		if (p.exists()) {
			p.delete(true, true, null);
		}
		p.create(null);
		p.open(null);
		IProjectDescription desc = p.getDescription();
		desc.setNatureIds(new String[] { JavaCore.NATURE_ID });
		p.setDescription(desc, null);

		IFolder src = p.getFolder("src");
		src.create(true, true, null);

		IJavaProject jp = JavaCore.create(p);
		IClasspathEntry[] cp = new IClasspathEntry[] {
				JavaCore.newSourceEntry(src.getFullPath()),
				JavaRuntime.getDefaultJREContainerEntry() };
		jp.setRawClasspath(cp, p.getFolder("bin").getFullPath(), null);

		List<IFile> files = new ArrayList<>(fileCount);
		for (int i = 0; i < fileCount; i++) {
			IFile file = src.getFile("PerfClass" + i + ".java");
			String content = """
					public class PerfClass%d {
						private int counter;
						public void method1() {
							for (int i = 0; i < 10; i++) {
								counter++;
							}
						}
						public String greet(String name) {
							return "hello " + name;
						}
						public int square(int x) {
							return x * x;
						}
					}
					""".formatted(i);
			file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, null);
			files.add(file);
		}
		project = p;
		return files;
	}

	private void runThemeSwapBenchmark(IThemeEngine themeEngine) {
		for (int i = 0; i < WARMUP_ROUNDS; i++) {
			swap(themeEngine, DARK_THEME);
			swap(themeEngine, LIGHT_THEME);
		}
		// Measure the two directions separately: a dark and a light swap style
		// different property sets, so folding them into one median is bimodal.
		// Settle on a clean heap and reset the engine counters after warmup so
		// the stable applyStyles metric covers only the measured swaps.
		System.gc();
		CSSCorePolicy.reset();
		long[] toDark = new long[MEASURE_ROUNDS];
		long[] toLight = new long[MEASURE_ROUNDS];
		for (int i = 0; i < MEASURE_ROUNDS; i++) {
			long t0 = System.nanoTime();
			swap(themeEngine, DARK_THEME);
			toDark[i] = System.nanoTime() - t0;
			t0 = System.nanoTime();
			swap(themeEngine, LIGHT_THEME);
			toLight[i] = System.nanoTime() - t0;
		}
		report("theme swap -> dark ", toDark);
		report("theme swap -> light", toLight);
		reportEngine(2L * MEASURE_ROUNDS);
	}

	private void runApplyStylesBenchmark(IThemeEngine themeEngine, IWorkbenchWindow window) {
		System.out.println("[CSS-PERF] dark/light themes not available; falling back to applyStyles benchmark");
		for (int i = 0; i < WARMUP_ROUNDS; i++) {
			themeEngine.applyStyles(window.getShell(), true);
			processEvents();
		}
		System.gc();
		CSSCorePolicy.reset();
		long[] samples = new long[MEASURE_ROUNDS];
		for (int i = 0; i < MEASURE_ROUNDS; i++) {
			long t0 = System.nanoTime();
			themeEngine.applyStyles(window.getShell(), true);
			processEvents();
			samples[i] = System.nanoTime() - t0;
		}
		report("applyStyles full workbench shell", samples);
		reportEngine(MEASURE_ROUNDS);
	}

	private void swap(IThemeEngine engine, String themeId) {
		engine.setTheme(themeId, false);
		processEvents();
	}

	private void pumpEvents(Display display, long millis) {
		long endAt = System.currentTimeMillis() + millis;
		while (System.currentTimeMillis() < endAt) {
			if (!display.readAndDispatch()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	private void report(String label, long[] ns) {
		long[] sorted = ns.clone();
		Arrays.sort(sorted);
		long min = sorted[0];
		long median = sorted[sorted.length / 2];
		long p95 = sorted[(int) Math.min(sorted.length - 1, sorted.length * 0.95)];
		long max = sorted[sorted.length - 1];
		long sum = 0;
		for (long v : ns) {
			sum += v;
		}
		double meanMs = sum / 1_000_000.0 / ns.length;
		System.out.printf(
				"[CSS-PERF] %s rounds=%d min=%.2fms median=%.2fms p95=%.2fms max=%.2fms mean=%.2fms%n",
				label, ns.length,
				min / 1e6, median / 1e6, p95 / 1e6, max / 1e6, meanMs);
	}

	/**
	 * Reports the engine-internal applyStyles cost, the stable cross-run metric:
	 * instrumented applyStyles time per measured swap and per call. Far less
	 * noisy than the wall-clock swap medians, which include paint and layout.
	 */
	private void reportEngine(long swaps) {
		long calls = CSSCorePolicy.applyStylesCount.get();
		long ns = CSSCorePolicy.applyStylesNs.get();
		double perSwapMs = ns / 1_000_000.0 / swaps;
		double perCallUs = calls == 0 ? 0 : ns / 1000.0 / calls;
		System.out.printf(
				"[CSS-PERF] engine applyStyles: swaps=%d per-swap=%.2fms per-call=%.2fus calls/swap=%d%n",
				swaps, perSwapMs, perCallUs, swaps == 0 ? 0 : calls / swaps);
	}
}
