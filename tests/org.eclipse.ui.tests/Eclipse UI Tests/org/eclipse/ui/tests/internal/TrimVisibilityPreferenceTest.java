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
package org.eclipse.ui.tests.internal;

import static org.eclipse.ui.tests.harness.util.UITestUtil.openTestWindow;
import static org.eclipse.ui.tests.harness.util.UITestUtil.processEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.WorkbenchWindow;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.tests.harness.util.CloseTestWindowsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests that the coolbar follows the workspace preference unless a window
 * carries an explicit override.
 */
@ExtendWith(CloseTestWindowsExtension.class)
public class TrimVisibilityPreferenceTest {

	private static final String KEY = IPreferenceConstants.COOLBAR_VISIBLE;

	private WorkbenchWindow window;

	private IPreferenceStore preferences;

	@BeforeEach
	public void setUp() throws Exception {
		preferences = PrefUtil.getInternalPreferenceStore();
		preferences.setToDefault(KEY);
		window = (WorkbenchWindow) openTestWindow();
		processEvents();
	}

	@AfterEach
	public void tearDown() {
		preferences.setToDefault(KEY);
		processEvents();
	}

	@Test
	public void testWindowWithoutOverrideFollowsPreference() {
		assertFalse(persistedState().containsKey(KEY), "a fresh window must not pin the value");
		assertTrue(window.getCoolBarVisible());

		preferences.setValue(KEY, false);
		processEvents();

		assertFalse(window.getCoolBarVisible());
		assertFalse(persistedState().containsKey(KEY), "following the preference must not create an override");
	}

	@Test
	public void testTogglingAwayFromPreferenceStoresOverride() {
		window.setCoolBarVisible(false);

		assertEquals(Boolean.FALSE.toString(), persistedState().get(KEY));
		assertFalse(window.getCoolBarVisible());
	}

	@Test
	public void testTogglingBackToPreferenceDropsOverride() {
		window.setCoolBarVisible(false);
		window.setCoolBarVisible(true);

		assertFalse(persistedState().containsKey(KEY), "an override equal to the preference must be dropped");
	}

	@Test
	public void testOverrideSurvivesPreferenceChange() {
		window.setCoolBarVisible(false);

		preferences.setValue(KEY, true);
		processEvents();

		assertFalse(window.getCoolBarVisible(), "an explicit choice must win over the preference");
		assertEquals(Boolean.FALSE.toString(), persistedState().get(KEY));
	}

	private Map<String, String> persistedState() {
		return window.getModel().getPersistedState();
	}
}
