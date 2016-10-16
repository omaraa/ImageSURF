package feature.calculator;

import ij.plugin.filter.RankFilters;
import ij.process.FloatProcessor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class StandardDeviation implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	public static final int DEFAULT_RADIUS = 5;
	private int radius;

	public StandardDeviation(int radius)
	{
		setRadius(radius);
	}

	public StandardDeviation()
	{
		setRadius(DEFAULT_RADIUS);
	}

	public int getRadius()
	{
		return radius;
	}

	public void setRadius(int radius)
	{
		this.radius = radius;
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		pixels = Arrays.copyOf(pixels, pixels.length);

		//AS DESCRIBED HERE - http://stackoverflow.com/questions/11456565/opencv-mean-sd-filter
		float[] floatPixels = new float[pixels.length];
		for(int i=0;i<pixels.length;i++)
			floatPixels[i] = pixels[i] & 0xff;

		final float[] squaredMean;
		{
			float[] squared = new float[pixels.length];
			for(int i=0;i<pixels.length;i++)
				squared[i] = floatPixels[i]*floatPixels[i];

			FloatProcessor squaredMeanProcessor = new FloatProcessor(width, height, squared);
			new RankFilters().rank(squaredMeanProcessor, radius, RankFilters.MEAN);

			squaredMean = (float[]) squaredMeanProcessor.getPixels();
		}

		final float[] mean;
		{
			FloatProcessor meanProcessor = new FloatProcessor(width, height, floatPixels);
			new RankFilters().rank(meanProcessor, radius, RankFilters.MEAN);

			mean = (float[]) meanProcessor.getPixels();
		}

		byte[] result = new byte[pixels.length];
		for(int i=0;i<pixels.length;i++)
		{
			double stdDev = Math.sqrt((squaredMean[i]) - (mean[i] * mean[i]));
			result[i] = (byte) Math.min(Math.round((float) stdDev*2), 0xff);
		}

		byte[][] resultArray = new byte[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		pixels = Arrays.copyOf(pixels, pixels.length);

		//AS DESCRIBED HERE - http://stackoverflow.com/questions/11456565/opencv-mean-sd-filter
		float[] floatPixels = new float[pixels.length];
		for(int i=0;i<pixels.length;i++)
			floatPixels[i] = pixels[i] & 0xffff;

		final float[] squaredMean;
		{
			float[] squared = new float[pixels.length];
			for(int i=0;i<pixels.length;i++)
				squared[i] = floatPixels[i]*floatPixels[i];

			FloatProcessor squaredMeanProcessor = new FloatProcessor(width, height, squared);
			new RankFilters().rank(squaredMeanProcessor, radius, RankFilters.MEAN);

			squaredMean = (float[]) squaredMeanProcessor.getPixels();
		}

		final float[] mean;
		{
			FloatProcessor meanProcessor = new FloatProcessor(width, height, floatPixels);
			new RankFilters().rank(meanProcessor, radius, RankFilters.MEAN);

			mean = (float[]) meanProcessor.getPixels();
		}

		short[] result = new short[pixels.length];
		for(int i=0;i<pixels.length;i++)
		{
			double stdDev = Math.sqrt((squaredMean[i]) - (mean[i] * mean[i]));
			result[i] = (short) Math.min(Math.round(stdDev*2), 0xffff);
		}

		short[][] resultArray = new short[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	@Override
	public String[] getResultDescriptions()
	{
		return new String[] {getDescription()};
	}

	@Override
	public int getNumImagesReturned()
	{
		return 1;
	}

	@Override
	public String getName()
	{
		return "Standard Deviation";
	}

	@Override
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new StandardDeviation(radius);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		StandardDeviation that = (StandardDeviation) o;

		if (radius != that.radius) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		return radius;
	}
}
