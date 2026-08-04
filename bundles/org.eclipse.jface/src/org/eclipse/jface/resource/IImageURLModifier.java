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
package org.eclipse.jface.resource;

import java.net.URL;

/**
 * Rewrites image URLs before they are loaded. This allows an application or an
 * icon pack to substitute icons without changes to the code that creates the
 * image descriptors.
 * <p>
 * Inside an Eclipse workbench a modifier can also be contributed as an OSGi
 * service of this type. The workbench installs the highest ranked one while it
 * starts, so an icon pack does not need any startup code of its own.
 * </p>
 *
 * @see ImageDescriptor#setURLModifier(IImageURLModifier)
 * @since 3.40
 */
@FunctionalInterface
public interface IImageURLModifier {

	/**
	 * Returns the URL to load instead of the given one. Returning the given URL or
	 * <code>null</code> leaves the original URL in place.
	 * <p>
	 * Called for every image URL resolution, so implementations should be fast and
	 * thread-safe.
	 * </p>
	 */
	URL modifyURL(URL originalURL);
}
