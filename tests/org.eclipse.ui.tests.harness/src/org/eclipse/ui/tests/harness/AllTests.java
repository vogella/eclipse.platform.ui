/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
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
package org.eclipse.ui.tests.harness;

import org.eclipse.ui.tests.harness.tests.MocksTest;

import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectClasses;

/**
 * Test the test harness
 *
 * @since 3.3
 */

@Suite
@SuiteClasses(MocksTest.class)
public class AllTests {

}
