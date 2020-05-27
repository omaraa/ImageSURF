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
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
		void writeRow(int y, double[][] row);
	}

	protected interface Calculator {
		int[] calculate(PixelWindow pixelWindow);
	}

	abstract protected Calculator getCalculator(final PixelReader reader);

	@Override
	public FeatureCalculator[] getDependencies()
	{
		return new FeatureCalculator[0];
	}

	@Override
	public byte[][] calculate(final byte[] pixels, final int width, final int height, final Map<FeatureCalculator, byte[][]> calculated) {
		return calculateMultiple(pixels, new NeighbourhoodHistogramCalculator[] {this}, width, height, calculated);
	}

	@Override
	public short[][] calculate(short[] pixels, int width, int height, Map<FeatureCalculator, short[][]> calculated) {
		return calculateMultiple(pixels, new NeighbourhoodHistogramCalculator[] {this}, width, height, calculated);
	}

	protected void calculate(final PixelReader reader, final PixelWriter writer, final int width, final int height, int nBins) {
		calculateMultiple(reader,
				new NeighbourhoodHistogramCalculator[] { this },
				new PixelWriter[] { writer },
				width,
				height
		);
	}

	private static void calculateMultiple(final PixelReader reader,
										  final NeighbourhoodHistogramCalculator[] features,
										  final PixelWriter[] writers,
										  final int width,
										  final int height) {

		final int radius = features[0].radius;
		final Mask mask = Mask.get(radius);
		final int maskOffset = -radius;

		final ExecutorService threadPool = ImageSurfEnvironment.getFeatureExecutor();

		final Histogram histogramPrototype = new Histogram(reader);
		final HistogramPool histogramPool= new HistogramPool(ImageSurfEnvironment.getNumThreads(), histogramPrototype);

		try {
			threadPool.submit(() ->
					IntStream.range(0, height)
							.parallel()
							.forEach(y -> {

								final Histogram histogram;
								try {
									histogram = histogramPool.borrowObject();
								} catch (Exception e) {
									throw new RuntimeException(e);
								}

								final Calculator[] calculators = Arrays.stream(features)
										.map( f -> f.getCalculator(reader))
										.toArray(Calculator[]::new);
								final int nCalculators = calculators.length;

								PixelWindow pixelWindow = PixelWindow.get(reader, width, height, mask, maskOffset, y, histogram);
								final double[][][] rowOutput = Arrays.stream(features)
										.map( (f) -> new double[f.getNumImagesReturned()][width])
										.toArray(double[][][]::new);

								for (int x = 0; x < width; x++) {
									for (int c = 0; c < nCalculators; c++) {
										final int[] values = calculators[c].calculate(pixelWindow);
										for(int v = 0; v < values.length; v++)
											rowOutput[c][v][x] = values[v];
									}
									pixelWindow.moveWindow();
								}

								for (int c = 0; c < nCalculators; c++)
									synchronized (writers[c]) {
										writers[c].writeRow(y, rowOutput[c]);
									}

								histogramPool.returnObject(histogram);
							})
			).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		};
	}

	private static class HistogramPool extends GenericObjectPool<Histogram> {

		HistogramPool( int size, Histogram prototype) {
			super(new BasePooledObjectFactory<Histogram>() {
				@Override
				public Histogram create() throws Exception {
					return prototype.copy();
				}

				@Override
				public PooledObject<Histogram> wrap(Histogram histogram) {
					return new DefaultPooledObject<Histogram>(histogram);
				}
			}, config(size));

			try {
				addObjects(size);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		static GenericObjectPoolConfig<Histogram> config(int size) {
			final GenericObjectPoolConfig<Histogram> config = new GenericObjectPoolConfig<>();
			config.setMaxIdle(size);
			config.setMaxTotal(size);

			return config;
		}
	}

	public static byte[][] calculateMultiple(byte[] pixels,
											 NeighbourhoodHistogramCalculator[] features,
											 int width,
											 int height,
											 Map<FeatureCalculator, byte[][]> calculated) {
		if(features.length < 1)
			throw new RuntimeException("Features array must contain at least 1 calculator");

		if(Arrays.stream(features).mapToInt( f -> f.radius).distinct().count() != 1)
			throw new RuntimeException("Cannot calculate multiple features with differing radius");

		final PixelReader reader = bytePixelReader(pixels, width, height);

		final byte[][][] results = Arrays.stream(features)
				.map( (f) -> new byte[f.getNumImagesReturned()][width * height])
				.toArray(byte[][][]::new);
		final PixelWriter[] writers = Arrays.stream(results)
				.map((r) -> bytePixelWriter(width, r)).toArray(PixelWriter[]::new);

		calculateMultiple(reader, features, writers, width, height);

		for(int i = 0; i < features.length; i++)
			calculated.put(features[i], results[i]);

		final int numOutImages = Arrays.stream(results).mapToInt( b -> b.length).sum();
		final byte[][] out = new byte[numOutImages][];
		int c = 0;
		for(byte[][] bytes : results)
			for(byte[] b : bytes)
				out[c++] = b;

			return out;
	}

	public static short[][] calculateMultiple(short[] pixels,
											  NeighbourhoodHistogramCalculator[] features,
											  int width,
											  int height,
											  Map<FeatureCalculator, short[][]> calculated) {
		if(features.length < 1)
			throw new RuntimeException("Features array must contain at least 1 calculator");

		if(Arrays.stream(features).mapToInt( f -> f.radius).distinct().count() != 1)
			throw new RuntimeException("Cannot calculate multiple features with differing radius");

		final PixelReader reader = shortPixelReader(pixels, width, height);

		final short[][][] results = Arrays.stream(features)
				.map( (f) -> new short[f.getNumImagesReturned()][width * height])
				.toArray(short[][][]::new);
		final PixelWriter[] writers = Arrays.stream(results)
				.map((r) -> shortPixelWriter(width, r)).toArray(PixelWriter[]::new);

		calculateMultiple(reader, features, writers, width, height);

		for(int i = 0; i < features.length; i++)
			calculated.put(features[i], results[i]);

		final int numOutImages = Arrays.stream(results).mapToInt( s -> s.length).sum();
		final short[][] out = new short[numOutImages][];
		int c = 0;
		for(short[][] shorts : results)
			for(short[] s : shorts)
				out[c++] = s;

		return out;
	}

	@NotNull
	private static PixelWriter bytePixelWriter(int width, byte[][] result) {
		return (y, row) -> {
			for(int v = 0; v < result.length; v ++) {
				final byte[] rowOutput = new byte[width];
				for(int i = 0; i < rowOutput.length; i++)
					rowOutput[i] = (byte) row[v][i];

				System.arraycopy(rowOutput, 0, result[v], y*width, width);
			}
		};
	}

	@NotNull
	private static PixelReader bytePixelReader(byte[] pixels, int width, int height) {
		return new PixelReader() {
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

			@Override
			public int maxValue() {
				return 255;
			}
		};
	}

	@NotNull
	private static PixelWriter shortPixelWriter(int width, short[][] result) {
		return (y, row) -> {
			for(int v = 0; v < result.length; v ++) {
				final short[] rowOutput = new short[width];
				for(int i = 0; i < rowOutput.length; i++)
					rowOutput[i] = (short) row[v][i];

				System.arraycopy(rowOutput, 0, result[v], y*width, width);
			}
		};
	}

	@NotNull
	private static PixelReader shortPixelReader(short[] pixels, int width, int height) {
		return new PixelReader() {
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

			@Override
			public int maxValue() {
				return 65535;
			}
		};
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
	public Enumeration<String> getTagNames()
	{
		return tags.keys();
	}

	@Override
	public void removeTag(String tagName)
	{
		tags.remove(tagName);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if(!this.getClass().equals(o.getClass()))
			return false;

		if (!(o instanceof NeighbourhoodHistogramCalculator)) return false;
		NeighbourhoodHistogramCalculator that = (NeighbourhoodHistogramCalculator) o;
		return getRadius() == that.getRadius() &&
				getTags().equals(that.getTags());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getClass(),getRadius(), getTags());
	}

	@Override
	public boolean preferCaching() {
		return true;
	}
}