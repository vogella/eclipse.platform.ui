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
import java.io.FileReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
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
import org.eclipse.ui.IMemento;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.osgi.framework.FrameworkUtil;

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

	private Composite launchNavItem;
	private Composite workspacesNavItem;
	private Composite recentNavItem;
	private Composite settingsNavItem;

	private Combo pathCombo;
	private Composite selectedNavItem;

	private Color darkBg;
	private Color sidebarBg;
	private Color accentColor;
	private Color textColor;
	private Color dimTextColor;
	private Color linkColor;
	private Color selectedBg;
	private Font titleFont;
	private Font sectionFont;
	private final List<Image> navImages = new ArrayList<>();

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

		selectNavItem(launchNavItem, launchPage);

		return container;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Composite empty = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().hint(0, 0).exclude(true).applyTo(empty);
		return empty;
	}

	// ---- Sidebar ----

	private void createSidebar(Composite parent) {
		Composite sidebar = new Composite(parent, SWT.NONE);
		sidebar.setBackground(sidebarBg);
		GridDataFactory.fillDefaults().grab(false, true).hint(SIDEBAR_WIDTH, SWT.DEFAULT).applyTo(sidebar);
		GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 10).spacing(0, 0).applyTo(sidebar);

		launchNavItem = createNavItem(sidebar, "icons/full/obj16/styled_launch.svg", "Launch"); //$NON-NLS-1$ //$NON-NLS-2$
		workspacesNavItem = createNavItem(sidebar, "icons/full/obj16/styled_workspaces.svg", "Workspaces"); //$NON-NLS-1$ //$NON-NLS-2$
		recentNavItem = createNavItem(sidebar, "icons/full/obj16/styled_recent.svg", "Recent"); //$NON-NLS-1$ //$NON-NLS-2$
		settingsNavItem = createNavItem(sidebar, "icons/full/obj16/styled_settings.svg", "Settings"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Composite createNavItem(Composite parent, String iconPath, String label) {
		Composite item = new Composite(parent, SWT.NONE);
		item.setBackground(sidebarBg);
		item.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
		GridDataFactory.fillDefaults().grab(true, false).hint(SIDEBAR_WIDTH, 58).applyTo(item);
		GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 6).spacing(0, 2).applyTo(item);

		Label iconLabel = new Label(item, SWT.CENTER);
		iconLabel.setBackground(sidebarBg);
		Image image = loadBundleImage(iconPath);
		if (image != null) {
			iconLabel.setImage(image);
		}
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).grab(true, false).applyTo(iconLabel);

		Label textLabel = new Label(item, SWT.CENTER);
		textLabel.setText(label);
		textLabel.setBackground(sidebarBg);
		textLabel.setForeground(dimTextColor);
		GridDataFactory.fillDefaults().align(SWT.CENTER, SWT.CENTER).grab(true, false).applyTo(textLabel);

		// Click handler on all children
		org.eclipse.swt.widgets.Listener clickHandler = e -> {
			Composite page = getPageForItem(item);
			if (page != null) {
				selectNavItem(item, page);
			}
		};
		item.addListener(SWT.MouseDown, clickHandler);
		iconLabel.addListener(SWT.MouseDown, clickHandler);
		textLabel.addListener(SWT.MouseDown, clickHandler);

		return item;
	}

	private Composite getPageForItem(Composite item) {
		if (item == launchNavItem) return launchPage;
		if (item == workspacesNavItem) return workspacesPage;
		if (item == recentNavItem) return recentPage;
		if (item == settingsNavItem) return settingsPage;
		return null;
	}

	private void selectNavItem(Composite item, Composite page) {
		if (selectedNavItem != null) {
			setNavItemBackground(selectedNavItem, sidebarBg);
			setNavItemForeground(selectedNavItem, dimTextColor);
		}
		selectedNavItem = item;
		setNavItemBackground(item, selectedBg);
		setNavItemForeground(item, accentColor);

		stackLayout.topControl = page;
		contentStack.layout();
	}

	private static void setNavItemBackground(Composite item, Color color) {
		item.setBackground(color);
		for (Control child : item.getChildren()) {
			child.setBackground(color);
		}
	}

	private static void setNavItemForeground(Composite item, Color color) {
		for (Control child : item.getChildren()) {
			if (child instanceof Label label) {
				label.setForeground(color);
			}
		}
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
		newWsLink.addListener(SWT.Selection, e -> selectNavItem(workspacesNavItem, workspacesPage));

		Label newWsDesc = new Label(leftCol, SWT.WRAP);
		newWsDesc.setText("Browse for a folder to use as workspace."); //$NON-NLS-1$
		newWsDesc.setForeground(dimTextColor);
		newWsDesc.setBackground(darkBg);
		GridDataFactory.fillDefaults().hint(280, SWT.DEFAULT).applyTo(newWsDesc);

		// -- Right: Recent Changes (loaded asynchronously from all workspaces) --
		Label changesLabel = new Label(rightCol, SWT.NONE);
		changesLabel.setText("Recent Changes"); //$NON-NLS-1$
		changesLabel.setFont(sectionFont);
		changesLabel.setForeground(textColor);
		changesLabel.setBackground(darkBg);

		Label loadingLabel = new Label(rightCol, SWT.NONE);
		loadingLabel.setText("Loading\u2026"); //$NON-NLS-1$
		loadingLabel.setForeground(dimTextColor);
		loadingLabel.setBackground(darkBg);

		// Read MRU lists from all workspaces asynchronously
		List<String> workspacePaths = new ArrayList<>(recentWorkspaces);
		Display display = parent.getDisplay();
		CompletableFuture.supplyAsync(() -> readRecentFilesFromWorkspaces(workspacePaths, 10))
				.thenAccept(recentFiles -> {
					if (display.isDisposed()) {
						return;
					}
					display.asyncExec(() -> {
						if (rightCol.isDisposed()) {
							return;
						}
						loadingLabel.dispose();
						if (recentFiles.isEmpty()) {
							Label noFiles = new Label(rightCol, SWT.NONE);
							noFiles.setText("No recent files found"); //$NON-NLS-1$
							noFiles.setForeground(dimTextColor);
							noFiles.setBackground(darkBg);
						} else {
							for (RecentFileEntry entry : recentFiles) {
								Link fileLink = new Link(rightCol, SWT.NONE);
								fileLink.setText("<a>" + entry.fileName + "</a>"); //$NON-NLS-1$ //$NON-NLS-2$
								fileLink.setToolTipText(entry.filePath);
								fileLink.setForeground(linkColor);
								fileLink.setBackground(darkBg);

								Label projectLabel = new Label(rightCol, SWT.NONE);
								projectLabel.setText("  " + entry.projectName); //$NON-NLS-1$
								projectLabel.setForeground(dimTextColor);
								projectLabel.setBackground(darkBg);
							}
						}
						rightCol.layout();
					});
				});

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

		// Existing workspaces list
		List<String> recentWorkspaces = getRecentWorkspaces();
		if (!recentWorkspaces.isEmpty()) {
			Label existingLabel = new Label(page, SWT.NONE);
			existingLabel.setText("Select an existing workspace:"); //$NON-NLS-1$
			existingLabel.setFont(sectionFont);
			existingLabel.setForeground(textColor);
			existingLabel.setBackground(darkBg);
			GridDataFactory.fillDefaults().span(2, 1).applyTo(existingLabel);

			org.eclipse.swt.widgets.List workspaceList = new org.eclipse.swt.widgets.List(page,
					SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
			GridDataFactory.fillDefaults().span(2, 1).grab(true, true).hint(SWT.DEFAULT, 150).applyTo(workspaceList);
			for (String ws : recentWorkspaces) {
				workspaceList.add(ws);
			}
			workspaceList.select(0);

			// Sync list selection with combo
			workspaceList.addListener(SWT.Selection, e -> {
				int idx = workspaceList.getSelectionIndex();
				if (idx >= 0) {
					pathCombo.setText(workspaceList.getItem(idx));
				}
			});

			// Double-click to launch directly
			workspaceList.addListener(SWT.MouseDoubleClick, e -> {
				int idx = workspaceList.getSelectionIndex();
				if (idx >= 0) {
					String workspace = workspaceList.getItem(idx);
					launchData.workspaceSelected(TextProcessor.deprocess(workspace));
					setReturnCode(OK);
					close();
				}
			});

			addSeparator(page, 2);
		}

		// New workspace section
		Label newLabel = new Label(page, SWT.NONE);
		newLabel.setText("Or enter a new workspace path:"); //$NON-NLS-1$
		newLabel.setForeground(textColor);
		newLabel.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).applyTo(newLabel);

		// Combo + Browse row
		Composite browseRow = new Composite(page, SWT.NONE);
		browseRow.setBackground(darkBg);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false).applyTo(browseRow);
		GridLayoutFactory.fillDefaults().numColumns(2).applyTo(browseRow);

		pathCombo = new Combo(browseRow, SWT.BORDER | SWT.DROP_DOWN);
		GridDataFactory.fillDefaults().grab(true, false).hint(400, SWT.DEFAULT).applyTo(pathCombo);

		// Populate combo with recent workspaces
		for (String ws : recentWorkspaces) {
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
		selectedBg = new Color(display, 55, 55, 55);
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

	private Image loadBundleImage(String path) {
		try {
			URL iconUrl = FrameworkUtil.getBundle(getClass()).getEntry(path);
			if (iconUrl != null) {
				ImageDescriptor descriptor = ImageDescriptor.createFromURL(iconUrl);
				Image image = descriptor.createImage(false);
				if (image != null) {
					navImages.add(image);
				}
				return image;
			}
		} catch (Exception e) {
			// Fall back to no image
		}
		return null;
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
	 * A recent file entry with display information.
	 */
	private record RecentFileEntry(String fileName, String filePath, String projectName) {
	}

	/**
	 * Reads the MRU (most recently used) file lists from multiple workspaces'
	 * workbench.xml metadata files. Called on a background thread.
	 *
	 * @param workspacePaths the root paths of the workspaces to scan
	 * @param maxEntries     maximum total number of entries to return
	 * @return list of recent file entries, deduplicated, preserving MRU order
	 */
	private static List<RecentFileEntry> readRecentFilesFromWorkspaces(List<String> workspacePaths, int maxEntries) {
		List<RecentFileEntry> result = new ArrayList<>();
		// Track seen file names to avoid duplicates across workspaces
		Map<String, Boolean> seen = new LinkedHashMap<>();
		for (String workspacePath : workspacePaths) {
			if (workspacePath == null || workspacePath.isBlank()) {
				continue;
			}
			File workbenchXml = new File(workspacePath,
					".metadata/.plugins/org.eclipse.ui.workbench/workbench.xml"); //$NON-NLS-1$
			if (!workbenchXml.exists()) {
				continue;
			}
			try (Reader reader = new FileReader(workbenchXml)) {
				XMLMemento memento = XMLMemento.createReadRoot(reader);
				IMemento mruMemento = memento.getChild("mruList"); //$NON-NLS-1$
				if (mruMemento == null) {
					continue;
				}
				for (IMemento child : mruMemento.getChildren("file")) { //$NON-NLS-1$
					String name = child.getString("name"); //$NON-NLS-1$
					String tooltip = child.getString("tooltip"); //$NON-NLS-1$
					if (name != null && !name.isEmpty() && !seen.containsKey(name)) {
						seen.put(name, Boolean.TRUE);
						String filePath = tooltip != null ? tooltip : name;
						String projectName = extractProjectName(filePath);
						result.add(new RecentFileEntry(name, filePath, projectName));
						if (result.size() >= maxEntries) {
							return result;
						}
					}
				}
			} catch (Exception e) {
				// Workspace metadata unreadable — skip to next
			}
		}
		return result;
	}

	/**
	 * Extracts a meaningful project or parent folder name from a file path.
	 */
	private static String extractProjectName(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return ""; //$NON-NLS-1$
		}
		// Tooltip paths often start with "L/" or "/" followed by project/path
		String path = filePath;
		if (path.startsWith("L/") || path.startsWith("L\\")) { //$NON-NLS-1$ //$NON-NLS-2$
			path = path.substring(2);
		} else if (path.startsWith("/")) { //$NON-NLS-1$
			path = path.substring(1);
		}
		int sep = path.indexOf('/');
		if (sep < 0) {
			sep = path.indexOf('\\');
		}
		if (sep > 0) {
			return path.substring(0, sep);
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
		for (Image img : navImages) {
			if (img != null && !img.isDisposed()) {
				img.dispose();
			}
		}
		navImages.clear();
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
		if (selectedBg != null && !selectedBg.isDisposed()) {
			selectedBg.dispose();
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
