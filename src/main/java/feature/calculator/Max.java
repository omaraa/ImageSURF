package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Max extends Rank implements Serializable
{
	static final long serialVersionUID = 42L;

	public Max(double radius)
	{
		super(radius, RankFilter.Type.MAX);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Max(getRadius());
	}

	@Override
	public String getName()
	{
		return "Max";
	}
}
