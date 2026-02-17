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

import java.util.Objects;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

/**
 * An image descriptor that applies a color matrix to another image descriptor.
 */
final class ColorMatrixImageDescriptor extends ImageDescriptor {
	private final ImageDescriptor original;
	private final ColorMatrix matrix;

	ColorMatrixImageDescriptor(ImageDescriptor original, ColorMatrix matrix) {
		super(original.shouldBeCached());
		this.original = original;
		this.matrix = Objects.requireNonNull(matrix);
	}

	@Override
	public ImageData getImageData(int zoom) {
		ImageData data = original.getImageData(zoom);
		if (data == null) {
			return null;
		}
		float[] m = matrix.getMatrix();

		ImageData result = (ImageData) data.clone();
		PaletteData palette = result.palette;
		if (palette.isDirect) {
			for (int y = 0; y < result.height; y++) {
				for (int x = 0; x < result.width; x++) {
					int pixel = result.getPixel(x, y);
					int alpha = result.getAlpha(x, y);

					float r = ((pixel & palette.redMask) >>> Math.abs(palette.redShift)) / 255f;
					float g = ((pixel & palette.greenMask) >>> Math.abs(palette.greenShift)) / 255f;
					float b = ((pixel & palette.blueMask) >>> Math.abs(palette.blueShift)) / 255f;
					float a = alpha / 255f;

					float nr = m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4];
					float ng = m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9];
					float nb = m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14];
					float na = m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19];

					int inr = Math.min(255, Math.max(0, Math.round(nr * 255)));
					int ing = Math.min(255, Math.max(0, Math.round(ng * 255)));
					int inb = Math.min(255, Math.max(0, Math.round(nb * 255)));
					int ina = Math.min(255, Math.max(0, Math.round(na * 255)));

					int newPixel = (inr << Math.abs(palette.redShift)) & palette.redMask
							| (ing << Math.abs(palette.greenShift)) & palette.greenMask
							| (inb << Math.abs(palette.blueShift)) & palette.blueMask;

					result.setPixel(x, y, newPixel);
					result.setAlpha(x, y, ina);
				}
			}
		} else {
			// Indexed palette
			RGB[] rgbs = palette.getRGBs();
			RGB[] newRgbs = new RGB[rgbs.length];
			for (int i = 0; i < rgbs.length; i++) {
				RGB rgb = rgbs[i];
				float r = rgb.red / 255f;
				float g = rgb.green / 255f;
				float b = rgb.blue / 255f;
				float a = 1f; // Indexed palette doesn't have per-pixel alpha usually, or it's handled separately

				float nr = m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4];
				float ng = m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9];
				float nb = m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14];

				int inr = Math.min(255, Math.max(0, Math.round(nr * 255)));
				int ing = Math.min(255, Math.max(0, Math.round(ng * 255)));
				int inb = Math.min(255, Math.max(0, Math.round(nb * 255)));

				newRgbs[i] = new RGB(inr, ing, inb);
			}
			result.palette = new PaletteData(newRgbs);
			// Alpha is still per-pixel in ImageData even for indexed palettes
			for (int y = 0; y < result.height; y++) {
				for (int x = 0; x < result.width; x++) {
					int alpha = result.getAlpha(x, y);
					float a = alpha / 255f;
					float na = m[15] * 0 + m[16] * 0 + m[17] * 0 + m[18] * a + m[19]; // Simplified
					int ina = Math.min(255, Math.max(0, Math.round(na * 255)));
					result.setAlpha(x, y, ina);
				}
			}
		}
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ColorMatrixImageDescriptor other)) {
			return false;
		}
		return original.equals(other.original) && matrix.equals(other.matrix);
	}

	@Override
	public int hashCode() {
		return original.hashCode() ^ matrix.hashCode();
	}
}
