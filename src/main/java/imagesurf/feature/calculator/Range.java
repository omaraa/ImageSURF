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
import java.util.Map;

public class Range implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	public static final int DEFAULT_RADIUS = 5;
	private int radius;

	private Min minCalculator;
	private Max maxCalculator;

	public Range(int radius)
	{
		setRadius(radius);
	}

	public Range()
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

		minCalculator = new Min(radius);
		maxCalculator = new Max(radius);
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[] {new Min(radius), new Max(radius)};
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		byte[] min = (calculated.containsKey(minCalculator) ? calculated.get(minCalculator) : minCalculator.calculate(pixels, width, height))[0];
		byte[] max = (calculated.containsKey(maxCalculator) ? calculated.get(maxCalculator) : maxCalculator.calculate(pixels, width, height))[0];

		byte[] result = new byte[pixels.length];
		for(int i=0;i<pixels.length;i++)
		{
			result[i] = (byte) ((0xff & max[i]) - (0xff & min[i]));
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

		short[] min = (calculated.containsKey(minCalculator) ? calculated.get(minCalculator) : minCalculator.calculate(pixels, width, height))[0];
		short[] max = (calculated.containsKey(maxCalculator) ? calculated.get(maxCalculator) : maxCalculator.calculate(pixels, width, height))[0];

		short[] result = new short[pixels.length];
		for(int i=0;i<pixels.length;i++)
		{
			result[i] = (short) ((0xffff & max[i]) - (0xffff & min[i]));
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
		return "Range";
	}

	@Override
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Range(radius);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Range that = (Range) o;

		if (radius != that.radius) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		return radius;
	}
}
