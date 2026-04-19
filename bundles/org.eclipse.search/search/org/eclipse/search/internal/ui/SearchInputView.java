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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.WorkbenchActivityHelper;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.part.ViewPart;

import org.eclipse.search.internal.ui.util.ExceptionHandler;
import org.eclipse.search.ui.IReplacePage;
import org.eclipse.search.ui.ISearchPage;
import org.eclipse.search.ui.ISearchPageContainer;

/**
 * Re-hosts the existing search pages (contributed via
 * {@code org.eclipse.search.searchPages}) inside a workbench view instead of a
 * modal dialog. Activated via the
 * {@link SearchPreferencePage#USE_SEARCH_VIEW_FOR_CTRL_H} preference.
 */
public class SearchInputView extends ViewPart implements ISearchPageContainerSite {

	public static final String VIEW_ID = "org.eclipse.search.ui.views.SearchInputView"; //$NON-NLS-1$

	private final List<SearchPageDescriptor> fDescriptors;
	private final List<Composite> fPageWrappers = new ArrayList<>();
	private final List<ScopePart> fScopeParts = new ArrayList<>();

	private Combo fPageCombo;
	private Composite fPageStack;
	private StackLayout fStackLayout;
	private Button fSearchButton;
	private Button fReplaceButton;

	private int fCurrentIndex = -1;
	private ISearchPage fCurrentPage;
	private ISelection fCurrentSelection;
	private String[] fCurrentEnclosingProject = new String[0];
	private boolean fLastEnableState = true;

	public SearchInputView() {
		fDescriptors = filterByActivities(SearchPlugin.getDefault().getEnabledSearchPageDescriptors(null));
	}

	@Override
	public void createPartControl(Composite parent) {
		refreshSelectionContext();

		Composite root = new Composite(parent, SWT.NONE);
		GridLayout rootLayout = new GridLayout(1, false);
		rootLayout.marginWidth = 5;
		rootLayout.marginHeight = 5;
		root.setLayout(rootLayout);

		if (fDescriptors.isEmpty()) {
			Label empty = new Label(root, SWT.WRAP);
			empty.setText(SearchMessages.SearchDialog_noSearchExtension);
			empty.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
			return;
		}

		Composite header = new Composite(root, SWT.NONE);
		header.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
		GridLayout headerLayout = new GridLayout(2, false);
		headerLayout.marginWidth = 0;
		headerLayout.marginHeight = 0;
		header.setLayout(headerLayout);

		Label pageLabel = new Label(header, SWT.NONE);
		pageLabel.setText(SearchMessages.SearchInputView_pageLabel);

		fPageCombo = new Combo(header, SWT.READ_ONLY | SWT.DROP_DOWN);
		fPageCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		for (SearchPageDescriptor descriptor : fDescriptors) {
			fPageCombo.add(LegacyActionTools.removeMnemonics(descriptor.getLabel()));
		}
		fPageCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showPage(fPageCombo.getSelectionIndex());
			}
		});

		fPageStack = new Composite(root, SWT.NONE);
		fStackLayout = new StackLayout();
		fPageStack.setLayout(fStackLayout);
		fPageStack.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		for (int i = 0; i < fDescriptors.size(); i++) {
			fPageWrappers.add(null);
			fScopeParts.add(null);
		}

		Composite buttons = new Composite(root, SWT.NONE);
		buttons.setLayoutData(new GridData(SWT.END, SWT.END, true, false));
		GridLayout buttonLayout = new GridLayout(2, false);
		buttonLayout.marginWidth = 0;
		buttonLayout.marginHeight = 0;
		buttons.setLayout(buttonLayout);

		fReplaceButton = new Button(buttons, SWT.PUSH);
		fReplaceButton.setText(SearchMessages.SearchInputView_replaceAction);
		fReplaceButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		fReplaceButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				performReplace();
			}
		});
		fReplaceButton.setVisible(false);

		fSearchButton = new Button(buttons, SWT.PUSH);
		fSearchButton.setText(SearchMessages.SearchInputView_searchAction);
		fSearchButton.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		fSearchButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				performSearch();
			}
		});

		PlatformUI.getWorkbench().getHelpSystem().setHelp(root, ISearchHelpContextIds.SEARCH_DIALOG);

		fPageCombo.select(0);
		showPage(0);
	}

	private void performSearch() {
		if (fCurrentPage == null) {
			return;
		}
		boolean ok = fCurrentPage.performAction();
		if (ok) {
			// Bring the result view to front — the input view stays put.
			IWorkbenchWindow window = getWorkbenchWindow();
			if (window != null && window.getActivePage() != null) {
				try {
					window.getActivePage().showView("org.eclipse.search.ui.views.SearchView"); //$NON-NLS-1$
				} catch (Exception ignore) {
					// best effort
				}
			}
		}
	}

	private void performReplace() {
		if (!(fCurrentPage instanceof IReplacePage replacePage)) {
			return;
		}
		boolean wasAutoBuilding = SearchPlugin.setAutoBuilding(false);
		try {
			replacePage.performReplace();
		} finally {
			SearchPlugin.setAutoBuilding(wasAutoBuilding);
		}
	}

	private void showPage(int index) {
		if (index < 0 || index >= fDescriptors.size()) {
			return;
		}
		if (index == fCurrentIndex) {
			return;
		}

		ISearchPage oldPage = fCurrentPage;
		if (oldPage != null) {
			oldPage.setVisible(false);
		}

		Composite wrapper = fPageWrappers.get(index);
		if (wrapper == null) {
			wrapper = createPageControl(fDescriptors.get(index), index);
			fPageWrappers.set(index, wrapper);
		}
		fStackLayout.topControl = wrapper;
		fPageStack.layout();

		fCurrentIndex = index;
		fCurrentPage = fDescriptors.get(index).getPage();

		setPerformActionEnabled(fCurrentPage != null);
		if (fCurrentPage != null) {
			fCurrentPage.setVisible(true);
		}

		fReplaceButton.setVisible(fCurrentPage instanceof IReplacePage);
		fReplaceButton.getParent().layout();
	}

	private Composite createPageControl(SearchPageDescriptor descriptor, int index) {
		Composite wrapper = new Composite(fPageStack, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		wrapper.setLayout(layout);

		final Composite pageHost = new Composite(wrapper, SWT.NONE);
		GridLayout hostLayout = new GridLayout();
		hostLayout.marginWidth = 0;
		hostLayout.marginHeight = 0;
		pageHost.setLayout(hostLayout);
		pageHost.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));

		SafeRunner.run(new ISafeRunnable() {
			@Override
			public void run() throws Exception {
				ISearchPage page = descriptor.createObject(SearchInputView.this);
				if (page != null) {
					page.createControl(pageHost);
					Control control = page.getControl();
					if (control != null) {
						control.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
					}
				}
			}

			@Override
			public void handleException(Throwable ex) {
				if (ex instanceof CoreException coreEx) {
					ExceptionHandler.handle(coreEx, getSite().getShell(),
							SearchMessages.Search_Error_createSearchPage_title,
							Messages.format(SearchMessages.Search_Error_createSearchPage_message,
									descriptor.getLabel()));
				} else {
					ExceptionHandler.displayMessageDialog(ex, getSite().getShell(),
							SearchMessages.Search_Error_createSearchPage_title,
							Messages.format(SearchMessages.Search_Error_createSearchPage_message,
									descriptor.getLabel()));
				}
			}
		});

		if (descriptor.showScopeSection()) {
			Composite scopeHost = new Composite(wrapper, SWT.NONE);
			scopeHost.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
			scopeHost.setLayout(new GridLayout());
			ScopePart scopePart = new ScopePart(this, descriptor.canSearchInProjects(),
					descriptor.canSearchInOpenedEditors());
			Control scopeControl = scopePart.createPart(scopeHost);
			scopeControl.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
			scopePart.setVisible(true);
			fScopeParts.set(index, scopePart);
		}

		return wrapper;
	}

	private static List<SearchPageDescriptor> filterByActivities(List<SearchPageDescriptor> input) {
		List<SearchPageDescriptor> filtered = new ArrayList<>(input.size());
		for (SearchPageDescriptor descriptor : input) {
			if (!WorkbenchActivityHelper.filterItem(descriptor)) {
				filtered.add(descriptor);
			}
		}
		return filtered;
	}

	@Override
	public void setFocus() {
		if (fPageCombo != null && !fPageCombo.isDisposed()) {
			fPageCombo.setFocus();
		}
	}

	@Override
	public void dispose() {
		for (SearchPageDescriptor descriptor : fDescriptors) {
			descriptor.dispose();
		}
		super.dispose();
	}

	// ---- ISearchPageContainer ------------------------------------------------

	@Override
	public ISelection getSelection() {
		return fCurrentSelection;
	}

	@Override
	public IRunnableContext getRunnableContext() {
		return PlatformUI.getWorkbench().getProgressService();
	}

	@Override
	public void setPerformActionEnabled(boolean state) {
		fLastEnableState = state;
		if (fSearchButton != null && !fSearchButton.isDisposed()) {
			fSearchButton.setEnabled(state && hasValidScope());
		}
	}

	@Override
	public int getSelectedScope() {
		ScopePart scope = currentScopePart();
		if (scope == null) {
			return ISearchPageContainer.WORKSPACE_SCOPE;
		}
		return scope.getSelectedScope();
	}

	@Override
	public void setSelectedScope(int scope) {
		ScopePart part = currentScopePart();
		if (part != null) {
			part.setSelectedScope(scope);
		}
	}

	@Override
	public boolean hasValidScope() {
		return getSelectedScope() != WORKING_SET_SCOPE || getSelectedWorkingSets() != null;
	}

	@Override
	public void setActiveEditorCanProvideScopeSelection(boolean state) {
		ScopePart part = currentScopePart();
		if (part != null) {
			part.setActiveEditorCanProvideScopeSelection(state);
		}
	}

	@Override
	public IEditorInput getActiveEditorInput() {
		IEditorPart editor = getActiveEditor();
		if (editor == null) {
			return null;
		}
		if (editor instanceof MultiPageEditorPart multi) {
			Object page = multi.getSelectedPage();
			if (page instanceof IEditorPart innerEditor) {
				editor = innerEditor;
			} else {
				return null;
			}
		}
		return editor.getEditorInput();
	}

	@Override
	public IWorkingSet[] getSelectedWorkingSets() {
		ScopePart part = currentScopePart();
		if (part == null) {
			return null;
		}
		return part.getSelectedWorkingSets();
	}

	@Override
	public void setSelectedWorkingSets(IWorkingSet[] workingSets) {
		ScopePart part = currentScopePart();
		if (part != null) {
			part.setSelectedWorkingSets(workingSets);
		}
	}

	@Override
	public String[] getSelectedProjectNames() {
		if (getSelectedScope() == SELECTED_PROJECTS_SCOPE) {
			return fCurrentEnclosingProject;
		}
		return null;
	}

	// ---- ISearchPageContainerSite -------------------------------------------

	@Override
	public IWorkbenchWindow getWorkbenchWindow() {
		return getSite() != null ? getSite().getWorkbenchWindow() : null;
	}

	@Override
	public IEditorPart getActiveEditor() {
		IWorkbenchWindow window = getWorkbenchWindow();
		if (window == null) {
			return null;
		}
		IWorkbenchPage activePage = window.getActivePage();
		if (activePage == null) {
			return null;
		}
		IWorkbenchPart activePart = activePage.getActivePart();
		if (activePart == null || activePart == this) {
			// The view itself is the active part — fall back to the most recent editor.
			return activePage.getActiveEditor();
		}
		IEditorPart activeEditor = activePage.getActiveEditor();
		if (activeEditor == activePart) {
			return activeEditor;
		}
		return null;
	}

	@Override
	public String[] getEnclosingProjectNames() {
		return fCurrentEnclosingProject;
	}

	@Override
	public void notifyScopeSelectionChanged() {
		setPerformActionEnabled(fLastEnableState);
	}

	// ---- Selection context capture ------------------------------------------

	/**
	 * Refreshes the view's understanding of the current workbench selection and
	 * enclosing project. Called on creation and whenever Ctrl+H is pressed
	 * again so the view picks up a freshly selected resource the way the
	 * dialog does on each open.
	 */
	public void refreshSelectionContext() {
		IWorkbenchWindow window = getWorkbenchWindow();
		if (window == null) {
			window = SearchPlugin.getActiveWorkbenchWindow();
		}
		fCurrentSelection = window != null ? window.getSelectionService().getSelection() : null;
		fCurrentEnclosingProject = evaluateEnclosingProject(fCurrentSelection, getActiveEditor());
	}

	private ScopePart currentScopePart() {
		if (fCurrentIndex < 0 || fCurrentIndex >= fScopeParts.size()) {
			return null;
		}
		return fScopeParts.get(fCurrentIndex);
	}

	private static String[] evaluateEnclosingProject(ISelection selection, IEditorPart activeEditor) {
		if (activeEditor != null) {
			String name = evaluateEnclosingProject(activeEditor.getEditorInput());
			if (name != null) {
				return new String[] { name };
			}
		} else if (selection instanceof IStructuredSelection structured) {
			HashSet<String> res = new HashSet<>();
			for (Iterator<?> iter = structured.iterator(); iter.hasNext();) {
				Object curr = iter.next();
				if (curr instanceof IWorkingSet workingSet) {
					if (workingSet.isAggregateWorkingSet() && workingSet.isEmpty()) {
						IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
						for (IProject proj : projects) {
							if (proj.isOpen()) {
								res.add(proj.getName());
							}
						}
					} else {
						for (IAdaptable element : workingSet.getElements()) {
							String name = evaluateEnclosingProject(element);
							if (name != null) {
								res.add(name);
							}
						}
					}
				} else if (curr instanceof IAdaptable adaptable) {
					String name = evaluateEnclosingProject(adaptable);
					if (name != null) {
						res.add(name);
					}
				}
			}
			if (!res.isEmpty()) {
				return res.toArray(new String[res.size()]);
			}
		}
		return new String[0];
	}

	private static String evaluateEnclosingProject(IAdaptable adaptable) {
		IProject project = adaptable.getAdapter(IProject.class);
		if (project == null) {
			IResource resource = adaptable.getAdapter(IResource.class);
			if (resource != null) {
				project = resource.getProject();
			}
		}
		if (project != null && project.isAccessible()) {
			return project.getName();
		}
		return null;
	}

}
