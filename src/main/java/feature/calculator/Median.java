package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Median extends Rank implements Serializable
{
	static final long serialVersionUID = 42L;

	public Median(double radius)
	{
		super(radius, RankFilter.Type.MEDIAN);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Median(getRadius());
	}

	@Override
	public String getName()
	{
		return "Median";
	}
}
