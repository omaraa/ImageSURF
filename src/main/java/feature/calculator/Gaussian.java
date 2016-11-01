package feature.calculator;

import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.awt.image.ColorModel;
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

		ByteProcessor bp = new ByteProcessor(width, height, pixels);

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

		ShortProcessor sp = new ShortProcessor(width, height, pixels, null);
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
