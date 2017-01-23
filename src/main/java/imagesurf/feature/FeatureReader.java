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

package imagesurf.feature;

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
