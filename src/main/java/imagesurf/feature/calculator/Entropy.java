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
import imagesurf.feature.calculator.histogram.PixelWindow;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;

public class Entropy extends NeighbourhoodHistogramCalculator implements Serializable
{
	static final long serialVersionUID = 42L;

	private static final double one_over_log2 = 1d/Math.log(2.0);

	public Entropy(int radius)
	{
		super(radius);
	}

	@Override
	protected Calculator getCalculator(final PixelReader reader) {

		final double binsPerBit = (double) reader.numValues() / (double) reader.numBits();

		return new Calculator() {

			final IntDoubleHashMap calculated = new IntDoubleHashMap(reader.uniqueValues().length);
			int calculatedForNumPixels = 0;

			@Override
			public int[] calculate(PixelWindow pw) {
				int numPixels = pw.getNumPixels();
				final double oneOverTotal = 1d / numPixels;

				final Iterator<Histogram.Bin> added;
				final Iterator<Histogram.Bin> removed;
				if(calculatedForNumPixels != numPixels) {
					calculated.clear();
					added = pw.getHistogramIterator();
					removed = Collections.emptyIterator();
					calculatedForNumPixels = numPixels;
				} else {
					added = pw.getLastAdded();
					removed = pw.getLastRemoved();
				}

				added.forEachRemaining( b -> {
					final double p = b.getCount() * oneOverTotal;
					final double entropy = -p * Math.log(p);
					calculated.put(b.value, entropy);
				});

				removed.forEachRemaining( b -> {
					if(b.getCount() == 0) {
						calculated.remove(b.value);
					} else {
						final double p = b.getCount() * oneOverTotal;
						final double entropy = -p * Math.log(p);
						calculated.put(b.value, entropy);
					}
				});

				double totalEntropy = calculated.sum();

				return new int[]{(int) Math.floor(totalEntropy * one_over_log2 * binsPerBit)};
			}
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