/*******************************************************************************
 * Copyright (c) 2008, 2010 Matthew Hall and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthew Hall - initial API and implementation (bug 194734)
 *     Matthew Hall - bug 251611
 ******************************************************************************/

package org.eclipse.jface.internal.databinding.swt;

import org.eclipse.core.databinding.observable.list.ListDiff;
import org.eclipse.core.databinding.observable.list.ListDiffVisitor;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;

/**
 * @since 3.3
 * 
 */
public class ListItemsProperty extends ControlStringListProperty {
	protected void doUpdateStringList(final Control control, ListDiff diff) {
		diff.accept(new ListDiffVisitor() {
			List list = (List) control;

			public void handleAdd(int index, Object element) {
				list.add((String) element, index);
			}

			public void handleRemove(int index, Object element) {
				list.remove(index);
			}

			// public void handleMove(int oldIndex, int newIndex, Object
			// element) {
			// int selectionIndex = combo.getSelectionIndex();
			// Listener[] modifyListeners = combo.getListeners(SWT.Modify);
			// if (selectionIndex == oldIndex) {
			// for (int i = 0; i < modifyListeners.length; i++)
			// combo.removeListener(SWT.Modify, modifyListeners[i]);
			// }
			//
			// super.handleMove(oldIndex, newIndex, element);
			//
			// if (selectionIndex == oldIndex) {
			// combo.select(newIndex);
			// for (int i = 0; i < modifyListeners.length; i++)
			// combo.addListener(SWT.Modify, modifyListeners[i]);
			// }
			// }

			public void handleReplace(int index, Object oldElement,
					Object newElement) {
				list.setItem(index, (String) newElement);
			}
		});
	}

	public String[] doGetStringList(Control control) {
		return ((List) control).getItems();
	}

	public String toString() {
		return "List.items[] <String>"; //$NON-NLS-1$
	}
}
