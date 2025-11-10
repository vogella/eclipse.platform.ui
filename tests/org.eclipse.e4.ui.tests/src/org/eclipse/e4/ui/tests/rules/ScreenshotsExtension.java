/*******************************************************************************
 * Copyright (c) 2025 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     vogella GmbH - initial API and implementation
 ******************************************************************************/
package org.eclipse.e4.ui.tests.rules;

import java.io.File;
import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit 5 extension for taking screenshots on test failure.
 * This is a JUnit 5 compatible version that can be used with @RegisterExtension.
 *
 * <p>Usage example:</p>
 * <pre>
 * &#64;RegisterExtension
 * TestWatcher screenshotExtension = ScreenshotsExtension.onFailure(() -&gt; myShell);
 * </pre>
 */
public class ScreenshotsExtension implements TestWatcher {

	private final Supplier<Shell> shellSupplier;
	private final PrintStream out;

	/**
	 * Creates a new screenshot extension.
	 *
	 * @param shellSupplier supplier that provides the shell to focus on, or null for display-wide screenshot
	 * @param out print stream for diagnostics output
	 */
	public ScreenshotsExtension(Supplier<Shell> shellSupplier, PrintStream out) {
		this.shellSupplier = shellSupplier;
		this.out = out != null ? out : System.out;
	}

	/**
	 * Creates a screenshot extension that takes screenshots on test failure.
	 *
	 * @param shellSupplier supplier that provides the shell to focus on, or null for display-wide screenshot
	 * @return a TestWatcher extension for JUnit 5
	 */
	public static TestWatcher onFailure(Supplier<Shell> shellSupplier) {
		return new ScreenshotsExtension(shellSupplier, System.out);
	}

	@Override
	public void testFailed(ExtensionContext context, Throwable cause) {
		String testName = context.getDisplayName();
		Class<?> testClass = context.getRequiredTestClass();

		try {
			String screenshotPath = takeScreenshot(testClass, testName, out);
			out.println("Screenshot saved on test failure: " + screenshotPath);
		} catch (Exception e) {
			out.println("Failed to take screenshot: " + e.getMessage());
			e.printStackTrace(out);
		}
	}

	/**
	 * Takes a screenshot and dumps debugging information.
	 *
	 * @param testClass test class that takes the screenshot
	 * @param name screenshot identifier (e.g. test name)
	 * @param out print stream to use for diagnostics
	 * @return file system path to the screenshot file
	 */
	public static String takeScreenshot(Class<?> testClass, String name, PrintStream out) {
		File resultsHtmlDir = getJunitReportOutput();

		if (resultsHtmlDir == null) {
			// Fallback: use relative path
			File eclipseDir = new File("").getAbsoluteFile();
			resultsHtmlDir = new File(eclipseDir, "../../results/html/").getAbsoluteFile();
		}

		Display display = Display.getCurrent();
		if (display == null) {
			display = Display.getDefault();
		}

		// Wiggle the mouse to ensure any hover states are visible
		Event mouseMove = new Event();
		mouseMove.x = 10;
		mouseMove.y = 10;
		display.post(mouseMove);
		runEventQueue(display);
		mouseMove.x = 20;
		mouseMove.y = 20;
		display.post(mouseMove);
		runEventQueue(display);

		// Dump focus control, parents, and shells
		Control focusControl = display.getFocusControl();
		out.println("FocusControl: ");
		if (focusControl == null) {
			out.println("  null!");
		} else {
			StringBuilder indent = new StringBuilder("  ");
			do {
				out.println(indent.toString() + focusControl);
				focusControl = focusControl.getParent();
				indent.append("  ");
			} while (focusControl != null);
		}

		Shell[] shells = display.getShells();
		if (shells.length > 0) {
			out.println("Shells: ");
			for (Shell shell : shells) {
				out.print(display.getActiveShell() == shell ? "  active, " : "  inactive, ");
				out.print((shell.isVisible() ? "visible: " : "invisible: ") + shell);
				out.println(" @ " + shell.getBounds());
			}
		}

		// Take a screenshot
		GC gc = new GC(display);
		Rectangle displayBounds = display.getBounds();
		out.println("Display @ " + displayBounds);

		final Image image = new Image(display, displayBounds.width, displayBounds.height);
		gc.copyArea(image, 0, 0);
		gc.dispose();

		resultsHtmlDir.mkdirs();

		// Sanitize test name for filename (remove special characters)
		String sanitizedName = name.replaceAll("[^a-zA-Z0-9.-]", "_");
		String filename = new File(
				resultsHtmlDir.getAbsolutePath(),
				testClass.getName() + "." + sanitizedName + ".png").getAbsolutePath();

		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] { image.getImageData() };
		loader.save(filename, SWT.IMAGE_PNG);
		out.println("Screenshot saved to: " + filename);
		image.dispose();

		return filename;
	}

	private static File getJunitReportOutput() {
		String[] args = Platform.getCommandLineArgs();
		for (int i = 0; i < args.length - 1; i++) {
			if ("-junitReportOutput".equals(args[i])) {
				return new File(args[i + 1]).getAbsoluteFile();
			}
		}
		return null;
	}

	private static void runEventQueue(Display display) {
		for (int i = 0; i < 10; i++) {
			while (display.readAndDispatch()) {
				// process events
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
