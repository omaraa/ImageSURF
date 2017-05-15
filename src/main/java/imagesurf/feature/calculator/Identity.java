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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Identity implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	public static Identity get()
	{
		return new Identity();
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
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

	private final ConcurrentHashMap<String, Object> tags = new ConcurrentHashMap<>();

	@Override
	public Object getTag(String tagName)
	{
		return tags.get(tagName);
	}

	@Override
	public void setTag(String tagName, Object tagValue)
	{
		tags.put(tagName, tagValue);
	}

	@Override
	public Enumeration<String> getAllTags()
	{
		return tags.keys();
	}

	@Override
	public void removeTag(String tagName)
	{
		tags.remove(tagName);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
			return true;
		if (!(o instanceof Identity))
			return false;

		Identity identity = (Identity) o;

		return tags.equals(identity.tags);
	}

	@Override
	public int hashCode()
	{
		return tags.hashCode();
	}
}
