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

public class StandardDeviation extends NeighbourhoodHistogramCalculator implements Serializable
{
	static final long serialVersionUID = 42L;

	public StandardDeviation(int radius)
	{
		super(radius);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new StandardDeviation(getRadius());
	}

	@Override
	public String getName()
	{
		return "Standard Deviation";
	}

	@Override
	protected Calculator getCalculator(PixelReader reader) {
		final int maxValue = reader.maxValue();

		return pw -> {
			final int numPixels = pw.getNumPixels();
			double sum = 0;
			double sumSquared = 0;

			final int numEntries = pw.getNumUniqueValues();
			final Iterator<Histogram.Bin> it = pw.getHistogramIterator();

			for(int i = 0; i < numEntries; i++)
			{
				final Histogram.Bin b = it.next();
				final int count = b.getCount();
				final double value = b.value;

				sum += (count * value);

				sumSquared += (value * value * count);
			}

			final double mean = sum/numPixels;
			final double squaredMean = sumSquared/numPixels;

			int stdDev = (int) Math.round(Math.sqrt(squaredMean - (mean * mean)));

			return new int[] {Math.min(stdDev * 2, maxValue)};
		};
	}
}
