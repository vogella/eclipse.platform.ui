/*******************************************************************************
 * Copyright (c) 2026 Felix Schmid
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Felix Schmid - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.ltk.core.refactoring.resource;

import java.net.URI;
import java.text.MessageFormat;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ReorgExecutionLog;
import org.eclipse.ltk.internal.core.refactoring.BasicElementLabels;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCoreMessages;

/**
 * {@link Change} that copies a resource.
 *
 * @since 3.16
 */
public class CopyResourceChange extends ResourceChange {

	private ChangeDescriptor descriptor;

	private final IResource origin;

	private final ReorgExecutionLog log;

	private final IContainer destination;

	public CopyResourceChange(final IResource origin, final ReorgExecutionLog log, final IContainer destination) {
		Assert.isTrue(origin instanceof IFile || origin instanceof IFolder);
		this.origin= origin;
		this.log= log;
		this.destination= destination;
		setValidationMethod(VALIDATE_NOT_DIRTY);
	}

	@Override
	public String getName() {
		return MessageFormat.format(RefactoringCoreMessages.CopyResourceChange_name,
				BasicElementLabels.getPathLabel(origin.getFullPath(), false),
				BasicElementLabels.getResourceName(destination));
	}

	@Override
	public final Change perform(final IProgressMonitor pm) throws CoreException, OperationCanceledException {
		pm.beginTask(getName(), 2);
		try {
			String newName= log.getNewName(origin);
			if (newName == null) {
				newName= origin.getName();
			}

			final IResource resAtDest= destination.findMember(newName);
			if (resAtDest != null && resAtDest.exists() && areEqualInWorkspaceOrOnDisk(origin, resAtDest)) {
				return new NullChange();
			}

			final Change undoOverwrite= deleteIfAlreadyExists(resAtDest, SubMonitor.convert(pm, 1));

			final IPath copyTo= destination.getFullPath().append(newName);
			origin.copy(copyTo, getReorgFlags(), SubMonitor.convert(pm, 1));
			log.markAsProcessed(origin);

			if (undoOverwrite != null) {
				return new CompositeChange(RefactoringCoreMessages.CopyResourceChange_undo_composite_name,
						new Change[] { new DeleteResourceChange(copyTo, false), undoOverwrite });
			}
			return new DeleteResourceChange(copyTo, false);
		} finally {
			pm.done();
		}
	}

	@Override
	protected IResource getModifiedResource() {
		return origin;
	}

	/**
	 * deletes a resource if it exists and returns a <code>Change</code> to undo the deletion
	 *
	 * @param resource the resource to delete
	 * @param pm the progress monitor
	 * @return returns an undo <code>Change</code> or <code>null</code> if nothing was deleted
	 * @throws CoreException thrown when the resource cannot be accessed
	 */
	private Change deleteIfAlreadyExists(final IResource resource, final IProgressMonitor pm) throws CoreException {
		if (resource == null || !resource.exists()) {
			pm.done();
			return null;
		}
		SubMonitor subMonitor= SubMonitor.convert(pm,
				RefactoringCoreMessages.MoveResourceChange_progress_delete_destination, 3);
		DeleteResourceChange deleteChange= new DeleteResourceChange(resource.getFullPath(), true);
		deleteChange.initializeValidationData(subMonitor.newChild(1));
		RefactoringStatus deleteStatus= deleteChange.isValid(subMonitor.newChild(1));
		if (!deleteStatus.hasFatalError()) {
			return deleteChange.perform(subMonitor.newChild(1));
		}
		return null;
	}

	private static boolean areEqualInWorkspaceOrOnDisk(final IResource r1, final IResource r2) {
		if (r1 == null || r2 == null) {
			return false;
		}
		if (r1.equals(r2)) {
			return true;
		}
		final URI r1Location= r1.getLocationURI();
		final URI r2Location= r2.getLocationURI();
		if (r1Location == null || r2Location == null) {
			return false;
		}
		return r1Location.equals(r2Location);
	}

	private static int getReorgFlags() {
		return IResource.KEEP_HISTORY | IResource.SHALLOW;
	}

	@Override
	public ChangeDescriptor getDescriptor() {
		return descriptor;
	}

	public void setDescriptor(ChangeDescriptor descriptor) {
		this.descriptor= descriptor;
	}
}
