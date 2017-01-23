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

package imagesurf.feature;

import imagesurf.feature.calculator.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum PixelType {
	GRAY_8_BIT, GRAY_16_BIT;

	int getMax()
	{
		switch (this)
		{
			case GRAY_8_BIT:
				return (1<<8)-1;
			case GRAY_16_BIT:
				return (1<<16)-1;
			default:
				throw new IllegalArgumentException("Pixel type not yet implemented: "+this);
		}
	}

	public FeatureCalculator[] getAllFeatureCalculators(int minRadius, int maxRadius)
	{
		final int[] scales = IntStream.range(2, (int) (Math.log(maxRadius) / Math.log(2)+1))
				.map(i -> 1<<i)
				.map(i -> i%2 == 0 ? i+1 : i)
				.filter(i -> i>=minRadius && i<= maxRadius).toArray();

		if(scales.length > 0 && scales[0] == 2)
			scales[0] = 3;

		List<FeatureCalculator> f = new ArrayList<FeatureCalculator>();

		for (int scale : scales)
		{
			f.add(new Mean(scale));
			f.add(new Min(scale));
			f.add(new Max(scale));
			f.add(new Gaussian(scale));
			f.add(new Median(scale));
			f.add(new StandardDeviation(scale));
			f.add(new LocalIntensity(scale));

			f.add(new DifferenceOf(Identity.get(),new Mean(scale)));
			f.add(new DifferenceOf(Identity.get(),new Gaussian(scale)));
			f.add(new DifferenceOf(Identity.get(),new Median(scale)));
			f.add(new DifferenceOf(Identity.get(),new Min(scale), 2.0, 0));
			f.add(new DifferenceOf(Identity.get(), new Max(scale), 2.0, getMax()));

			f.add(new Range(scale));
			f.add(new Entropy(scale));

			for(int s2 : scales)
				if(s2 < scale)
				{
					f.add(new DifferenceOf(new Gaussian(s2), new Gaussian(scale)));
					f.add(new DifferenceOf(new Entropy(s2), new Entropy(scale)));
				}
		}

		f.add(Identity.get());

		return f.toArray(new FeatureCalculator[f.size()]);
	}
};
