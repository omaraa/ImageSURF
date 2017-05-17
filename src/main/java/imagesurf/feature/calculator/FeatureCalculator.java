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

package imagesurf.feature.calculator;

import java.io.Serializable;
import java.util.*;

public interface FeatureCalculator extends Serializable
{
	default Object calculate(Object pixels, int width, int height, Map<FeatureCalculator, Object> calculated)
	{
		if(pixels instanceof  byte[])
		{
			Map<FeatureCalculator, byte[][]> byteCalculated = new HashMap<FeatureCalculator, byte[][]>();

			for(FeatureCalculator f : calculated.keySet())
				byteCalculated.put(f, (byte[][]) calculated.get(f));

			if(byteCalculated.containsKey(this))
				return calculated.get(this);

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

			if(shortCalculated.containsKey(this))
				return calculated.get(this);

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

	default FeatureCalculator[] getDependenciesWithTags()
	{
		FeatureCalculator[] dependencies = getDependencies();

		Enumeration<String> tags = getAllTags();

		while (tags.hasMoreElements())
		{
			String tagName = tags.nextElement();
			Object tagValue = getTag(tagName);

			for(FeatureCalculator f :dependencies)
				f.setTag(tagName, tagValue);
		}

		return dependencies;
	}

	FeatureCalculator duplicate();

	int getRadius();

	Object getTag(String tagName);
	void setTag(String tagName,Object tagValue);
	Enumeration<String> getAllTags();
	void removeTag(String tagName);
	default void removeTags(Collection<String> tagNames)
	{
		for(String tagName : tagNames)
			removeTag(tagName);
	}

	default boolean hasTag(String tagName)
	{
		Enumeration<String> tags = getAllTags();

		while (tags.hasMoreElements())
			if(tags.nextElement().equals(tagName))
				return true;

		return false;
	}
}
