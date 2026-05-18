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
package org.eclipse.ui.internal.misc;

import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.core.internal.runtime.StartupTrace;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;

/**
 * Installs SWT.PreEvent / SWT.PostEvent listeners on the workbench Display so
 * each dispatched UI event is bracketed and recorded in StartupTrace. Events
 * shorter than {@link #THRESHOLD_NS} are dropped to avoid CSV blow-up; only
 * outliers are reported, identified by SWT event type.
 *
 * Local-only debug instrumentation. NOT FOR UPSTREAM MERGE.
 */
public final class DisplayEventProbe {

	/** Below this duration an event is considered uninteresting and skipped. */
	private static final long THRESHOLD_NS = 16_000_000L; // 16 ms (one 60 Hz frame)

	private static final AtomicBoolean INSTALLED = new AtomicBoolean();
	private static long currentEventStartNs;

	private DisplayEventProbe() {
	}

	/** Installs the listeners once; subsequent calls are no-ops. */
	public static void install(Display display) {
		if (!StartupTrace.ENABLED || display == null || display.isDisposed()) {
			return;
		}
		if (!INSTALLED.compareAndSet(false, true)) {
			return;
		}
		display.addListener(SWT.PreEvent, e -> currentEventStartNs = System.nanoTime());
		display.addListener(SWT.PostEvent, e -> {
			long start = currentEventStartNs;
			if (start == 0L) {
				return;
			}
			currentEventStartNs = 0L;
			long elapsed = System.nanoTime() - start;
			if (elapsed >= THRESHOLD_NS) {
				StartupTrace.record("DisplayEvent." + eventName(e.type), start); //$NON-NLS-1$
			}
		});
	}

	private static String eventName(int type) {
		return switch (type) {
		case SWT.Paint -> "Paint"; //$NON-NLS-1$
		case SWT.Resize -> "Resize"; //$NON-NLS-1$
		case SWT.Selection -> "Selection"; //$NON-NLS-1$
		case SWT.DefaultSelection -> "DefaultSelection"; //$NON-NLS-1$
		case SWT.MouseDown -> "MouseDown"; //$NON-NLS-1$
		case SWT.MouseUp -> "MouseUp"; //$NON-NLS-1$
		case SWT.MouseMove -> "MouseMove"; //$NON-NLS-1$
		case SWT.MouseWheel -> "MouseWheel"; //$NON-NLS-1$
		case SWT.MouseDoubleClick -> "MouseDoubleClick"; //$NON-NLS-1$
		case SWT.MouseEnter -> "MouseEnter"; //$NON-NLS-1$
		case SWT.MouseExit -> "MouseExit"; //$NON-NLS-1$
		case SWT.MouseHover -> "MouseHover"; //$NON-NLS-1$
		case SWT.KeyDown -> "KeyDown"; //$NON-NLS-1$
		case SWT.KeyUp -> "KeyUp"; //$NON-NLS-1$
		case SWT.Traverse -> "Traverse"; //$NON-NLS-1$
		case SWT.FocusIn -> "FocusIn"; //$NON-NLS-1$
		case SWT.FocusOut -> "FocusOut"; //$NON-NLS-1$
		case SWT.Activate -> "Activate"; //$NON-NLS-1$
		case SWT.Deactivate -> "Deactivate"; //$NON-NLS-1$
		case SWT.Show -> "Show"; //$NON-NLS-1$
		case SWT.Hide -> "Hide"; //$NON-NLS-1$
		case SWT.Close -> "Close"; //$NON-NLS-1$
		case SWT.Modify -> "Modify"; //$NON-NLS-1$
		case SWT.Verify -> "Verify"; //$NON-NLS-1$
		case SWT.Move -> "Move"; //$NON-NLS-1$
		case SWT.Expand -> "Expand"; //$NON-NLS-1$
		case SWT.Collapse -> "Collapse"; //$NON-NLS-1$
		case SWT.SetData -> "SetData"; //$NON-NLS-1$
		case SWT.MeasureItem -> "MeasureItem"; //$NON-NLS-1$
		case SWT.PaintItem -> "PaintItem"; //$NON-NLS-1$
		case SWT.EraseItem -> "EraseItem"; //$NON-NLS-1$
		case SWT.MenuDetect -> "MenuDetect"; //$NON-NLS-1$
		case SWT.Dispose -> "Dispose"; //$NON-NLS-1$
		default -> "type" + type; //$NON-NLS-1$
		};
	}
}
