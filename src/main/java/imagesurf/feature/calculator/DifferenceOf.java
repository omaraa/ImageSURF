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
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DifferenceOf implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private final FeatureCalculator featureCalculatorA;
	private final FeatureCalculator featureCalculatorB;
	private final double multiplier;
	private final double offset;

	private static final double DEFAULT_OFFSET = 255.0 / 2.0;
	private static final double DEFAULT_MULTIPLIER = 1.0;

	public DifferenceOf(FeatureCalculator featureCalculatorA, FeatureCalculator featureCalculatorB)
	{
		this(featureCalculatorA, featureCalculatorB, DEFAULT_MULTIPLIER);
	}

	public DifferenceOf(FeatureCalculator featureCalculatorA, FeatureCalculator featureCalculatorB, double multiplier)
	{
		this(featureCalculatorA, featureCalculatorB, multiplier, DEFAULT_OFFSET);
	}

	public DifferenceOf(FeatureCalculator featureCalculatorA, FeatureCalculator featureCalculatorB, double multiplier, double offset)
	{
		if(featureCalculatorA.getNumImagesReturned() != featureCalculatorA.getNumImagesReturned())
			throw new IllegalArgumentException("Feature calculator result lengths differ.");

		this.featureCalculatorA = featureCalculatorA;
		this.featureCalculatorB = featureCalculatorB;

		this.multiplier = multiplier;
		this.offset = offset;
	}

	@Override
	public byte[][] calculate(byte[] pixels, final int width, final int height, final Map<FeatureCalculator, byte[][]> calculated)
	{
		final byte[][] result = new byte[featureCalculatorA.getNumImagesReturned()][width * height];
		final byte[][] featureA = calculated!=null && calculated.containsKey(featureCalculatorA) ? calculated.get(featureCalculatorA) : featureCalculatorA.calculate(pixels, width, height, calculated);
		final byte[][] featureB = calculated!=null && calculated.containsKey(featureCalculatorB) ? calculated.get(featureCalculatorB) : featureCalculatorB.calculate(pixels, width, height, calculated);

		for (int imageIndex = 0; imageIndex < featureCalculatorA.getNumImagesReturned(); imageIndex++)
		{
			byte[] currentResult = result[imageIndex];
			byte[] imageA = featureA[imageIndex];
			byte[] imageB = featureB[imageIndex];

			double featurePixelB, featurePixelA;

			for (int i = 0; i < pixels.length; i++)
			{
				featurePixelA = (0xff & imageA[i]);
				featurePixelB = (0xff & imageB[i]);


				double resultPixel = (featurePixelB - featurePixelA) / 2;

				resultPixel *= multiplier;
				resultPixel += offset;

				resultPixel = Math.max(0, resultPixel);
				resultPixel = Math.min(255, resultPixel);

				currentResult[i] = (byte) resultPixel;
			}
		}

		if(calculated!=null)
			calculated.put(this, result);

		return result;
	}

	@Override
	public short[][] calculate(short[] pixels, final int width, final int height, final Map<FeatureCalculator, short[][]> calculated)
	{
		final short[][] result = new short[featureCalculatorA.getNumImagesReturned()][width * height];
		final short[][] featureA = calculated!=null && calculated.containsKey(featureCalculatorA) ? calculated.get(featureCalculatorA) : featureCalculatorA.calculate(pixels, width, height, calculated);
		final short[][] featureB = calculated!=null && calculated.containsKey(featureCalculatorB) ? calculated.get(featureCalculatorB) : featureCalculatorB.calculate(pixels, width, height, calculated);

		for (int imageIndex = 0; imageIndex < featureCalculatorA.getNumImagesReturned(); imageIndex++)
		{
			short[] currentResult = result[imageIndex];
			short[] imageA = featureA[imageIndex];
			short[] imageB = featureB[imageIndex];

			double featurePixelB, featurePixelA;

			for (int i = 0; i < pixels.length; i++)
			{
				featurePixelA = (0xffff & imageA[i]);
				featurePixelB = (0xffff & imageB[i]);

				double resultPixel = (featurePixelB - featurePixelA) / 2;

				resultPixel *= multiplier;
				resultPixel += offset;

				resultPixel = Math.max(0, resultPixel);
				resultPixel = Math.min(65535, resultPixel);

				currentResult[i] = (short) resultPixel;
			}
		}

		if(calculated!=null)
			calculated.put(this, result);

		return result;
	}

	@Override
	public String[] getResultDescriptions()
	{
		return new String[]{getDescription()};
	}

	@Override
	public int getNumImagesReturned()
	{
		return featureCalculatorA.getNumImagesReturned();
	}

	@Override
	public String getName()
	{
		return "Difference between " + featureCalculatorA.getDescription()+" and "+featureCalculatorB.getDescription();
	}

	@Override
	public String getDescription()
	{
		return getName();
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new DifferenceOf(featureCalculatorA.duplicate(), featureCalculatorB.duplicate());
	}

	@Override
	public int getRadius()
	{
		return Math.max(featureCalculatorA.getRadius(), featureCalculatorB.getRadius());
	}


	private final ConcurrentHashMap<String, Object> tags = new ConcurrentHashMap<>();

	@Override
	public Object getTag(String tagName)
	{
		return tags.get(tagName);
	}

	@Override
	public void setTag(String tagName, Object tagValue)
	{
		tags.put(tagName, tagValue);
	}

	@Override
	public Enumeration<String> getTagNames()
	{
		return tags.keys();
	}

	@Override
	public void removeTag(String tagName)
	{
		tags.remove(tagName);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (!(o instanceof DifferenceOf))
			return false;

		DifferenceOf that = (DifferenceOf) o;

		if (Double.compare(that.multiplier, multiplier) != 0)
			return false;
		if (Double.compare(that.offset, offset) != 0)
			return false;
		if (!featureCalculatorA.equals(that.featureCalculatorA))
			return false;
		if (!featureCalculatorB.equals(that.featureCalculatorB))
			return false;
		return tags.equals(that.tags);
	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		result = featureCalculatorA.hashCode();
		result = 31 * result + featureCalculatorB.hashCode();
		temp = Double.doubleToLongBits(multiplier);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(offset);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		result = 31 * result + tags.hashCode();
		return result;
	}

	//	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[] {featureCalculatorA, featureCalculatorB};
	}

	public Class[] getFeatureCalculatorClasses()
	{
		return new Class[] {featureCalculatorA.getClass(), featureCalculatorB.getClass()};
	}
}