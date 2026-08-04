/*******************************************************************************
 * Copyright (c) 2026 Eclipse contributors and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.ui.internal;

import org.eclipse.jface.resource.IImageURLModifier;
import org.eclipse.jface.resource.ImageDescriptor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Installs the highest ranked {@link IImageURLModifier} service in JFace, which
 * lets an icon pack substitute images without the workbench knowing the pack.
 * Tracked instead of read once because the contributing bundle may start after
 * the workbench bundle.
 */
class ImageURLModifierTracker extends ServiceTracker<IImageURLModifier, IImageURLModifier> {

	private ServiceReference<IImageURLModifier> installed;

	ImageURLModifierTracker(BundleContext context) {
		super(context, IImageURLModifier.class, null);
	}

	@Override
	public IImageURLModifier addingService(ServiceReference<IImageURLModifier> reference) {
		IImageURLModifier modifier = super.addingService(reference);
		if (modifier != null && (installed == null || reference.compareTo(installed) > 0)) {
			installed = reference;
			ImageDescriptor.setURLModifier(modifier);
		}
		return modifier;
	}

	@Override
	public void removedService(ServiceReference<IImageURLModifier> reference, IImageURLModifier service) {
		super.removedService(reference, service);
		if (reference.equals(installed)) {
			installed = getServiceReference();
			ImageDescriptor.setURLModifier(installed == null ? null : getService(installed));
		}
	}
}
