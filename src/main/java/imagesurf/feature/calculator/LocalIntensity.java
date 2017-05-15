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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalIntensity implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	final int radius;
	private final Min min;
	private final Max max;

	public LocalIntensity(int radius)
	{
		this.radius = radius;

		this.min = new Min(radius);
		this.max = new Max(radius);
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[] {min, max};
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		byte[] result = Arrays.copyOf(pixels, pixels.length);

		final byte[][] minResult;
		if(calculated!=null && calculated.containsKey(this.min))
		{
			minResult = calculated.get(this.min);
		}
		else
		{
			minResult = this.min.calculate(Arrays.copyOf(pixels, pixels.length), width, height, calculated);

			if(calculated!=null)
				calculated.put(min, minResult);
		}

		final byte[][] maxResult;
		if(calculated!=null && calculated.containsKey(this.max))
		{
			maxResult = calculated.get(this.max);
		}
		else
		{
			maxResult = this.max.calculate(Arrays.copyOf(pixels, pixels.length), width, height, calculated);

			if(calculated!=null)
				calculated.put(max, maxResult);
		}

		byte[] min = minResult[0];
		byte[] max = maxResult[0];


		double currentPixel, maxPixel, minPixel, range;

		for (int i = 0; i < pixels.length; i++)
		{
			minPixel = (0xff & min[i]);
			maxPixel = (0xff & max[i]);
			currentPixel = (0xff & result[i]);

			range = maxPixel - minPixel;

			double resultPixel = (currentPixel - minPixel) * (255 / range);

			resultPixel = Math.max(0, resultPixel);
			resultPixel = Math.min(255, resultPixel);

			result[i] = (byte) resultPixel;
		}

		byte[][] resultArray = new byte[][]{result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		short[] result = Arrays.copyOf(pixels, pixels.length);

		final short[][] minResult;
		if(calculated!=null && calculated.containsKey(this.min))
		{
			minResult = calculated.get(this.min);
		}
		else
		{
			minResult = this.min.calculate(Arrays.copyOf(pixels, pixels.length), width, height, calculated);

			if(calculated!=null)
				calculated.put(min, minResult);
		}

		final short[][] maxResult;
		if(calculated!=null && calculated.containsKey(this.max))
		{
			maxResult = calculated.get(this.max);
		}
		else
		{
			maxResult = this.max.calculate(Arrays.copyOf(pixels, pixels.length), width, height, calculated);

			if(calculated!=null)
				calculated.put(max, maxResult);
		}

		short[] min = minResult[0];
		short[] max = maxResult[0];


		double currentPixel, maxPixel, minPixel, range;

		for (int i = 0; i < pixels.length; i++)
		{
			minPixel = (0xffff & min[i]);
			maxPixel = (0xffff & max[i]);
			currentPixel = (0xffff & result[i]);

			range = maxPixel - minPixel;

			double resultPixel = (currentPixel - minPixel) * (65535 / range);

			resultPixel = Math.max(0, resultPixel);
			resultPixel = Math.min(65535, resultPixel);

			result[i] = (short) resultPixel;
		}

		short[][] resultArray = new short[][]{result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	@Override
	public String[] getResultDescriptions()
	{
		return new String[]{getDescription()};
	}


	@Override
	public int getNumImagesReturned()
	{
		return 1;
	}

	@Override
	public String getName()
	{
		return "Locally scaled intensity";
	}

	@Override
	public String getDescription()
	{
		return getName()+" (" + radius + ")";
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new LocalIntensity(radius);
	}

	@Override
	public int getRadius()
	{
		return radius;
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
		if (!(o instanceof LocalIntensity))
			return false;

		LocalIntensity that = (LocalIntensity) o;

		if (radius != that.radius)
			return false;
		if (!min.equals(that.min))
			return false;
		if (!max.equals(that.max))
			return false;
		return tags.equals(that.tags);
	}

	@Override
	public int hashCode()
	{
		int result = radius;
		result = 31 * result + min.hashCode();
		result = 31 * result + max.hashCode();
		result = 31 * result + tags.hashCode();
		return result;
	}
}
