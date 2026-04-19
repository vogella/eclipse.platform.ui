/*******************************************************************************
 * Copyright (c) 2026 Vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.search.internal.ui;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;

import org.eclipse.search.ui.ISearchPageContainer;

/**
 * Callback hooks that {@link ScopePart} uses against its hosting container.
 * Extracted so the scope part can be reused by both {@link SearchDialog} and
 * {@link SearchInputView}.
 */
interface ISearchPageContainerSite extends ISearchPageContainer {

	IWorkbenchWindow getWorkbenchWindow();

	IEditorPart getActiveEditor();

	String[] getEnclosingProjectNames();

	void notifyScopeSelectionChanged();
}
