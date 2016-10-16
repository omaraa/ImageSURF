package feature.calculator;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public interface FeatureCalculator extends Serializable
{
	default Object calculate(Object pixels, int width, int height, Map<FeatureCalculator, Object> calculated)
	{
		if(pixels instanceof  byte[])
		{
			Map<FeatureCalculator, byte[][]> byteCalculated = new HashMap<FeatureCalculator, byte[][]>();

			for(FeatureCalculator f : calculated.keySet())
				byteCalculated.put(f, (byte[][]) calculated.get(f));

			byte[][] result = calculate((byte[]) pixels, width, height, byteCalculated);

			for(FeatureCalculator f : byteCalculated.keySet())
			{
				if(!calculated.containsKey(f) || calculated.get(f) == null)
				{
					calculated.put(f, byteCalculated.get(f));
				}
			}

			return result;
		}
		else if(pixels instanceof  short[])
		{
			Map<FeatureCalculator, short[][]> shortCalculated = new HashMap<FeatureCalculator, short[][]>();

			for(FeatureCalculator f : calculated.keySet())
				shortCalculated.put(f, (short[][]) calculated.get(f));

			short[][] result = calculate((short[]) pixels, width, height, shortCalculated);

			for(FeatureCalculator f : shortCalculated.keySet())
			{
				if(!calculated.containsKey(f) || calculated.get(f) == null)
				{
					calculated.put(f, shortCalculated.get(f));
				}
			}

			return result;
		}

		throw new IllegalArgumentException("Pixels must be an array of short or byte");
	};

	default byte[][] calculate(byte[] pixels, int width, int height)
	{
		return (byte[][]) calculate((Object)pixels,width,height,new HashMap<FeatureCalculator, Object>());
	}

	default short[][] calculate(short[] pixels, int width, int height)
	{
		return (short[][]) calculate((Object)pixels,width,height,new HashMap<FeatureCalculator, Object>());
	}

	byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated);
	short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated);
	String[] getResultDescriptions();
	int getNumImagesReturned();
	String getName();
	String getDescription();

	FeatureCalculator[] getDependencies();

	FeatureCalculator duplicate();

	int getRadius();
}
