/*******************************************************************************
 * Copyright (c) 2008, 2015 IBM Corporation and others.
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
 *     Thibault Le Ouay <thibaultleouay@gmail.com> - Bug 443094
 *******************************************************************************/

package org.eclipse.e4.ui.css.swt.helpers;

import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.PROPS_OVERRIDDEN_BY_CSS_PROP;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.SEPARATOR;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.appendOverriddenPropertyName;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.getOverriddenPropertyNames;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.getPreferenceChangeListener;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.removeOverriddenByCssProperty;
import static org.eclipse.e4.ui.css.swt.helpers.EclipsePreferencesHelper.removeOverriddenPropertyNames;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.eclipse.core.internal.preferences.EclipsePreferences;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.junit.jupiter.api.Test;

public class EclipsePreferencesHelperTest {
	private static final String TEST_NODE = "org.eclipse.e4.ui.tests.css.swt.helpers";

	@Test
	void testAppendOverriddenPropertyName() {
		// given
		IEclipsePreferences preferences = spy(new EclipsePreferences());

		// when
		appendOverriddenPropertyName(preferences, "prop1");
		appendOverriddenPropertyName(preferences, "prop2");
		appendOverriddenPropertyName(preferences, "prop3");

		String overriddenPreferences = preferences.get(PROPS_OVERRIDDEN_BY_CSS_PROP, "");

		// then
		assertTrue(overriddenPreferences.contains(SEPARATOR + "prop1" + SEPARATOR));
		assertTrue(overriddenPreferences.contains(SEPARATOR + "prop2" + SEPARATOR));
		assertTrue(overriddenPreferences.contains(SEPARATOR + "prop3" + SEPARATOR));

		verify(preferences, times(1)).addPreferenceChangeListener(getPreferenceChangeListener());
	}

	@Test
	void testGetOverriddenPropertyNames() {
		// given
		IEclipsePreferences preferences = new EclipsePreferences();
		appendOverriddenPropertyName(preferences, "prop1");
		appendOverriddenPropertyName(preferences, "prop2");
		appendOverriddenPropertyName(preferences, "prop3");

		// when
		List<String> propertyNames = getOverriddenPropertyNames(preferences);

		// then
		assertEquals(3, propertyNames.size());
		assertTrue(propertyNames.add("prop1"));
		assertTrue(propertyNames.add("prop2"));
		assertTrue(propertyNames.add("prop3"));
	}

	@Test
	void testRemoveOverriddenPropertyNames() {
		// given
		IEclipsePreferences preferences = spy(new EclipsePreferences());
		appendOverriddenPropertyName(preferences, "prop1");

		// when
		removeOverriddenPropertyNames(preferences);

		// then
		assertNull(preferences.get(PROPS_OVERRIDDEN_BY_CSS_PROP, null));

		verify(preferences, times(1)).removePreferenceChangeListener(getPreferenceChangeListener());
	}

	@Test
	void testRemoveOverriddenByCssProperty() {
		// given
		IEclipsePreferences preferences = new EclipsePreferences();

		// when
		appendOverriddenPropertyName(preferences, "prop1");
		appendOverriddenPropertyName(preferences, "prop2");
		appendOverriddenPropertyName(preferences, "prop3");

		removeOverriddenByCssProperty(preferences, "prop2");

		String overriddenPreferences = preferences.get(PROPS_OVERRIDDEN_BY_CSS_PROP, "");

		// then
		assertTrue(overriddenPreferences.contains(SEPARATOR + "prop1" + SEPARATOR));
		assertFalse(overriddenPreferences.contains(SEPARATOR + "prop2" + SEPARATOR));
		assertTrue(overriddenPreferences.contains(SEPARATOR + "prop3" + SEPARATOR));
	}

	@Test
	void testRemovedCssValueKeepsDifferingDefault() {
		// given: CSS sets bold=false, the default stays true
		IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(TEST_NODE);
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(TEST_NODE);
		try {
			defaults.put("bold", "true");
			preferences.put("bold", "false");
			appendOverriddenPropertyName(preferences, "bold");

			// when: user checks bold, the preference store removes the value equal to
			// the default
			preferences.remove("bold");

			// then: the user value is stored and no longer owned by CSS
			assertEquals("true", preferences.get("bold", null));
			assertFalse(getOverriddenPropertyNames(preferences).contains("bold"));
		} finally {
			removeOverriddenPropertyNames(preferences);
			preferences.remove("bold");
			defaults.remove("bold");
		}
	}

	@Test
	void testRemovedCssValueEqualToDefaultStaysOwnedByCss() {
		// given: CSS value and default are both false
		IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(TEST_NODE);
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(TEST_NODE);
		try {
			defaults.put("bold", "false");
			preferences.put("bold", "false");
			appendOverriddenPropertyName(preferences, "bold");

			// when
			preferences.remove("bold");

			// then
			assertNull(preferences.get("bold", null));
			assertTrue(getOverriddenPropertyNames(preferences).contains("bold"));
		} finally {
			removeOverriddenPropertyNames(preferences);
			defaults.remove("bold");
		}
	}
}
