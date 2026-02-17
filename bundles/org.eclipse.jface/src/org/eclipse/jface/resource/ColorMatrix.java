/*******************************************************************************
 * Copyright (c) 2021, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.resource;

import java.util.Arrays;

/**
 * A color matrix is a 5x4 matrix that can be used to transform colors of an
 * image.
 *
 * @since 3.24
 */
public final class ColorMatrix {
	private final float[] matrix;

	/**
	 * Creates a new color matrix from the given 20 elements.
	 *
	 * @param matrix the 20 elements of the matrix
	 * @throws IllegalArgumentException if the matrix does not have 20 elements
	 */
	public ColorMatrix(float[] matrix) {
		if (matrix.length != 20) {
			throw new IllegalArgumentException("Matrix must have 20 elements"); //$NON-NLS-1$
		}
		this.matrix = Arrays.copyOf(matrix, 20);
	}

	/**
	 * Returns the 20 elements of the matrix.
	 *
	 * @return the matrix elements
	 */
	public float[] getMatrix() {
		return Arrays.copyOf(matrix, 20);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ColorMatrix other)) {
			return false;
		}
		return Arrays.equals(matrix, other.matrix);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(matrix);
	}

	@Override
	public String toString() {
		return "ColorMatrix " + Arrays.toString(matrix); //$NON-NLS-1$
	}
}
