package feature.calculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class Mean extends Rank implements Serializable
{
	static final long serialVersionUID = 42L;

	public Mean(double radius)
	{
		super(radius, RankFilter.Type.MEAN);
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Mean(getRadius());
	}

	@Override
	public String getName()
	{
		return "Mean";
	}
}
