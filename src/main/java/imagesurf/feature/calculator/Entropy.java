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

	private static final double log2 = Math.log(2.0);

	public Entropy(int radius)
	{
		super(radius);
	}

	@Override
	protected void calculate(PixelReader reader, PixelWriter writer, int width, int height, int[] histogram, Calculator calculator) {

		final int min, max;
		{
			int tempMin = Integer.MAX_VALUE;
			int tempMax = Integer.MIN_VALUE;
			for (int i = 0; i < width * height; i++) {
				final int value = reader.get(i);
				if (value < tempMin)
					tempMin = value;
				if (value > tempMax)
					tempMax = value;
			}
			min = tempMin;
			max = tempMax;
		}

		final int numBins = histogram.length;
		final double numBits = reader.numBits();

		calculator = h -> {
			double total = 0;
			for (int k = min ; k <= max; k++ )
				total += h[ k ];

			double entropy = 0;
			for (int k = min ; k < max ; k++ )
			{
				if (h[k]>0)
				{
					double p = h[k]/total; // calculate p
					entropy += -p * Math.log(p)/log2;
				}
			}

			return (int) Math.floor(entropy * ((double)(numBins)/numBits));
		};

		super.calculate(reader, writer, width, height, histogram, calculator);
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