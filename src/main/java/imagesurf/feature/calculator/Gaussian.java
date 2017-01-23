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

import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Gaussian implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;
	public static final int DEFAULT_RADIUS = 5;
	private int radius = DEFAULT_RADIUS;

	public Gaussian(int radius)
	{
		setRadius(radius);
	}

	public Gaussian()
	{
		setRadius(DEFAULT_RADIUS);
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		ByteProcessor bp = new ByteProcessor(width, height, Arrays.copyOf(pixels, pixels.length));

		GaussianBlur gs = new GaussianBlur();
		gs.blurGaussian(bp, 0.4*getRadius(), 0.4*getRadius(), 0.01);

		byte[][] result = {(byte[]) bp.getPixels()};

		if(calculated!=null)
			calculated.put(this, result);

		return result;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		ShortProcessor sp = new ShortProcessor(width, height, Arrays.copyOf(pixels, pixels.length), null);
		GaussianBlur gs = new GaussianBlur();
		gs.blurGaussian(sp, 0.4*getRadius(), 0.4*getRadius(), 0.01);

		short[][] result = {(short[]) sp.getPixels()};

		if(calculated!=null)
			calculated.put(this, result);

		return result;
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
	public FeatureCalculator duplicate()
	{
		return new Gaussian(radius);
	}

	@Override
	public String getName()
	{
		return "Gaussian Blur";
	}

	@Override
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		Gaussian gaussian = (Gaussian) o;

		return radius == gaussian.radius;

	}

	@Override
	public int hashCode()
	{
		return radius;
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}
}
