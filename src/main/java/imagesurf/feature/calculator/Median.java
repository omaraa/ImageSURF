/*
 *     This file is part of ImageSURF.
 *
 *     ImageSURF is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ImageSURF is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ImageSURF.  If not, see <http://www.gnu.org/licenses/>.
 */

package imagesurf.feature.calculator;

import imagesurf.feature.calculator.histogram.Histogram;
import imagesurf.feature.calculator.histogram.NeighbourhoodHistogramCalculator;
import imagesurf.feature.calculator.histogram.PixelReader;

import java.io.Serializable;
import java.util.Iterator;

public class Median extends NeighbourhoodHistogramCalculator implements Serializable
{
	static final long serialVersionUID = 42L;

	public Median(int radius)
	{
		super(radius);
	}

	@Override
	protected Calculator getCalculator(final PixelReader reader, final int numBins) {

		return pw -> {
			final int totalPixels = pw.getNumPixels();
			final int halfTotal = totalPixels / 2;
			int counted = 0;

			final int numEntries = pw.getNumUniqueValues();
			final Iterator<Histogram.Bin> it = pw.getHistogramIterator();

			for(int i = 0; i < numEntries; i++)
			{
				final Histogram.Bin b = it.next();
				counted += b.getCount();

				if(counted >= halfTotal)
					return new int[] {b.value};
			}

			throw new RuntimeException("Failed to calculate median");
		};
	}

	@Override
	public String getName()
	{
		return "Median";
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Median(getRadius());
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (!(o instanceof Median))
			return false;

		Median median = (Median) o;

		if (getRadius() != median.getRadius())
			return false;
		return tags.equals(median.tags);
	}

	@Override
	public int hashCode()
	{
		int result = getRadius();
		result = 31 * result + tags.hashCode();
		return result;
	}
}
