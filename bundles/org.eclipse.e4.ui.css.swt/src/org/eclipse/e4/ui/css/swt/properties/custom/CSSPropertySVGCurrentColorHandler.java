/*******************************************************************************
 * Copyright (c) 2026 Vogella GmbH and others.
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
package org.eclipse.e4.ui.css.swt.properties.custom;

import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.helpers.CSSSWTColorHelper;
import org.eclipse.e4.ui.css.swt.properties.AbstractCSSPropertySWTHandler;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.svg.JSVGRasterizer;
import org.eclipse.swt.widgets.Control;
import org.w3c.dom.css.CSSValue;

/**
 * Handler for <code>swt-svg-current-color</code>, the color SVG images use for
 * <code>currentColor</code>. The setting is global for the application, so the
 * value of the last styled shell wins. It only affects images rasterized after
 * it was applied; images rasterized earlier keep their color until the IDE is
 * restarted.
 */
public class CSSPropertySVGCurrentColorHandler extends AbstractCSSPropertySWTHandler {

	private static final String SVG_CURRENT_COLOR_PROP = "swt-svg-current-color"; //$NON-NLS-1$

	@Override
	protected void applyCSSProperty(Control control, String property, CSSValue value, String pseudo, CSSEngine engine)
			throws Exception {
		if (!SVG_CURRENT_COLOR_PROP.equals(property)) {
			return;
		}
		Color color = CSSSWTColorHelper.getSWTColor(value, control.getDisplay());
		if (color == null) {
			return;
		}
		try {
			JSVGRasterizer.setCurrentColor(color.getRGB());
		} catch (NoClassDefFoundError e) {
			// SVG support (org.eclipse.swt.svg) is not installed
		}
	}

	@Override
	protected String retrieveCSSProperty(Control control, String property, String pseudo, CSSEngine engine)
			throws Exception {
		return null;
	}
}
