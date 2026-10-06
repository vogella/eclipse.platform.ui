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
 *     Lars Vogel - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.dialogs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.text.StringMatcher;
import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.PlatformUI;

/**
 * Highlights the controls of a preference page whose text matches the filter
 * text of the preference dialog.
 */
class PreferencePageSearchHighlighter {

	private static final String HIGHLIGHT_COLOR = "org.eclipse.ui.workbench.PREFERENCE_SEARCH_HIGHLIGHT"; //$NON-NLS-1$

	private static final Color DEFAULT_HIGHLIGHT_COLOR = new Color(255, 230, 120);

	private final Map<Control, Color> highlightedBackgrounds = new LinkedHashMap<>();

	/**
	 * Highlights the matches of {@code filter} below {@code pageControl}, replacing
	 * any previous highlighting, and reveals the first match.
	 */
	void highlight(Control pageControl, String filter) {
		clear();
		if (pageControl == null || pageControl.isDisposed() || filter == null || filter.isBlank()) {
			return;
		}
		List<StringMatcher> matchers = new ArrayList<>();
		for (String word : filter.trim().split("\\s+")) { //$NON-NLS-1$
			matchers.add(new StringMatcher("*" + word + "*", true, false)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Color color = highlightColor();
		List<Control> matches = new ArrayList<>();
		collectMatches(pageControl, matchers, matches);
		for (Control match : matches) {
			highlightedBackgrounds.put(match, originalBackground(match));
			match.setBackground(color);
		}
		pageControl.getDisplay().asyncExec(() -> reveal(matches));
	}

	/**
	 * Removes all highlighting.
	 */
	void clear() {
		highlightedBackgrounds.forEach((control, background) -> {
			if (!control.isDisposed()) {
				control.setBackground(background);
			}
		});
		highlightedBackgrounds.clear();
	}

	private static void collectMatches(Control control, List<StringMatcher> matchers, List<Control> matches) {
		String text = textOf(control);
		if (text != null && matchesAll(LegacyActionTools.removeMnemonics(text), matchers)) {
			matches.add(control);
		}
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				collectMatches(child, matchers, matches);
			}
		}
	}

	private static boolean matchesAll(String text, List<StringMatcher> matchers) {
		for (StringMatcher matcher : matchers) {
			if (!matcher.match(text)) {
				return false;
			}
		}
		return !text.isBlank();
	}

	private static String textOf(Control control) {
		if (control instanceof Label label && (label.getStyle() & SWT.SEPARATOR) == 0) {
			return label.getText();
		}
		if (control instanceof CLabel label) {
			return label.getText();
		}
		if (control instanceof Button button && (button.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
			return button.getText();
		}
		if (control instanceof Link link) {
			return link.getText().replaceAll("<[^>]*>", ""); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return null;
	}

	/**
	 * Selects the tab holding the first match unless a match is already visible,
	 * then scrolls that match into view.
	 */
	private static void reveal(List<Control> matches) {
		matches.removeIf(Control::isDisposed);
		if (matches.isEmpty()) {
			return;
		}
		Control target = matches.stream().filter(Control::isVisible).findFirst().orElse(null);
		if (target == null) {
			target = matches.get(0);
			selectEnclosingTabs(target);
		}
		for (Composite parent = target.getParent(); parent != null; parent = parent.getParent()) {
			if (parent instanceof ScrolledComposite scrolled) {
				scrolled.showControl(target);
				break;
			}
		}
	}

	private static void selectEnclosingTabs(Control control) {
		Control child = control;
		for (Composite parent = control.getParent(); parent != null; child = parent, parent = parent.getParent()) {
			if (parent instanceof TabFolder folder) {
				for (TabItem item : folder.getItems()) {
					if (item.getControl() == child) {
						folder.setSelection(item);
					}
				}
			} else if (parent instanceof CTabFolder folder) {
				for (CTabItem item : folder.getItems()) {
					if (item.getControl() == child) {
						folder.setSelection(item);
					}
				}
			}
		}
	}

	/**
	 * Returns the background to restore, using the parent's color for an inherited
	 * background since Cocoa cannot reset a Button to transparent.
	 */
	private static Color originalBackground(Control control) {
		Color original = control.getBackground();
		control.setBackground(null);
		boolean inherited = original.equals(control.getBackground());
		return inherited ? control.getParent().getBackground() : original;
	}

	private static Color highlightColor() {
		Color color = PlatformUI.isWorkbenchRunning()
				? PlatformUI.getWorkbench().getThemeManager().getCurrentTheme().getColorRegistry().get(HIGHLIGHT_COLOR)
				: null;
		return color != null ? color : DEFAULT_HIGHLIGHT_COLOR;
	}
}
