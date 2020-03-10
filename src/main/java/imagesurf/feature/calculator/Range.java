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

import imagesurf.feature.calculator.histogram.NeighbourhoodHistogramCalculator;
import imagesurf.feature.calculator.histogram.PixelReader;

import java.io.Serializable;

public class Range extends NeighbourhoodHistogramCalculator implements Serializable
{
	static final long serialVersionUID = 42L;

	public Range(int radius)
	{
		super(radius);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Range(getRadius());
	}

	@Override
	public String getName()
	{
		return "Range";
	}

	@Override
	protected Calculator getCalculator(PixelReader reader) {
		return pw -> {
			final int min = pw.getHistogramIterator().next().value;
			final int max = pw.getHistogramIteratorDescending().next().value;

			return new int[] {max - min};
		};
	}
}
