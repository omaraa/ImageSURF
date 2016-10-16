package feature;

import feature.calculator.*;

import java.util.ArrayList;
import java.util.List;

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

	public FeatureCalculator[] getDefaultFeatureCalculators()
	{
		final int[] scales = new int[]{3, 5, 7, 11, 23, 31, 61, 121, 241};
		List<FeatureCalculator> f = new ArrayList<FeatureCalculator>();

		for (int scale : scales)
		{
			f.add(new Mean(scale));
			f.add(new Min(scale));
			f.add(new Max(scale));
//			f.add(new Median(scale));
			f.add(new StandardDeviation(scale));
			f.add(new LocalIntensity(scale));

			f.add(new DifferenceFrom(new Mean(scale)));
//			f.add(new DifferenceFrom(new Median(scale)));
			f.add(new DifferenceFrom(new Min(scale), 2.0, 0));
			f.add(new DifferenceFrom(new Max(scale), 2.0, getMax()));
		}

		f.add(Identity.get());

		return f.toArray(new FeatureCalculator[f.size()]);
	}
};
