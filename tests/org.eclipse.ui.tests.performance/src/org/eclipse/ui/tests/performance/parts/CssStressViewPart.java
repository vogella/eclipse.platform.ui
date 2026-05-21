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
package org.eclipse.ui.tests.performance.parts;

import org.eclipse.e4.ui.css.swt.dom.WidgetElement;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.part.ViewPart;

/**
 * View used by CSS theme-swap performance tests. Builds a large, varied widget
 * tree so the CSS engine has meaningful work to do when a theme is applied.
 */
public class CssStressViewPart extends ViewPart {

	public static final String ID = "org.eclipse.ui.tests.performance.cssStressView";

	private static final int WIDGET_COUNT = 4000;
	private static final int GROUP_SIZE = 40;

	private Composite root;

	@Override
	public void createPartControl(Composite parent) {
		ScrolledComposite scroll = new ScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL);
		root = new Composite(scroll, SWT.NONE);
		root.setLayout(new GridLayout(8, true));
		buildTree(root, WIDGET_COUNT);
		scroll.setContent(root);
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);
		scroll.setMinSize(root.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	private void buildTree(Composite parent, int target) {
		int remaining = target;
		int groupIndex = 0;
		while (remaining > 0) {
			Group group = new Group(parent, SWT.NONE);
			group.setText("Group " + groupIndex);
			group.setLayout(new GridLayout(4, false));
			int n = Math.min(GROUP_SIZE, remaining);
			for (int i = 0; i < n; i++) {
				addWidget(group, (groupIndex * GROUP_SIZE + i) % 14);
			}
			remaining -= n;
			groupIndex++;
		}
	}

	private void addWidget(Composite parent, int kind) {
		switch (kind) {
		case 0:
			Label l = new Label(parent, SWT.NONE);
			l.setText("label-" + kind);
			if (kind % 7 == 0) {
				WidgetElement.setCSSClass(l, "hot");
			}
			break;
		case 1:
			Button push = new Button(parent, SWT.PUSH);
			push.setText("push");
			break;
		case 2:
			Button check = new Button(parent, SWT.CHECK);
			check.setText("check");
			break;
		case 3:
			Button radio = new Button(parent, SWT.RADIO);
			radio.setText("radio");
			break;
		case 4:
			Text text = new Text(parent, SWT.BORDER);
			text.setText("text");
			break;
		case 5:
			Combo combo = new Combo(parent, SWT.READ_ONLY);
			combo.setItems("a", "b", "c");
			combo.select(0);
			break;
		case 6:
			Spinner spinner = new Spinner(parent, SWT.BORDER);
			spinner.setSelection(5);
			break;
		case 7:
			List list = new List(parent, SWT.BORDER | SWT.SINGLE);
			list.setItems("item1", "item2", "item3");
			GridData ld = new GridData();
			ld.heightHint = 40;
			list.setLayoutData(ld);
			break;
		case 8:
			Link link = new Link(parent, SWT.NONE);
			link.setText("<a>linked</a>");
			break;
		case 9:
			ProgressBar pb = new ProgressBar(parent, SWT.HORIZONTAL);
			pb.setSelection(40);
			break;
		case 10:
			CLabel clabel = new CLabel(parent, SWT.NONE);
			clabel.setText("clabel");
			break;
		case 11:
			CCombo ccombo = new CCombo(parent, SWT.BORDER);
			ccombo.setItems(new String[] { "x", "y", "z" });
			ccombo.select(0);
			break;
		case 12:
			Scale scale = new Scale(parent, SWT.HORIZONTAL);
			scale.setSelection(30);
			break;
		case 13:
			DateTime dt = new DateTime(parent, SWT.DATE | SWT.SHORT);
			// no extra setup
			if (dt == null) {
				throw new IllegalStateException();
			}
			break;
		default:
			break;
		}

		if (kind == 4) {
			// once per group cycle, add a small Tree / Table / ExpandBar / ToolBar so
			// the engine sees container widgets too
			addContainerWidgets(parent);
		}
	}

	private void addContainerWidgets(Composite parent) {
		ToolBar tb = new ToolBar(parent, SWT.FLAT);
		new ToolItem(tb, SWT.PUSH).setText("a");
		new ToolItem(tb, SWT.SEPARATOR);
		new ToolItem(tb, SWT.PUSH).setText("b");

		Tree tree = new Tree(parent, SWT.BORDER);
		GridData td = new GridData();
		td.heightHint = 50;
		tree.setLayoutData(td);
		TreeItem root = new TreeItem(tree, SWT.NONE);
		root.setText("root");
		new TreeItem(root, SWT.NONE).setText("child-1");
		new TreeItem(root, SWT.NONE).setText("child-2");

		Table table = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible(true);
		TableColumn c1 = new TableColumn(table, SWT.LEFT);
		c1.setText("name");
		c1.setWidth(60);
		TableColumn c2 = new TableColumn(table, SWT.LEFT);
		c2.setText("value");
		c2.setWidth(60);
		TableItem ti = new TableItem(table, SWT.NONE);
		ti.setText(new String[] { "k1", "v1" });
		GridData tabd = new GridData();
		tabd.heightHint = 50;
		table.setLayoutData(tabd);

		ExpandBar bar = new ExpandBar(parent, SWT.NONE);
		ExpandItem ei = new ExpandItem(bar, SWT.NONE);
		ei.setText("section");
		Composite inner = new Composite(bar, SWT.NONE);
		inner.setLayout(new GridLayout(1, false));
		new Label(inner, SWT.NONE).setText("inner");
		ei.setControl(inner);
		ei.setHeight(40);
	}

	@Override
	public void setFocus() {
		if (root != null && !root.isDisposed()) {
			root.setFocus();
		}
	}
}
