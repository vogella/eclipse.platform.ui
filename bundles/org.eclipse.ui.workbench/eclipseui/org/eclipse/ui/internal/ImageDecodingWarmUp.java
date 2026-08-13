/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Lars Vogel <Lars.Vogel@vogella.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.ImageData;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Initializes the image decoding stack on a background thread while the workbench
 * starts up.
 * <p>
 * Decoding the first SVG loads several hundred classes. Without this that happens on
 * the UI thread, either while the window icons are created or later while the first
 * window paints its icons. Activated eagerly, so that the warm-up already runs while
 * the framework is still starting bundles and the UI thread is waiting for it.
 */
@Component(immediate = true)
public final class ImageDecodingWarmUp {

	/** The fragment of org.eclipse.swt that contributes the SVG rasterizer. */
	private static final String SWT_SVG_FRAGMENT = "org.eclipse.swt.svg"; //$NON-NLS-1$

	private static final AtomicBoolean STARTED = new AtomicBoolean();

	/**
	 * A small document that exercises gradient and path rendering, so that decoding it
	 * reaches most of the rasterizer.
	 */
	private static final String WARM_UP_SVG = """
			<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">\
			<defs><linearGradient id="g"><stop offset="0" stop-color="#000"/>\
			<stop offset="1" stop-color="#fff"/></linearGradient></defs>\
			<rect width="16" height="16" fill="url(#g)"/>\
			<path d="M2 2 L14 14" stroke="#000" stroke-width="2" fill="none"/></svg>"""; //$NON-NLS-1$

	@Activate
	void activate() {
		start();
	}

	/**
	 * Starts the warm-up and returns immediately. Does nothing if it was started
	 * before, if the machine has a single processor, or if the SVG support of SWT is
	 * not installed.
	 */
	public static void start() {
		if (Runtime.getRuntime().availableProcessors() < 2 || Platform.getBundle(SWT_SVG_FRAGMENT) == null
				|| !STARTED.compareAndSet(false, true)) {
			return;
		}
		// a plain thread, the IDE suspends the job manager for the whole startup
		Thread warmUp = new Thread(() -> {
			try {
				new ImageData(new ByteArrayInputStream(WARM_UP_SVG.getBytes(StandardCharsets.UTF_8)));
			} catch (Exception | LinkageError e) {
				// best effort, the first image that is really needed pays the cost instead
			}
		}, "Image decoding warm-up"); //$NON-NLS-1$
		warmUp.setDaemon(true);
		warmUp.start();
	}
}
