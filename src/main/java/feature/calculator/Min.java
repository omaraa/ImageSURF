package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Min extends Rank implements Serializable
{
	static final long serialVersionUID = 42L;

	public Min(double radius)
	{
		super(radius, RankFilter.Type.MIN);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Min(getRadius());
	}

	@Override
	public String getName()
	{
		return "Min";
	}
}
