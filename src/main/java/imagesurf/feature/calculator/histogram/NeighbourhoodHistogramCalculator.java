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

package imagesurf.feature.calculator.histogram;

import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.util.ImageSurfEnvironment;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

abstract public class NeighbourhoodHistogramCalculator implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private int radius;

	public NeighbourhoodHistogramCalculator(int radius)
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

	protected interface PixelWriter {
		void writeRow(int y, double[] row);
	}

	protected interface Calculator {
		int calculate(PixelWindow pixelWindow);
	}

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

	@Override
	public byte[][] calculate(final byte[] pixels, final int width, final int height, final Map<FeatureCalculator, byte[][]> calculated)
	{
		final PixelReader reader = new PixelReader() {
			@Override
			public int get(int index) {
				return pixels[index] & 0xff;
			}

			@Override
			public int numBits() {
				return 8;
			}

			@Override
			public int numPixels() {
				return width * height;
			}
		};

		final byte[] result = new byte[width*height];

		final PixelWriter writer = (y, row) -> {
			final byte[] rowOutput = new byte[width];
			for(int i= 0; i < width; i++)
				rowOutput[i] = (byte) row[i];

			System.arraycopy(rowOutput, 0, result, y*width, width);
		};

		calculate(reader, writer, width, height, 256);

		byte[][] resultArray = new byte[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;
	}

	abstract protected Calculator getCalculator(final PixelReader reader, final int numBins);

	protected void calculate(final PixelReader reader, final PixelWriter writer, final int width, final int height, int nBins) {
		final Calculator calculator = getCalculator(reader, nBins);

		final Mask mask = Mask.get(radius);
		final int maskOffset = -radius;

		ExecutorService threadPool = ImageSurfEnvironment.getFeatureExecutor();

		try {
			threadPool.submit(() ->
					IntStream.range(0, height)
							.parallel()
							.forEach(y -> {
						PixelWindow pixelWindow = PixelWindow.get(reader, width, height, mask, maskOffset, y);
						final double[] rowOutput = new double[width];

						for (int x = 0; x < width; x++) {
							rowOutput[x] = calculator.calculate(pixelWindow);
							pixelWindow.moveWindow();
						}

						synchronized (writer) {
							writer.writeRow(y, rowOutput);
						}
					})
			).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated)
	{
		final PixelReader reader =new PixelReader() {
			@Override
			public int get(int index) {
				return pixels[index] & 0xffff;
			}

			@Override
			public int numBits() {
				return 16;
			}

			@Override
			public int numPixels() {
				return width * height;
			}
		};

		final short[] result = new short[width*height];

		final PixelWriter writer = (y, row) -> {
			final short[] rowOutput = new short[width];
			for(int i = 0; i < row.length; i++)
				rowOutput[i] = (short) row[i];

			System.arraycopy(rowOutput, 0, result, y*width, width);
		};

		calculate(reader, writer, width, height, 65536);

		short[][] resultArray = new short[][] {result};

		if(calculated!=null)
			calculated.put(this, resultArray);

		return resultArray;	}

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
	public String getDescription()
	{
		return getName() + " ("+getRadius() + ')';
	}

	protected final ConcurrentHashMap<String, Object> tags = new ConcurrentHashMap<>();

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
}