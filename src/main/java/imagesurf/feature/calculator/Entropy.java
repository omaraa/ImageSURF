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

import ij.gui.OvalRoi;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

/**
 * Reimplementation of entropy filter from WEKA trainable segmentation 20170111
 * https://github.com/fiji/Trainable_Segmentation/blob/master/src/main/java/trainableSegmentation/filters/Entropy_Filter.java
 */
public class Entropy implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private int radius;

	public Entropy(int radius)
	{
		setRadius(radius);
	}

	public int getRadius()
	{
		return radius;
	}

	public void setRadius(int radius)
	{
		this.radius = radius;
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

	@Override
	public byte[][] calculate(byte[] pixels, int width, int height, Map<FeatureCalculator, byte[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		pixels = Arrays.copyOf(pixels, pixels.length);

		final int numBins = 256;
		final double log2 = Math.log(2.0);

		final int min, max;
		{
			int tempMin = Integer.MAX_VALUE;
			int tempMax = Integer.MIN_VALUE;

				for (byte b : pixels)
			{
				int value = 0xff & b;
				if (value < tempMin)
					tempMin = value;
				else if (value > tempMax)
					tempMax = value;
			}

			min = tempMin;
			max = tempMax;
		}

		final ByteProcessor bp = new ByteProcessor(width, height, pixels);
		bp.setHistogramRange( 0, numBins-1 );
		bp.setHistogramSize( numBins);

		byte[] result = new byte[width*height];

		final int size = 2 * radius + 1;

		for(int x=0; x<bp.getWidth(); x++)
		{
			for(int y=0; y<bp.getHeight(); y++)
			{
				final OvalRoi roi = new OvalRoi(x-radius, y-radius, size, size);
				bp.setRoi( roi );
				final int[] histogram = bp.getHistogram(); // Get histogram from the ROI

				double total = 0;
				for (int k = min ; k <= max; k++ )
					total +=histogram[ k ];

				double entropy = 0;
				for (int k = min ; k < max ; k++ )
				{
					if (histogram[k]>0)
					{
						double p = histogram[k]/total; // calculate p
						entropy += -p * Math.log(p)/log2;
					}
				}

				//entropy should be a value between 0.0 and 8.0, so scale to fit byte range of 0-255
				entropy = Math.floor(entropy * ((double)(numBins)/8.0));

				result[width*y] = (byte) (0xff & ((int) entropy));
			}
		}


		byte[][] resultArray = new byte[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		if(calculated!=null && calculated.containsKey(this))
			return calculated.get(this);

		pixels = Arrays.copyOf(pixels, pixels.length);

		final int numBins = 1 << 16;
		final double log2 = Math.log(2.0);

		final int min, max;
		{
			int tempMin = Integer.MAX_VALUE;
			int tempMax = Integer.MIN_VALUE;

			for (short s : pixels)
			{
				int value = 0xffff & s;
				if (value < tempMin)
					tempMin = value;
				else if (value > tempMax)
					tempMax = value;
			}

			min = tempMin;
			max = tempMax;
		}

		final ShortProcessor shortProcessor = new ShortProcessor(width, height, pixels, null);
		shortProcessor.setHistogramRange( 0, numBins-1 );
		shortProcessor.setHistogramSize( numBins);

		short[] result = new short[width*height];

		final int size = 2 * radius + 1;

		for(int x=0; x<width; x++)
		{
			for(int y=0; y<height; y++)
			{
				final OvalRoi roi = new OvalRoi(x-radius, y-radius, size, size);
				shortProcessor.setRoi( roi );
				final int[] histogram = shortProcessor.getHistogram(); // Get histogram from the ROI

				double total = 0;
				for (int k = min ; k <= max; k++ )
					total +=histogram[ k ];

				double entropy = 0;
				for (int k = min ; k <= max; k++ )
				{
					if (histogram[k]>0)
					{
						double p = histogram[k]/total; // calculate p
						entropy += -p * Math.log(p)/log2;
					}
				}

				//entropy should be a value between 0.0 and 16, so scale to fit short range of 0-2^16-1
				entropy = Math.floor(entropy * ((double)(numBins)/16.0));

				result[width*y+x] = (short) (0xffff & ((int) entropy));
			}
		}


		short[][] resultArray = new short[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
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
	public String getName()
	{
		return "Entropy";
	}

	@Override
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new Entropy(radius);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Entropy that = (Entropy) o;

		if (radius != that.radius) return false;

		return true;
	}

	@Override
	public int hashCode()
	{
		return radius;
	}
}