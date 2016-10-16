package feature;

import net.mintern.primitive.Primitive;

import java.util.Arrays;

public interface FeatureReader
{
	int getClassValue(final int instanceIndex);
	double getValue(final int instanceIndex, final int attributeIndex);

	default int[] getSortedIndices(int attributeIndex, int[] instanceIndices)
	{
		int[] sortedIndices = Arrays.copyOf(instanceIndices, instanceIndices.length);

		Primitive.sort(sortedIndices, (i1, i2) -> Double.compare(getValue(i1, attributeIndex), getValue(i2, attributeIndex)));

		return sortedIndices;
	}

	default short[] getClasses()
	{
		final int numInstances = getNumInstances();
		short[] classes = new short[numInstances];

		for(int i=0;i<numInstances;i++)
		{
			classes[i] = (short) getClassValue(i);
		}

		return classes;
	}

	default double getWeight(final int instanceIndex)
	{
		return 1;
	}

	int getNumInstances();
	int getNumFeatures();
	int getClassIndex();
}
