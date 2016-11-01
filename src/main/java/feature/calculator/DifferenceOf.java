package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

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

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		DifferenceOf that = (DifferenceOf) o;

		if (Double.compare(that.multiplier, multiplier) != 0)
			return false;
		if (Double.compare(that.offset, offset) != 0)
			return false;
		if (featureCalculatorA != null ? !featureCalculatorA.equals(that.featureCalculatorA) : that.featureCalculatorA != null)
			return false;
		return featureCalculatorB != null ? featureCalculatorB.equals(that.featureCalculatorB) : that.featureCalculatorB == null;

	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		result = featureCalculatorA != null ? featureCalculatorA.hashCode() : 0;
		result = 31 * result + (featureCalculatorB != null ? featureCalculatorB.hashCode() : 0);
		temp = Double.doubleToLongBits(multiplier);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(offset);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	//	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[] {featureCalculatorA, featureCalculatorB};
	}
}
