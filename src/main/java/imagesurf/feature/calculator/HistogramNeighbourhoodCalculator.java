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

import ij.Prefs;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

abstract public class HistogramNeighbourhoodCalculator implements FeatureCalculator, Serializable
{
	static final long serialVersionUID = 42L;

	private int radius;

	public HistogramNeighbourhoodCalculator(int radius)
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

	protected interface PixelReader {
		int get(int index);
		int numPixels();
		int numBits();
	}

	protected interface PixelWriter {
		void writeRow(int y, double[] row);
	}

	protected interface Calculator {
		int calculate(int[] histogram);
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

		final CircleRow[] mask = getMaskRows(radius);
		final int maskOffset = -radius;

		final ForkJoinPool threadPool = new ForkJoinPool(Prefs.getThreads());

		try {
			threadPool.submit(() ->
					IntStream.range(0, height).parallel().forEach( y -> {
						int[] rowHistogram = getHistogram(reader, width, height, nBins, mask, maskOffset, y);
						final double[] rowOutput = new double[width];
						for(int x = 0; x < width; x++ )
						{
							final double entropy = calculator.calculate(rowHistogram);
							rowOutput[x] = entropy;
							rowHistogram = moveWindow(reader, width, height, rowHistogram, mask, maskOffset, y, x);
						}

						synchronized (writer) {writer.writeRow(y, rowOutput);}
					})).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private int[] moveWindow(PixelReader reader, int width, int height, int[] histogram, CircleRow[] mask, int maskOffset, int y, int x) {
		final int[] moved = Arrays.copyOf(histogram, histogram.length);

		for(int i = 0; i < mask.length; i++) {
			final int currentY = y + i + maskOffset;
			if (currentY >= 0 && currentY < height) {

				final int oldX = x + maskOffset + mask[i].offset;
				final int newX = oldX + mask[i].width;

				if (oldX >= 0 && oldX < width) {
					final int oldValue = reader.get(to1d(oldX, currentY, width));
					moved[oldValue]--;
				}

				if (newX >= 0 && newX < width) {
					final int newValue = reader.get(to1d(newX, currentY, width));

					moved[newValue]++;
				}
			}
		}

		return moved;
	}

	private int[] getHistogram(PixelReader reader, int width, int height, int numBins, CircleRow[] mask, int maskOffset, int y) {

		int[] histogram = new int[numBins];
		for(int i = 0; i < mask.length; i++)
		{
			final int currentY = y + i + maskOffset;
			if(currentY < 0 || currentY >= height)
				continue;

			for(int j = 0; j < mask[i].width; j++) {
				final int x = j + maskOffset + mask[i].offset;
				if(x < width && x >= 0) {
					final int value = reader.get(to1d(x, currentY, width));
					histogram[value]++;
				}
			}
		}

		return histogram;
	}

	private final static int to1d(int x, int y, int width) {
		return y*width + x;
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

	private static class CircleRow {
		private final int offset;
		private final int width;

		private CircleRow(int offset, int width) {
			this.offset = offset;
			this.width = width;
		}
	}

	private static CircleRow[] getMaskRows(final int radius)
	{
		final boolean[][] mask = getCircle(radius);

		return Arrays.stream(mask).map((row) -> {
			int offset = -1;
			int width = 0;

			for(int i=0;i<row.length;i++) {
				if (row[i]) {
						offset = i;
						break;
					}
				}

			for(int i = row.length-1; i >= 0; i--) {
				if(row[i]) {
					width = i-offset+1;
					break;
				}
			}

			return new CircleRow(offset, width);
		}).collect(Collectors.toList()).toArray(new CircleRow[mask.length]);
	}

	private static boolean[][] getCircle(final int radius)
	{
		final int diameter = radius*2+1;
		final boolean[][] mask = new boolean[diameter][diameter];

		int d = (5 - radius * 4)/4;
		int x = 0;
		int y = radius;

		do {
			mask[radius + x][radius + y] = true;
			mask[radius + x][radius - y] = true;
			mask[radius - x][radius + y] = true;
			mask[radius - x][radius - y] = true;
			mask[radius + y][radius + x] = true;
			mask[radius + y][radius - x] = true;
			mask[radius - y][radius + x] = true;
			mask[radius - y][radius - x] = true;
			if (d < 0) {
				d += 2 * x + 1;
			} else {
				d += 2 * (x - y) + 1;
				y--;
			}
			x++;
		} while (x <= y);

		return mask;
	}
}