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
package org.eclipse.ui.internal.ide.application;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;

/**
 * A styled workspace selection dialog with a sidebar navigation, inspired by
 * the DevStyle plugin workspace chooser. Provides pages for Launch, Workspaces,
 * Recent, and Settings with a modern look.
 *
 * <p>
 * The Launch page shows recent workspaces and placeholder recent changes. The
 * Workspaces page provides a combo/browse row for selecting a new workspace.
 * Recent and Settings pages show placeholder content for future implementation.
 * </p>
 */
public class StyledChooseWorkspaceDialog extends Dialog {

	private static final int SIDEBAR_WIDTH = 90;

	private final ChooseWorkspaceData launchData;

	private Composite contentStack;
	private StackLayout stackLayout;
	private Composite launchPage;
	private Composite workspacesPage;
	private Composite recentPage;
	private Composite settingsPage;

	private Button launchNavButton;
	private Button workspacesNavButton;
	private Button recentNavButton;
	private Button settingsNavButton;

	private Combo pathCombo;
	private Button selectedNavButton;

	private Color darkBg;
	private Color sidebarBg;
	private Color accentColor;
	private Color textColor;
	private Color dimTextColor;
	private Color linkColor;
	private Font titleFont;
	private Font sectionFont;

	/**
	 * Creates the styled workspace selection dialog.
	 *
	 * @param parentShell the parent shell, or {@code null} for a top-level shell
	 * @param launchData  the workspace launch data containing recent workspaces
	 */
	public StyledChooseWorkspaceDialog(Shell parentShell, ChooseWorkspaceData launchData) {
		super(parentShell);
		this.launchData = launchData;
		setShellStyle(SWT.CLOSE | SWT.TITLE | SWT.APPLICATION_MODAL | SWT.RESIZE);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Eclipse Workspace Launcher"); //$NON-NLS-1$
	}

	@Override
	protected Point getInitialSize() {
		return new Point(780, 550);
	}

	@Override
	protected Point getInitialLocation(Point initialSize) {
		Shell shell = getShell();
		Monitor monitor = shell.getDisplay().getPrimaryMonitor();
		if (shell.getParent() != null) {
			monitor = shell.getParent().getMonitor();
		}
		Rectangle monitorBounds = monitor.getClientArea();
		int x = monitorBounds.x + (monitorBounds.width - initialSize.x) / 2;
		int y = monitorBounds.y + (monitorBounds.height - initialSize.y) / 3;
		return new Point(x, y);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		allocateColors(parent.getDisplay());
		allocateFonts(parent.getDisplay());

		Composite container = (Composite) super.createDialogArea(parent);
		container.setBackground(darkBg);

		GridLayout containerLayout = new GridLayout(2, false);
		containerLayout.marginWidth = 0;
		containerLayout.marginHeight = 0;
		containerLayout.horizontalSpacing = 0;
		container.setLayout(containerLayout);

		createSidebar(container);
		createContentArea(container);

		selectNavButton(launchNavButton, launchPage);

		return container;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		// No standard button bar — workspace launch is handled via links/buttons inside
		// the pages
		return new Composite(parent, SWT.NONE);
	}

	// ---- Sidebar ----

	private void createSidebar(Composite parent) {
		Composite sidebar = new Composite(parent, SWT.NONE);
		sidebar.setBackground(sidebarBg);
		GridDataFactory.fillDefaults().grab(false, true).hint(SIDEBAR_WIDTH, SWT.DEFAULT).applyTo(sidebar);
		GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 10).spacing(0, 2).applyTo(sidebar);

		launchNavButton = createNavButton(sidebar, "\u2694", "Launch"); //$NON-NLS-1$ //$NON-NLS-2$
		workspacesNavButton = createNavButton(sidebar, "\uD83D\uDCC2", "Workspaces"); //$NON-NLS-1$ //$NON-NLS-2$
		recentNavButton = createNavButton(sidebar, "\u23F0", "Recent"); //$NON-NLS-1$ //$NON-NLS-2$
		settingsNavButton = createNavButton(sidebar, "\u2699", "Settings"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Button createNavButton(Composite parent, String icon, String label) {
		Button btn = new Button(parent, SWT.FLAT | SWT.CENTER);
		btn.setText(icon + "\n" + label); //$NON-NLS-1$
		btn.setBackground(sidebarBg);
		btn.setForeground(dimTextColor);
		GridDataFactory.fillDefaults().grab(true, false).hint(SIDEBAR_WIDTH, 60).applyTo(btn);

		btn.addListener(SWT.Selection, e -> {
			Composite page = getPageForButton(btn);
			if (page != null) {
				selectNavButton(btn, page);
			}
		});
		return btn;
	}

	private Composite getPageForButton(Button btn) {
		if (btn == launchNavButton) return launchPage;
		if (btn == workspacesNavButton) return workspacesPage;
		if (btn == recentNavButton) return recentPage;
		if (btn == settingsNavButton) return settingsPage;
		return null;
	}

	private void selectNavButton(Button btn, Composite page) {
		if (selectedNavButton != null) {
			selectedNavButton.setBackground(sidebarBg);
			selectedNavButton.setForeground(dimTextColor);
		}
		selectedNavButton = btn;
		btn.setBackground(darkBg);
		btn.setForeground(accentColor);

		stackLayout.topControl = page;
		contentStack.layout();
	}

	// ---- Content Area ----

	private void createContentArea(Composite parent) {
		contentStack = new Composite(parent, SWT.NONE);
		contentStack.setBackground(darkBg);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(contentStack);

		stackLayout = new StackLayout();
		contentStack.setLayout(stackLayout);

		launchPage = createLaunchPage(contentStack);
		workspacesPage = createWorkspacesPage(contentStack);
		recentPage = createRecentPage(contentStack);
		settingsPage = createSettingsPage(contentStack);
	}

	// ---- Launch Page ----

	private Composite createLaunchPage(Composite parent) {
		Composite page = createPage(parent);

		Label title = new Label(page, SWT.NONE);
		title.setText("Launch"); //$NON-NLS-1$
		title.setFont(titleFont);
		title.setForeground(textColor);
		title.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(title);

		addSeparator(page, 2);

		// Two-column layout: Recent Workspaces | Recent Changes
		Composite leftCol = new Composite(page, SWT.NONE);
		leftCol.setBackground(darkBg);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(leftCol);
		GridLayoutFactory.fillDefaults().margins(0, 5).spacing(5, 4).applyTo(leftCol);

		Composite rightCol = new Composite(page, SWT.NONE);
		rightCol.setBackground(darkBg);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(rightCol);
		GridLayoutFactory.fillDefaults().margins(10, 5).spacing(5, 4).applyTo(rightCol);

		// -- Left: Recent Workspaces --
		Label recentWsLabel = new Label(leftCol, SWT.NONE);
		recentWsLabel.setText("Recent Workspaces"); //$NON-NLS-1$
		recentWsLabel.setFont(sectionFont);
		recentWsLabel.setForeground(textColor);
		recentWsLabel.setBackground(darkBg);

		List<String> recentWorkspaces = getRecentWorkspaces();
		if (recentWorkspaces.isEmpty()) {
			Label noWs = new Label(leftCol, SWT.NONE);
			noWs.setText("No recent workspaces"); //$NON-NLS-1$
			noWs.setForeground(dimTextColor);
			noWs.setBackground(darkBg);
		} else {
			for (String ws : recentWorkspaces) {
				Link link = new Link(leftCol, SWT.NONE);
				link.setText("<a>" + shortenPath(ws) + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
				link.setToolTipText(ws);
				link.setForeground(linkColor);
				link.setBackground(darkBg);
				link.addListener(SWT.Selection, e -> {
					launchData.workspaceSelected(TextProcessor.deprocess(ws));
					setReturnCode(OK);
					close();
				});
			}
		}

		// New workspace link
		Label spacer = new Label(leftCol, SWT.NONE);
		spacer.setBackground(darkBg);

		Label startLabel = new Label(leftCol, SWT.NONE);
		startLabel.setText("Start"); //$NON-NLS-1$
		startLabel.setFont(sectionFont);
		startLabel.setForeground(textColor);
		startLabel.setBackground(darkBg);

		Link newWsLink = new Link(leftCol, SWT.NONE);
		newWsLink.setText("<a>Create a new workspace</a>"); //$NON-NLS-1$
		newWsLink.setForeground(linkColor);
		newWsLink.setBackground(darkBg);
		newWsLink.addListener(SWT.Selection, e -> selectNavButton(workspacesNavButton, workspacesPage));

		Label newWsDesc = new Label(leftCol, SWT.WRAP);
		newWsDesc.setText("Browse for a folder to use as workspace."); //$NON-NLS-1$
		newWsDesc.setForeground(dimTextColor);
		newWsDesc.setBackground(darkBg);
		GridDataFactory.fillDefaults().hint(280, SWT.DEFAULT).applyTo(newWsDesc);

		// -- Right: Recent Changes (placeholder) --
		Label changesLabel = new Label(rightCol, SWT.NONE);
		changesLabel.setText("Recent Changes"); //$NON-NLS-1$
		changesLabel.setFont(sectionFont);
		changesLabel.setForeground(textColor);
		changesLabel.setBackground(darkBg);

		String[] placeholderFiles = {
				"IDEApplication.java", //$NON-NLS-1$
				"eclipse.product", //$NON-NLS-1$
				"E4Application.java", //$NON-NLS-1$
				"MANIFEST.MF", //$NON-NLS-1$
				"DarkThemeProcessor.java" //$NON-NLS-1$
		};
		for (String file : placeholderFiles) {
			Link fileLink = new Link(rightCol, SWT.NONE);
			fileLink.setText("<a>" + file + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
			fileLink.setForeground(linkColor);
			fileLink.setBackground(darkBg);

			Label projectLabel = new Label(rightCol, SWT.NONE);
			projectLabel.setText("  platform"); //$NON-NLS-1$
			projectLabel.setForeground(dimTextColor);
			projectLabel.setBackground(darkBg);
		}

		return page;
	}

	// ---- Workspaces Page (functional) ----

	private Composite createWorkspacesPage(Composite parent) {
		Composite page = createPage(parent);

		Label title = new Label(page, SWT.NONE);
		title.setText("Workspaces"); //$NON-NLS-1$
		title.setFont(titleFont);
		title.setForeground(textColor);
		title.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(title);

		addSeparator(page, 2);

		Label desc = new Label(page, SWT.WRAP);
		desc.setText("Select or type a workspace directory to launch:"); //$NON-NLS-1$
		desc.setForeground(textColor);
		desc.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).hint(500, SWT.DEFAULT).applyTo(desc);

		// Combo + Browse row
		Composite browseRow = new Composite(page, SWT.NONE);
		browseRow.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(browseRow);
		GridLayoutFactory.fillDefaults().numColumns(2).applyTo(browseRow);

		pathCombo = new Combo(browseRow, SWT.BORDER | SWT.DROP_DOWN);
		GridDataFactory.fillDefaults().grab(true, false).hint(400, SWT.DEFAULT).applyTo(pathCombo);

		// Populate combo with recent workspaces
		for (String ws : getRecentWorkspaces()) {
			pathCombo.add(ws);
		}
		String initial = pathCombo.getItemCount() > 0 ? pathCombo.getItem(0) : launchData.getInitialDefault();
		pathCombo.setText(TextProcessor.process(initial));

		Button browseButton = new Button(browseRow, SWT.PUSH);
		browseButton.setText("Browse..."); //$NON-NLS-1$
		browseButton.addListener(SWT.Selection, e -> {
			DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.SHEET);
			dialog.setText("Select Workspace Directory"); //$NON-NLS-1$
			dialog.setMessage("Select a directory as the workspace."); //$NON-NLS-1$
			String filterPath = pathCombo.getText();
			if (!filterPath.isBlank()) {
				File f = new File(filterPath);
				while (f != null && !f.exists()) {
					f = f.getParentFile();
				}
				if (f != null) {
					dialog.setFilterPath(f.getAbsolutePath());
				}
			}
			String dir = dialog.open();
			if (dir != null) {
				pathCombo.setText(TextProcessor.process(dir));
			}
		});

		// Launch button
		Label spacer = new Label(page, SWT.NONE);
		spacer.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(spacer);

		Button launchButton = new Button(page, SWT.PUSH);
		launchButton.setText("Launch"); //$NON-NLS-1$
		launchButton.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT));
		GridDataFactory.fillDefaults().hint(120, 35).applyTo(launchButton);
		launchButton.addListener(SWT.Selection, e -> {
			String workspace = pathCombo.getText();
			if (workspace != null && !workspace.isBlank()) {
				launchData.workspaceSelected(TextProcessor.deprocess(workspace));
				setReturnCode(OK);
				close();
			}
		});

		// Show dialog checkbox
		Label spacer2 = new Label(page, SWT.NONE);
		spacer2.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).grab(false, true).applyTo(spacer2);

		Button showDialogCheck = new Button(page, SWT.CHECK);
		showDialogCheck.setText("Use this as the default and do not ask again"); //$NON-NLS-1$
		showDialogCheck.setForeground(textColor);
		showDialogCheck.setBackground(darkBg);
		showDialogCheck.setSelection(!launchData.getShowDialog());
		showDialogCheck.addListener(SWT.Selection, e -> launchData.toggleShowDialog());
		GridDataFactory.fillDefaults().span(2, 1).applyTo(showDialogCheck);

		return page;
	}

	// ---- Recent Page (placeholder) ----

	private Composite createRecentPage(Composite parent) {
		Composite page = createPage(parent);

		Label title = new Label(page, SWT.NONE);
		title.setText("Recent"); //$NON-NLS-1$
		title.setFont(titleFont);
		title.setForeground(textColor);
		title.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(title);

		addSeparator(page, 2);

		Label desc = new Label(page, SWT.WRAP);
		desc.setText("Recent activity across all workspaces will be shown here."); //$NON-NLS-1$
		desc.setForeground(dimTextColor);
		desc.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).hint(400, SWT.DEFAULT).applyTo(desc);

		// Placeholder items
		String[] recentItems = {
				"Edited ChooseWorkspaceDialog.java — 2 hours ago", //$NON-NLS-1$
				"Built org.eclipse.ui.ide — 5 hours ago", //$NON-NLS-1$
				"Ran JUnit tests — yesterday", //$NON-NLS-1$
				"Updated MANIFEST.MF — 2 days ago" //$NON-NLS-1$
		};
		for (String item : recentItems) {
			Label itemLabel = new Label(page, SWT.NONE);
			itemLabel.setText("  \u2022  " + item); //$NON-NLS-1$
			itemLabel.setForeground(textColor);
			itemLabel.setBackground(darkBg);
			GridDataFactory.fillDefaults().span(2, 1).applyTo(itemLabel);
		}

		return page;
	}

	// ---- Settings Page (placeholder) ----

	private Composite createSettingsPage(Composite parent) {
		Composite page = createPage(parent);

		Label title = new Label(page, SWT.NONE);
		title.setText("Settings"); //$NON-NLS-1$
		title.setFont(titleFont);
		title.setForeground(textColor);
		title.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(title);

		addSeparator(page, 2);

		Label desc = new Label(page, SWT.WRAP);
		desc.setText("Configure workspace launcher preferences."); //$NON-NLS-1$
		desc.setForeground(dimTextColor);
		desc.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).hint(400, SWT.DEFAULT).applyTo(desc);

		// Placeholder setting: max recent workspaces
		Label maxLabel = new Label(page, SWT.NONE);
		maxLabel.setText("Max recent workspaces:"); //$NON-NLS-1$
		maxLabel.setForeground(textColor);
		maxLabel.setBackground(darkBg);

		Label maxValue = new Label(page, SWT.NONE);
		maxValue.setText("10"); //$NON-NLS-1$
		maxValue.setForeground(dimTextColor);
		maxValue.setBackground(darkBg);

		// Placeholder setting: theme
		Label themeLabel = new Label(page, SWT.NONE);
		themeLabel.setText("Theme:"); //$NON-NLS-1$
		themeLabel.setForeground(textColor);
		themeLabel.setBackground(darkBg);

		Label themeValue = new Label(page, SWT.NONE);
		themeValue.setText("Dark (System default)"); //$NON-NLS-1$
		themeValue.setForeground(dimTextColor);
		themeValue.setBackground(darkBg);

		// Link to switch to classic dialog
		Label spacer = new Label(page, SWT.NONE);
		spacer.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).grab(false, true).applyTo(spacer);

		Link classicLink = new Link(page, SWT.NONE);
		classicLink.setText("Don't like this startup UI? <a>Turn it off</a> and use the classic workspace prompt."); //$NON-NLS-1$
		classicLink.setForeground(dimTextColor);
		classicLink.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(classicLink);

		return page;
	}

	// ---- Helpers ----

	private Composite createPage(Composite parent) {
		Composite page = new Composite(parent, SWT.NONE);
		page.setBackground(darkBg);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 20;
		layout.marginHeight = 15;
		layout.horizontalSpacing = 10;
		layout.verticalSpacing = 6;
		page.setLayout(layout);
		return page;
	}

	private void addSeparator(Composite parent, int span) {
		Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		sep.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(span, 1).grab(true, false).applyTo(sep);
	}

	private void allocateColors(Display display) {
		darkBg = new Color(display, 43, 43, 43);
		sidebarBg = new Color(display, 33, 33, 33);
		accentColor = new Color(display, 75, 175, 230);
		textColor = new Color(display, 210, 210, 210);
		dimTextColor = new Color(display, 140, 140, 140);
		linkColor = new Color(display, 85, 180, 230);
	}

	private void allocateFonts(Display display) {
		FontData[] defaultFontData = JFaceResources.getDefaultFont().getFontData();
		FontData titleFD = new FontData(defaultFontData[0].getName(), defaultFontData[0].getHeight() + 10, SWT.BOLD);
		titleFont = new Font(display, titleFD);

		FontData sectionFD = new FontData(defaultFontData[0].getName(), defaultFontData[0].getHeight() + 2, SWT.BOLD);
		sectionFont = new Font(display, sectionFD);
	}

	private List<String> getRecentWorkspaces() {
		String[] workspaces = launchData.getRecentWorkspaces();
		List<String> result = new ArrayList<>();
		if (workspaces != null) {
			for (String ws : workspaces) {
				if (ws != null && !ws.isEmpty()) {
					result.add(ws);
				}
			}
		}
		return result;
	}

	private static String shortenPath(String path) {
		if (path == null) return ""; //$NON-NLS-1$
		String home = System.getProperty("user.home"); //$NON-NLS-1$
		if (home != null && path.startsWith(home)) {
			return "~" + path.substring(home.length()); //$NON-NLS-1$
		}
		return path;
	}

	/**
	 * Show the dialog and return the workspace selection (delegates to
	 * {@link ChooseWorkspaceData}).
	 *
	 * @param force true to force showing the dialog even if "don't ask again" is
	 *              set
	 */
	public void prompt(boolean force) {
		if (force || launchData.getShowDialog()) {
			open();
			if (getReturnCode() == CANCEL) {
				launchData.workspaceSelected(null);
			}
			return;
		}

		String[] recent = launchData.getRecentWorkspaces();
		String workspace = null;
		if (recent != null && recent.length > 0) {
			workspace = recent[0];
		}
		if (workspace == null || workspace.isEmpty()) {
			workspace = launchData.getInitialDefault();
		}
		launchData.workspaceSelected(TextProcessor.deprocess(workspace));
	}

	@Override
	public boolean close() {
		if (titleFont != null && !titleFont.isDisposed()) {
			titleFont.dispose();
		}
		if (sectionFont != null && !sectionFont.isDisposed()) {
			sectionFont.dispose();
		}
		if (darkBg != null && !darkBg.isDisposed()) {
			darkBg.dispose();
		}
		if (sidebarBg != null && !sidebarBg.isDisposed()) {
			sidebarBg.dispose();
		}
		if (accentColor != null && !accentColor.isDisposed()) {
			accentColor.dispose();
		}
		if (textColor != null && !textColor.isDisposed()) {
			textColor.dispose();
		}
		if (dimTextColor != null && !dimTextColor.isDisposed()) {
			dimTextColor.dispose();
		}
		if (linkColor != null && !linkColor.isDisposed()) {
			linkColor.dispose();
		}
		return super.close();
	}
}
