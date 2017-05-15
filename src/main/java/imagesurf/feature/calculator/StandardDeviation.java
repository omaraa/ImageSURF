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

import ij.plugin.filter.RankFilters;
import ij.process.FloatProcessor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

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
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

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
	public Enumeration<String> getAllTags()
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
		if (!(o instanceof StandardDeviation))
			return false;

		StandardDeviation that = (StandardDeviation) o;

		if (radius != that.radius)
			return false;
		return tags.equals(that.tags);
	}

	@Override
	public int hashCode()
	{
		int result = radius;
		result = 31 * result + tags.hashCode();
		return result;
	}
}
