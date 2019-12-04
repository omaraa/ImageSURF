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

import java.io.Serializable;

public class Entropy extends HistogramNeighbourhoodCalculator implements Serializable
{
	static final long serialVersionUID = 42L;

	private static final double one_over_log2 = 1d/Math.log(2.0);

	public Entropy(int radius)
	{
		super(radius);
	}

	@Override
	protected Calculator getCalculator(final PixelReader reader, final int numBins) {

		final int min, max;
		{
			int tempMin = Integer.MAX_VALUE;
			int tempMax = Integer.MIN_VALUE;
			for (int i = 0; i < reader.numPixels(); i++) {
				final int value = reader.get(i);
				if (value < tempMin)
					tempMin = value;
				if (value > tempMax)
					tempMax = value;
			}
			min = tempMin;
			max = tempMax;
		}

		final double binsPerBit;
		{
			final double numBits = reader.numBits();
			binsPerBit = ((double)(numBins)/numBits);
		}

		return h -> {
			final double oneOverTotal;
			{
				double total = 0;
				for (int k = min; k <= max; k++)
					total += h[k];
				oneOverTotal = 1d/total;
			}

			double entropy = 0;
			for (int k = min ; k < max ; k++ )
			{
				if (h[k]>0)
				{
					double p = h[k]*oneOverTotal; // calculate p
					entropy += -p * Math.log(p)*one_over_log2;
				}
			}

			return (int) Math.floor(entropy * binsPerBit);
		};
	}

	@Override
	public String getName()
	{
		return "Entropy";
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Entropy(getRadius());
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (!(o instanceof Entropy))
			return false;

		Entropy entropy = (Entropy) o;

		if (getRadius() != entropy.getRadius())
			return false;
		return tags.equals(entropy.tags);
	}

	@Override
	public int hashCode()
	{
		int result = getRadius();
		result = 31 * result + tags.hashCode();
		return result;
	}
}