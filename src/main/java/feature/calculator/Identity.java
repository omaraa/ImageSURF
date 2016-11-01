package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Identity implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private static final Identity SINGLETON = new Identity();

	public static Identity get()
	{
		return SINGLETON;
	}

	private Identity() {}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		byte[][] identity = new byte[][] {Arrays.copyOf(pixels, pixels.length)};
		calculated.put(this, identity);

		return identity;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		short[][] identity = new short[][] {Arrays.copyOf(pixels, pixels.length)};
		calculated.put(this, identity);

		return identity;
	}

	@Override
	public String[] getResultDescriptions()
	{
		return new String[] {"Identity"};
	}

	@Override
	public int getNumImagesReturned()
	{
		return 1;
	}

	@Override
	public String getName()
	{
		return "Identity";
	}

	@Override
	public String getDescription()
	{
		return getName();
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Identity();
	}

	@Override
	public int getRadius()
	{
		return 1;
	}

	@Override
	public boolean equals(Object obj)
	{
		return obj.getClass() == this.getClass();
	}

	@Override
	public int hashCode()
	{
		return 42;
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

}
