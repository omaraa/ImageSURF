package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class DifferenceFrom implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private final FeatureCalculator featureCalculator;
	private final double multiplier;
	private final double offset;

	private static final double DEFAULT_OFFSET = 255.0 / 2.0;
	private static final double DEFAULT_MULTIPLIER = 1.0;

	public DifferenceFrom(FeatureCalculator featureCalculator)
	{
		this(featureCalculator, DEFAULT_MULTIPLIER);
	}

	public DifferenceFrom(FeatureCalculator featureCalculator, double multiplier)
	{
		this(featureCalculator, multiplier, DEFAULT_OFFSET);
	}

	public DifferenceFrom(FeatureCalculator featureCalculator, double multiplier, double offset)
	{
		this.featureCalculator = featureCalculator;

		this.multiplier = multiplier;
		this.offset = offset;
	}

	@Override
	public byte[][] calculate(byte[] pixels, final int width, final int height, final Map<FeatureCalculator, byte[][]> calculated)
	{
		pixels = Arrays.copyOf(pixels, pixels.length);
		final byte[] original = Arrays.copyOf(pixels, pixels.length);

		final byte[][] result = new byte[featureCalculator.getNumImagesReturned()][width * height];
		final byte[][] other = calculated!=null && calculated.containsKey(featureCalculator) ? calculated.get(featureCalculator) : featureCalculator.calculate(pixels, width, height, calculated);

		for (int imageIndex = 0; imageIndex < featureCalculator.getNumImagesReturned(); imageIndex++)
		{
			byte[] currentResult = result[imageIndex];
			byte[] otherImage = other[imageIndex];

			double currentPixel, gaussianPixel;

			for (int i = 0; i < pixels.length; i++)
			{
				gaussianPixel = (0xff & otherImage[i]);
				currentPixel = (0xff & original[i]);

				double resultPixel = (currentPixel - gaussianPixel) / 2;

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
		pixels = Arrays.copyOf(pixels, pixels.length);
		final short[] original = Arrays.copyOf(pixels, pixels.length);

		final short[][] result = new short[featureCalculator.getNumImagesReturned()][width * height];
		final short[][] other = calculated!=null && calculated.containsKey(featureCalculator) ? calculated.get(featureCalculator) : featureCalculator.calculate(pixels, width, height, calculated);

		for (int imageIndex = 0; imageIndex < featureCalculator.getNumImagesReturned(); imageIndex++)
		{
			short[] currentResult = result[imageIndex];
			short[] otherImage = other[imageIndex];

			double currentPixel, gaussianPixel;

			for (int i = 0; i < pixels.length; i++)
			{
				gaussianPixel = (0xffff & otherImage[i]);
				currentPixel = (0xffff & original[i]);

				double resultPixel = (currentPixel - gaussianPixel) / 2;

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
		return featureCalculator.getNumImagesReturned();
	}

	@Override
	public String getName()
	{
		return "Difference from " + featureCalculator.getDescription();
	}

	@Override
	public String getDescription()
	{
		return getName();
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new DifferenceFrom(featureCalculator.duplicate());
	}

	@Override
	public int getRadius()
	{
		return featureCalculator.getRadius();
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DifferenceFrom that = (DifferenceFrom) o;

		if (Double.compare(that.multiplier, multiplier) != 0) return false;
		if (Double.compare(that.offset, offset) != 0) return false;
		if (!featureCalculator.equals(that.featureCalculator)) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		int result;
		long temp;
		result = featureCalculator.hashCode();
		temp = Double.doubleToLongBits(multiplier);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(offset);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

//	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[] {featureCalculator};
	}
}
