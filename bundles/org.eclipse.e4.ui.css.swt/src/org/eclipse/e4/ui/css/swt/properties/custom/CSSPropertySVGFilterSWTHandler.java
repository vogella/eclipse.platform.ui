/*******************************************************************************
 * Copyright (c) 2021, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.css.swt.properties.custom;

import org.eclipse.e4.ui.css.core.dom.properties.ICSSPropertyHandler;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.CSSSWTConstants;
import org.eclipse.e4.ui.css.swt.helpers.SWTElementHelpers;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Widget;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.CSSValueList;

public class CSSPropertySVGFilterSWTHandler implements ICSSPropertyHandler {

	@Override
	public boolean applyCSSProperty(Object element, String property, CSSValue value, String pseudo, CSSEngine engine)
			throws Exception {
		Widget widget = SWTElementHelpers.getWidget(element);
		if (widget == null) {
			return false;
		}
		if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
			CSSValueList list = (CSSValueList) value;
			if (list.getLength() == 2) {
				CSSValue first = list.item(0);
				if (first instanceof CSSPrimitiveValue) {
					String text = ((CSSPrimitiveValue) first).getStringValue();
					if ("color".equalsIgnoreCase(text)) { //$NON-NLS-1$
						CSSValue second = list.item(1);
						Color color = (Color) engine.convert(second, Color.class, widget.getDisplay());
						if (color != null) {
							widget.setData(CSSSWTConstants.CSS_SVG_FILTER_COLOR, color.getRGB());
							return true;
						}
					}
				}
			}
		}
		if (value instanceof CSSPrimitiveValue) {
			String text = ((CSSPrimitiveValue) value).getStringValue();
			if ("none".equalsIgnoreCase(text)) { //$NON-NLS-1$
				widget.setData(CSSSWTConstants.CSS_SVG_FILTER_COLOR, null);
				return true;
			}
			// Handle single color value as well, just in case
			Color color = (Color) engine.convert(value, Color.class, widget.getDisplay());
			if (color != null) {
				widget.setData(CSSSWTConstants.CSS_SVG_FILTER_COLOR, color.getRGB());
				return true;
			}
		}
		return false;
	}

	@Override
	public String retrieveCSSProperty(Object element, String property, String pseudo, CSSEngine engine)
			throws Exception {
		Widget widget = SWTElementHelpers.getWidget(element);
		if (widget != null) {
			Object data = widget.getData(CSSSWTConstants.CSS_SVG_FILTER_COLOR);
			if (data != null) {
				return "color " + data.toString(); //$NON-NLS-1$
			}
		}
		return "none"; //$NON-NLS-1$
	}
}
