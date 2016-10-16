package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Min implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	public static final int DEFAULT_RADIUS = 5;
	private RankFilter filter;

	public Min(double radius)
	{
		setRadius(radius);
	}

	public Min()
	{
		setRadius(DEFAULT_RADIUS);
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		byte[][] result = {filter.rank(Arrays.copyOf(pixels, pixels.length), width, height)};

		if(calculated!=null)
			calculated.put(this, result);

		return result;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		short[][] result = {filter.rank(Arrays.copyOf(pixels, pixels.length), width, height)};

		if(calculated!=null)
			calculated.put(this, result);

		return result;
	}

	public int getRadius()
	{
		return (int) filter.radius;
	}

	public void setRadius(double radius)
	{
		filter = new RankFilter(RankFilter.Type.MIN, radius);
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
		return new Min(filter.radius);
	}

	@Override
	public String getName()
	{
		return "Min";
	}

	@Override
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}


	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Min min = (Min) o;

		if (!filter.equals(min.filter)) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		return filter.hashCode();
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}
}
