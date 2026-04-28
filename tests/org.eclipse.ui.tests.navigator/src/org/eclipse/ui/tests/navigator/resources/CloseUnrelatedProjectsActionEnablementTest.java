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
package org.eclipse.ui.tests.navigator.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.actions.CloseUnrelatedProjectsAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CloseUnrelatedProjectsActionEnablementTest {

	private IProject a;
	private IProject b;
	private IProject c;
	private Shell shell;

	@BeforeEach
	public void setUp() throws CoreException {
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		long suffix = System.nanoTime();
		a = ws.getRoot().getProject("CUPA_A_" + suffix);
		b = ws.getRoot().getProject("CUPA_B_" + suffix);
		c = ws.getRoot().getProject("CUPA_C_" + suffix);
		a.create(null);
		a.open(null);
		b.create(null);
		b.open(null);
		c.create(null);
		c.open(null);

		IProjectDescription aDesc = a.getDescription();
		aDesc.setReferencedProjects(new IProject[] { b });
		a.setDescription(aDesc, null);

		shell = new Shell(Display.getDefault());
	}

	@AfterEach
	public void tearDown() throws CoreException {
		if (shell != null && !shell.isDisposed()) {
			shell.dispose();
		}
		for (IProject p : new IProject[] { a, b, c }) {
			if (p != null && p.exists()) {
				p.delete(true, true, null);
			}
		}
	}

	@Test
	public void testDisabledAfterAllUnrelatedProjectsClosed() throws CoreException {
		CloseUnrelatedProjectsAction action = new CloseUnrelatedProjectsAction(() -> shell);

		action.selectionChanged(new StructuredSelection(a));
		assertTrue(action.isEnabled(),
				"action must be enabled while unrelated open project C exists");

		c.close(null);

		action.selectionChanged(new StructuredSelection(b));
		assertFalse(action.isEnabled(),
				"action must be disabled when no unrelated open project remains");
	}

	@Test
	public void testEnabledWhenUnrelatedOpenProjectExists() {
		CloseUnrelatedProjectsAction action = new CloseUnrelatedProjectsAction(() -> shell);

		action.selectionChanged(new StructuredSelection(a));
		assertTrue(action.isEnabled(), "expected enabled when unrelated open project C exists");

		action.selectionChanged(new StructuredSelection(b));
		assertTrue(action.isEnabled(),
				"expected enabled when unrelated open project C exists (selection B)");
	}

	@Test
	public void testDisabledWhenSelectionCoversAllOpenProjects() throws CoreException {
		c.close(null);
		CloseUnrelatedProjectsAction action = new CloseUnrelatedProjectsAction(() -> shell);
		action.selectionChanged(new StructuredSelection(new Object[] { a, b }));
		assertFalse(action.isEnabled(),
				"action must be disabled when selection plus its references covers all open projects");
	}

	@Test
	public void testListenerInvalidatesGraphOnProjectClose() throws CoreException {
		CloseUnrelatedProjectsAction action = new CloseUnrelatedProjectsAction(() -> shell);

		action.selectionChanged(new StructuredSelection(a));
		assertTrue(action.isEnabled(),
				"action must be enabled while unrelated open project C exists");

		// Closing C fires a POST_CHANGE event; the registered listener
		// invalidates the cached graph and re-evaluates enablement.
		c.close(null);

		// Re-select A without manually calling selectionChanged first —
		// the graph must already be invalidated by the listener.
		action.selectionChanged(new StructuredSelection(a));
		assertFalse(action.isEnabled(),
				"action must be disabled after listener-driven graph invalidation");
	}
}
