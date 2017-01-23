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

import ij.CompositeImage;
import ij.ImagePlus;
import ij.Prefs;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.feature.calculator.Identity;
import net.mintern.primitive.Primitive;
import imagesurf.util.Utility;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ImageFeatures implements Serializable
{
	static final long serialVersionUID = 42L;
	private static final double SAVE_THRESHOLD = 250d / (2000 * 2000); //250ms for a 2000 * 2000 image

	public final PixelType pixelType;

	public final String title;

	public final int width;
	public final int height;
	public final int numChannels;
	public final int numSlices;
	public final int numFrames;
	public final int pixelsPerChannel;
	public final int pixelsPerSlice;
	public final int pixelsPerFrame;

	private final Object pixels;
	private final Map<FeatureCalculator, Object>[] features;
	private boolean verbose = true;

	transient private static final Map<FeatureCalculator, Collection<Double>> computationTimes = new ConcurrentHashMap<>();

	public interface ProgressListener
	{
		void onProgress(int current, int max, String message);
	}

	private final Collection<ProgressListener> progressListeners = new HashSet<>();

	public boolean addProgressListener(ProgressListener progressListener)
	{
		return progressListeners.add(progressListener);
	}

	public boolean removeProgressListener(ProgressListener progressListener)
	{
		return progressListeners.remove(progressListener);
	}

	private void onProgress(int current, int max, String message)
	{
		for(ProgressListener p : progressListeners)
			p.onProgress(current, max, message);
	}

	public ImageFeatures(ImagePlus imagePlus)
	{
		this(getImagePlusPixels(imagePlus),
				getPixelType(imagePlus),
				imagePlus.getWidth(),
				imagePlus.getHeight(),
				imagePlus.getNChannels(),
				imagePlus.getNSlices(),
				imagePlus.getNFrames(),
				imagePlus.getTitle()
		);
	}

	private ImageFeatures(ImageFeatures i)
	{
		this.width = i.width;
		this.height = i.height;
		this.numChannels = i.numChannels;
		this.numSlices = i.numSlices;
		this.numFrames = i.numFrames;
		this.pixelsPerChannel = i.pixelsPerChannel;
		this.pixelsPerSlice = i.pixelsPerSlice;
		this.pixelsPerFrame = i.pixelsPerFrame;
		this.pixels = i.pixels;
		this.pixelType = i.pixelType;
		this.title = i.title;

		this.features = new Map[numChannels*numFrames*numSlices];

		int featureIndex = 0;
		for(int c = 0; c < numChannels; c++)
			for(int z = 0; z < numSlices; z++)
				for(int t = 0; t < numFrames; t++)
				{
					features[featureIndex] = new ConcurrentHashMap<>();
					features[featureIndex].putAll(i.features[featureIndex]);

					featureIndex++;
				}
	}

	private ImageFeatures(final Object pixels, final PixelType pixelType, final int width, final int height, final int numChannels, final int numSlices, final int numFrames, String title)
	{
		this.width = width;
		this.height = height;
		this.numChannels = numChannels;
		this.numSlices = numSlices;
		this.numFrames = numFrames;
		this.pixelsPerChannel = width * height;
		this.pixelsPerSlice = pixelsPerChannel * numChannels;
		this.pixelsPerFrame = pixelsPerSlice * numFrames;

		if(width<0 || height <0 || numChannels<0 || numSlices<0 || numFrames<0)
			throw new IllegalArgumentException("Image dimensions must be positive values");

		{
			final int numPixels;
			switch (pixelType)
			{
				case GRAY_8_BIT:
					numPixels = ((byte[]) pixels).length;
					break;
				case GRAY_16_BIT:
					numPixels = ((short[]) pixels).length;
					break;
				default:
					numPixels = -1;
			}

			if(numPixels!=pixelsPerFrame*numFrames)
				throw new IllegalArgumentException("Number of pixels must be exactly (frames * slices * channels * width * height pixels) long. Actual=" + numPixels + " Required=" + (pixelsPerFrame * numFrames));
		}

		this.pixels = pixels;
		this.pixelType = pixelType;
		this.title = title;

		features = new Map[numChannels*numFrames*numSlices];
		int i = 0;
		for(int c = 0; c < numChannels; c++)
			for(int z = 0; z < numSlices; z++)
				for(int t = 0; t < numFrames; t++)
				{
					features[i] = new ConcurrentHashMap<>();

					switch (pixelType)
					{
						case GRAY_8_BIT:
							features[i].put(Identity.get(), new byte[][] {(byte[]) getPixels(c, z, t)});
							break;
						case GRAY_16_BIT:
							features[i].put(Identity.get(), new short[][] {(short[]) getPixels(c, z, t)});
							break;
						default:
							throw new RuntimeException("Pixel type "+pixelType+" not supported");
					}

					i++;
				}
	}

	public Object getPixels(int c, int z, int t)
	{
		final Object channelPixels;
		switch (pixelType)
		{
			case GRAY_8_BIT:
				channelPixels = new byte[pixelsPerChannel];
				break;
			case GRAY_16_BIT:
				channelPixels = new short[pixelsPerChannel];
				break;
			default:
				throw new RuntimeException("Pixel type "+pixelType+" not supported.");
		}

		int offset = (c * pixelsPerChannel) + (z * pixelsPerSlice) + (t * pixelsPerFrame);
		System.arraycopy(pixels, offset, channelPixels, 0, pixelsPerChannel);

		return channelPixels;
	}

	private int getFeatureIndex(int c, int z, int t)
	{
		return c + (z * numChannels) + (t * numChannels * numSlices);
	}

	public Object getFeaturePixels(int c, int z, int t, FeatureCalculator feature)
	{

		Map<FeatureCalculator, Object> featureCache = features[getFeatureIndex(c, z, t)];

		if(!featureCache.containsKey(feature))
		{
			long startTime = System.currentTimeMillis();
			featureCache.put(feature, feature.calculate(getPixels(c, z, t), width, height, featureCache));
			recordComputationTime(feature, System.currentTimeMillis() - startTime, pixelsPerChannel*numChannels);
		}

		return featureCache.get(feature);

	}

	public FeatureReader getReader(int c, int z, int t, FeatureCalculator[] features) throws ExecutionException, InterruptedException
	{
		int[] classes = new int[pixelsPerChannel];
		Arrays.fill(classes, -1);

		return getReader(c, z, t, features, classes);
	}

	public FeatureReader getReader(int c, int z, int t, FeatureCalculator[] features, int[] classes) throws ExecutionException, InterruptedException
	{
		if(classes.length!=pixelsPerChannel)
			throw new IllegalArgumentException("Classes array does not match slice size. Expected size: "+
					pixelsPerChannel+" Actual size: "+classes.length);

		int numFeatureImages = 0;
		for(FeatureCalculator f : features)
			numFeatureImages+= f.getNumImagesReturned();

		calculateFeatures(c, z, t, features);

		final int classIndex = features.length;
		final Object[] featurePixels;
		switch (pixelType){
			case GRAY_8_BIT:
				featurePixels = new byte[numFeatureImages +1][];
				break;
			case GRAY_16_BIT:
				featurePixels = new short[numFeatureImages +1][];
				break;
			default:
				throw new RuntimeException("Pixel type "+pixelType+" not supported.");
		}

		int currentFeatureImage = 0;
		for(FeatureCalculator featureCalculator : features)
		{
			for(Object featureImage : (Object[]) getFeaturePixels(c, z, t, featureCalculator))
				featurePixels[currentFeatureImage++] = featureImage;
		}


		switch (pixelType)
		{
			case GRAY_8_BIT:
				byte[] byteClasses = new byte[classes.length];
				for(int i=0;i<classes.length;i++)
					byteClasses[i] = (byte) classes[i];

				featurePixels[classIndex] = byteClasses;
				return new ByteReader((byte[][]) featurePixels, classIndex);
			case GRAY_16_BIT:
				short[] shortClasses = new short[classes.length];
				for(int i=0;i<classes.length;i++)
					shortClasses[i] = (short) classes[i];

				featurePixels[classIndex] = shortClasses;
				return new ShortReader((short[][]) featurePixels, classIndex);
			default:
				throw new RuntimeException("Unsupported pixel type: "+pixelType);
		}
	}

	public Collection<FeatureCalculator> getFeatures()
	{
		Set<FeatureCalculator> featureSet = new HashSet<>();
		for(Map<FeatureCalculator, Object> featureCache : features)
			featureSet.addAll(featureCache.keySet());

		List<FeatureCalculator> featureList = new ArrayList<>(featureSet);
		Collections.sort(featureList, (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getDescription(), o2.getDescription()));

		return featureList;
	}

	private static Object getImagePlusPixels(final ImagePlus image)
	{
		final ImagePlus compositeImage = new CompositeImage(image);

		final int[] dimensions = image.getDimensions();
		int width = dimensions[0];
		int height = dimensions[1];
		int numChannels = Utility.isGrayScale(image) ? 1 : dimensions[2];
		int numSlices = dimensions[3];
		int numFrames = dimensions[4];
		int pixelsPerChannel = width * height;
		int pixelsPerSlice = pixelsPerChannel * numChannels;
		int pixelsPerFrame = pixelsPerSlice * numSlices;

		switch (getPixelType(image))
		{
			case GRAY_8_BIT:
			{
				byte[] imagePixels = new byte[pixelsPerFrame * numFrames];

				for (int currentT = 0; currentT < numFrames; currentT++)
				{
					compositeImage.setT(currentT);
					for (int currentZ = 0; currentZ < numSlices; currentZ++)
					{
						compositeImage.setZ(currentZ + 1);

						for (int currentC = 0; currentC < numChannels; currentC++)
						{
							int offset = (currentT * pixelsPerFrame + currentZ * pixelsPerSlice + currentC * pixelsPerChannel);
							compositeImage.setC(currentC + 1);

							ByteProcessor bp = (ByteProcessor) compositeImage.getChannelProcessor().convertToByte(false);
							System.arraycopy(bp.getPixels(), 0, imagePixels, offset, pixelsPerChannel);
						}
					}
				}
				return imagePixels;
			}

			case GRAY_16_BIT:
			{
				short[] imagePixels = new short[pixelsPerFrame * numFrames];

				for (int currentT = 0; currentT < numFrames; currentT++)
				{
					compositeImage.setT(currentT);
					for (int currentZ = 0; currentZ < numSlices; currentZ++)
					{
						compositeImage.setZ(currentZ + 1);

						for (int currentC = 0; currentC < numChannels; currentC++)
						{
							int offset = (currentT * pixelsPerFrame + currentZ * pixelsPerSlice + currentC * pixelsPerChannel);
							compositeImage.setC(currentC + 1);

							ShortProcessor sp = (ShortProcessor) compositeImage.getChannelProcessor().convertToShort(false);
							System.arraycopy(sp.getPixels(), 0, imagePixels, offset, pixelsPerChannel);
						}
					}
				}
				return imagePixels;
			}

			default:
				throw new IllegalArgumentException("Image type not supported.");
		}

	}

	private static PixelType getPixelType(ImagePlus imagePlus)
	{
		switch (imagePlus.getType())
		{
			case ImagePlus.GRAY8:
				return PixelType.GRAY_8_BIT;

			case ImagePlus.GRAY16:
				return PixelType.GRAY_16_BIT;

			case ImagePlus.GRAY32:
				throw new IllegalArgumentException("32-bit grayscale images are not yet supported.");

			case ImagePlus.COLOR_256:
			case ImagePlus.COLOR_RGB:
				throw new IllegalArgumentException("Color images are not yet supported.");

			default:
				throw new IllegalArgumentException("Image type not supported.");
		}
	}

	public boolean calculateFeatures(int c, int z, int t, FeatureCalculator[] features) throws ExecutionException, InterruptedException
	{
		ExecutorService executorService = Executors.newFixedThreadPool(Prefs.getThreads());
		try
		{
			return calculateFeatures(c, z, t, features, executorService);
		}
		finally
		{
			executorService.shutdown();
		}
	}

	/**
	 *
	 * @param c
	 * @param z
	 * @param t
	 * @param features
	 * @param executorService
	 * @return true if any features were calculated, otherwise false
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public boolean calculateFeatures(int c, int z, int t, FeatureCalculator[] features, ExecutorService executorService) throws ExecutionException, InterruptedException
	{
		List<Future> waitFutures = new ArrayList<Future>();

		final Map<FeatureCalculator, Object> featureCache = this.features[getFeatureIndex(c, z, t)];
		final FeatureCalculator[] featuresToCalculate = Arrays.stream(features)
				.filter(f -> !featureCache.containsKey(f))
				.toArray(FeatureCalculator[]::new);

		if(featuresToCalculate.length == 0)
			return false;

		final Object monitor = featureCache;

		final Object imagePixels = getPixels(c, z, t);

		final List<FeatureCalculator> processingFeatureCalculators = new Vector<FeatureCalculator>();
		final List<FeatureCalculator> remainingFeatureCalculators = new Vector<FeatureCalculator>(Arrays.asList(featuresToCalculate));

		long start = System.currentTimeMillis();

		while (!remainingFeatureCalculators.isEmpty() || !processingFeatureCalculators.isEmpty())
		{
			final List<FeatureCalculator> toAdd = new ArrayList<FeatureCalculator>();

			//Add imagesurf.feature calculators with no dependencies or dependencies already calculated to the list
			for (FeatureCalculator featureCalculator : remainingFeatureCalculators)
			{
				if (featureCalculator.getDependencies().length == 0)
				{
					toAdd.add(featureCalculator);
				}
				else
				{
					boolean dependenciesProcessing = false;
					for (FeatureCalculator dependency : featureCalculator.getDependencies())
					{
						if (processingFeatureCalculators.contains(dependency) || toAdd.contains(dependency))
						{
							dependenciesProcessing = true;
							break;
						}
					}

					if (!dependenciesProcessing)
						toAdd.add(featureCalculator);
				}
			}

			for (final FeatureCalculator featureCalculator : new ArrayList<FeatureCalculator>(toAdd))
			{
				remainingFeatureCalculators.remove(featureCalculator);
				processingFeatureCalculators.add(featureCalculator);
				toAdd.remove(featureCalculator);

				Runnable runnable = new Runnable()
				{
					@Override
					public void run()
					{
						long start = System.currentTimeMillis();
						//Feature image is added to cache upon completion

						featureCalculator.calculate(imagePixels, width, height, featureCache);

						processingFeatureCalculators.remove(featureCalculator);

						long computationTime = System.currentTimeMillis() - start;
						synchronized (monitor)
						{
							recordComputationTime(featureCalculator, computationTime, pixelsPerChannel*numChannels);
							int numRemaining = processingFeatureCalculators.size() + remainingFeatureCalculators.size();

							if(verbose)
								System.out.println("Calculated imagesurf.feature "+(featuresToCalculate.length - numRemaining)+"/"+featuresToCalculate.length+" for " + title + ": " + featureCalculator.getDescription() + " in " + (System.currentTimeMillis() - start) + "ms. [" + numRemaining + " remaining]");

							onProgress(featuresToCalculate.length - numRemaining, featuresToCalculate.length, "Calculated imagesurf.feature "+title);

							monitor.notifyAll();
						}
					}
				};

				executorService.execute(runnable);
			}

			if (!processingFeatureCalculators.isEmpty() || !remainingFeatureCalculators.isEmpty() )
			{
				synchronized (monitor)
				{
					monitor.wait(1000);
				}
			}
		}

		if(verbose)
			System.out.println("Calculated all features for " + title + " in " + (System.currentTimeMillis() - start) + "ms.");

		for (Future future : waitFutures)
		{
			future.get();
			if (future.isCancelled())
				throw new RuntimeException("Concurrent calculation of image derivatives failed.");
		}

		return true;
	}

	public void clearFeatureCache(int c, int t, int z)
	{
		final Map<FeatureCalculator, Object> featureCache = this.features[getFeatureIndex(c, z, t)];

		Object identity = featureCache.get(Identity.get());

		featureCache.clear();
		featureCache.put(Identity.get(), identity);
	}



	public void serialize(Path path) throws Exception
	{
		ImageFeatures toSerialize = new ImageFeatures(this);
		toSerialize.removeEasilyComputedFeatures();


		Utility.serializeObject(toSerialize, path.toFile(), false);
	}

	private void removeEasilyComputedFeatures()
	{
		Collection<FeatureCalculator> toRemove = getFeatures();
		Collection<FeatureCalculator> toSave = toRemove.stream()
				.filter(f -> f.getDependencies().length == 0)
				.collect(Collectors.toCollection(HashSet::new));

		toRemove.removeAll(toSave);

		for(FeatureCalculator f : toRemove)
		{
			boolean dependenciesMet = true;
			for(FeatureCalculator d : f.getDependencies())
				if(!toSave.contains(d) && ! toRemove.contains(d))
				{
					dependenciesMet = false;
					break;
				}

			if(!dependenciesMet)
				toSave.add(f);
		}

		toRemove.removeAll(toSave);

		Collection<FeatureCalculator> quickToCompute = toSave.stream()
				.filter(f -> (getAverageComputationTime(f) < SAVE_THRESHOLD))
				.collect(Collectors.toCollection(ArrayList::new));

		toRemove.addAll(quickToCompute);
		toSave.removeAll(toRemove);

		toRemove.remove(Identity.get());
		toSave.add(Identity.get());

		for(Map<FeatureCalculator, Object> featureCache : features)
			for(FeatureCalculator f : toRemove)
				if(featureCache.containsKey(f))
					featureCache.remove(f);
	}

	public static ImageFeatures deserialize(Path path) throws Exception
	{
		return (ImageFeatures) Utility.deserializeObject(path.toFile(), false);
	}

	public static class ByteReader implements FeatureReader
	{
		private static final int BIT_MASK = 0xff;
		private final int classIndex;
		private final byte[][] values;

		public ByteReader(byte[][] featurePixels, int classIndex)
		{
			this.values = featurePixels;
			this.classIndex = classIndex;
		}

		@Override
		public int getClassValue(final int instanceIndex)
		{
			return values[classIndex][instanceIndex] & BIT_MASK;
		}

		@Override
		public double getValue(final int instanceIndex, final int attributeIndex)
		{
			return values[attributeIndex][instanceIndex] & BIT_MASK;
		}

		@Override
		public int[] getSortedIndices(final int attributeIndex, final int[] instanceIndices)
		{
			final byte[] attributeArray = values[attributeIndex];
			final int[] sortedIndices = Arrays.copyOf(instanceIndices, instanceIndices.length);

			Primitive.sort(sortedIndices, (i1, i2) -> Integer.compare((attributeArray[i1] & BIT_MASK), (attributeArray[i2] & BIT_MASK)));

			return sortedIndices;
		}

		@Override
		public int getNumInstances()
		{
			return values[classIndex].length;
		}

		@Override
		public int getNumFeatures()
		{
			return values.length;
		}

		@Override
		public int getClassIndex()
		{
			return classIndex;
		}

		public byte[][] getValues()
		{
			return values;
		}
	}

	public static class ShortReader implements FeatureReader
	{
		private static final int BIT_MASK = 0xffff;
		private final int classIndex;
		private final short[][] values;

		public ShortReader(short[][] featurePixels, int classIndex)
		{
			this.values = featurePixels;
			this.classIndex = classIndex;
		}

		@Override
		public int getClassValue(final int instanceIndex)
		{
			return values[classIndex][instanceIndex] & BIT_MASK;
		}

		@Override
		public double getValue(final int instanceIndex, final int attributeIndex)
		{
			return values[attributeIndex][instanceIndex] & BIT_MASK;
		}

		@Override
		public int[] getSortedIndices(final int attributeIndex, final int[] instanceIndices)
		{
			final short[] attributeArray = values[attributeIndex];
			final int[] sortedIndices = Arrays.copyOf(instanceIndices, instanceIndices.length);

			Primitive.sort(sortedIndices, (i1, i2) -> Integer.compare((attributeArray[i1] & BIT_MASK), (attributeArray[i2] & BIT_MASK)));

			return sortedIndices;
		}

		@Override
		public int getNumInstances()
		{
			return values[classIndex].length;
		}

		@Override
		public int getNumFeatures()
		{
			return values.length;
		}

		@Override
		public int getClassIndex()
		{
			return classIndex;
		}

		public short[][] getValues()
		{
			return values;
		}
	}

	private static void recordComputationTime(FeatureCalculator featureCalculator, long time, int numPixels)
	{
		if(!computationTimes.containsKey(featureCalculator))
		{
			Vector<Double> times = new Vector<>();
			times.add(((double)time)/numPixels);
			computationTimes.put(featureCalculator, times);
		}
		else
		{
			computationTimes.get(featureCalculator).add(((double)time)/numPixels);
		}
	}

	private static double getAverageComputationTime(FeatureCalculator featureCalculator)
	{
		Collection<Double> times = computationTimes.getOrDefault(featureCalculator, null);
		if(times == null)
			return Double.MAX_VALUE;

		return times.stream().count()/times.size();
	}
}
