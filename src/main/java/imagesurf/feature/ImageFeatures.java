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
	public final int pixelsPerChannelSlice;
	public final int pixelsPerChannelFrame;

	public static final String FEATURE_TAG_CHANNEL_INDEX = "Channel";

	private final Object pixels;
	private final Map<FeatureCalculator, Object>[] features;
	public final int numMergedChannels;
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
				Utility.getPixelType(imagePlus),
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
		this.numMergedChannels = Utility.calculateNumMergedChannels(numChannels);
		this.numSlices = i.numSlices;
		this.numFrames = i.numFrames;
		this.pixelsPerChannel = i.pixelsPerChannel;
		this.pixelsPerChannelSlice = i.pixelsPerChannelSlice;
		this.pixelsPerChannelFrame = i.pixelsPerChannelFrame;
		this.pixels = i.pixels;
		this.pixelType = i.pixelType;
		this.title = i.title;

		this.features = new Map[numMergedChannels*numFrames*numSlices];

		for(int featureIndex = 0; featureIndex < this.features.length; featureIndex++)
		{
			features[featureIndex] = new ConcurrentHashMap<>();
			features[featureIndex].putAll(i.features[featureIndex]);
		}
	}

	private ImageFeatures(final Object pixels, final PixelType pixelType, final int width, final int height, final int numChannels, final int numSlices, final int numFrames, String title)
	{
		this.width = width;
		this.height = height;
		this.numChannels = numChannels;
		this.numMergedChannels = Utility.calculateNumMergedChannels(numChannels);
		this.numSlices = numSlices;
		this.numFrames = numFrames;
		this.pixelsPerChannel = width * height;
		this.pixelsPerChannelSlice = pixelsPerChannel * numChannels;
		this.pixelsPerChannelFrame = pixelsPerChannelSlice * numSlices;

		
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

			if(numPixels!=pixelsPerChannelFrame * numFrames)
				throw new IllegalArgumentException("Number of pixels must be exactly (frames * slices * channels * width * height pixels) long. Actual=" + numPixels + " Required=" + (pixelsPerChannelFrame * numFrames));
		}

		this.pixelType = pixelType;
		this.title = title;

		if(numChannels == 1)
		{
			this.pixels = pixels;
		}
		else
		{
			switch (pixelType)
			{

				case GRAY_8_BIT:
					this.pixels = mergeBytePixels(pixels, numChannels, numSlices, numFrames);

					break;
				case GRAY_16_BIT:
					this.pixels = mergeShortPixels(pixels, numChannels, numSlices, numFrames);
					break;

				default:
					throw new RuntimeException("Unsupported pixel type: "+pixelType);
			}
		}

		features = new Map[numMergedChannels*numFrames*numSlices];
		int i = 0;
		for(int mergedChannelIndex = 0; mergedChannelIndex < numMergedChannels; mergedChannelIndex++)
			for(int z = 0; z < numSlices; z++)
				for(int t = 0; t < numFrames; t++)
				{
					features[i] = new ConcurrentHashMap<>();

					FeatureCalculator channelIdentity = Identity.get();
					channelIdentity.setTag(FEATURE_TAG_CHANNEL_INDEX, mergedChannelIndex);

					final Object mergedChannelPixels;
					switch (pixelType)
					{
						case GRAY_8_BIT:
							mergedChannelPixels =  new byte[][] {(byte[]) getMergedChannelPixels(mergedChannelIndex, z, t)};
							break;
						case GRAY_16_BIT:
							mergedChannelPixels = new short[][] {(short[]) getMergedChannelPixels(mergedChannelIndex, z, t)};
							break;
						default:
							throw new RuntimeException("Pixel type "+pixelType+" not supported");
					}

					features[i].put(channelIdentity, mergedChannelPixels);

					i++;
				}
	}

	private byte[] mergeBytePixels(Object pixels, int numChannels, int numSlices, int numFrames)
	{
		byte[] mergedPixels = new byte[pixelsPerChannel * numMergedChannels];

		for(int channelMask = 1; channelMask <= numMergedChannels; channelMask++)
		{
			int numSelectedChannels = 0;
			boolean[] selectedChannels = new boolean[numChannels];
			for(int i = 0; i < numChannels; i++)
			{
				int mask = 1 << i;

				if((channelMask & (mask)) > 0)
				{
					selectedChannels[i] = true;
					numSelectedChannels++;
				}
			}

			for(int z = 0; z < numSlices; z++)
				for(int t = 0; t < numFrames; t++)
				{
					byte[][] selectedChannelPixels = new byte[numSelectedChannels][];
					{
						int currentChannel = 0;

						for (int c = 0; c < selectedChannels.length; c++)
						{
							if (selectedChannels[c])
							{
								selectedChannelPixels[currentChannel] = new byte[pixelsPerChannel];

								int offset = (pixelsPerChannel * c) + (z * pixelsPerChannelSlice) + (t * pixelsPerChannelFrame);
								System.arraycopy(pixels, offset, selectedChannelPixels[currentChannel], 0, pixelsPerChannel);

								currentChannel++;
							}
						}
					}

					byte[] mergedChannelPixels = new byte[pixelsPerChannel];
					for(int pixelIndex = 0; pixelIndex < pixelsPerChannel; pixelIndex++)
					{
						long pixelValue = 0;

						int currentSelectedChannel = 0;
						for(int i = 0; i < numChannels; i ++)
						{
							if(selectedChannels[i])
								pixelValue+= 0xff & selectedChannelPixels[currentSelectedChannel++][pixelIndex];
						}

						//Scale pixel value so each channel has equal weight within original range
						double doublePixelValue = pixelValue / numSelectedChannels;
						mergedChannelPixels[pixelIndex] = (byte) (0xff & ((long) doublePixelValue));
					}

					int offset = ((channelMask - 1) * pixelsPerChannel) + (z * pixelsPerChannelSlice) + (t * pixelsPerChannelFrame);
					System.arraycopy(mergedChannelPixels, 0,  mergedPixels, offset, pixelsPerChannel);
				}
		}

		return mergedPixels;
	}

	private short[] mergeShortPixels(Object pixels, int numChannels, int numSlices, int numFrames)
	{
		short[] mergedPixels = new short[pixelsPerChannel * numMergedChannels];

		for(int channelMask = 1; channelMask <= numMergedChannels; channelMask++)
		{
			int numSelectedChannels = 0;
			boolean[] selectedChannels = new boolean[numChannels];
			for(int i = 0; i < numChannels; i++)
			{
				int mask = 1 << i;

				if((channelMask & (mask)) > 0)
				{
					selectedChannels[i] = true;
					numSelectedChannels++;
				}
			}

			for(int z = 0; z < numSlices; z++)
				for(int t = 0; t < numFrames; t++)
				{
					short[][] selectedChannelPixels = new short[numSelectedChannels][];
					{
						int currentChannel = 0;

						for (int c = 0; c < selectedChannels.length; c++)
						{
							if (selectedChannels[c])
							{
								selectedChannelPixels[currentChannel] = new short[pixelsPerChannel];

								int offset = (pixelsPerChannel * c) + (z * pixelsPerChannelSlice) + (t * pixelsPerChannelFrame);
								System.arraycopy(pixels, offset, selectedChannelPixels[currentChannel], 0, pixelsPerChannel);

								currentChannel++;
							}
						}
					}

					short[] mergedChannelPixels = new short[pixelsPerChannel];
					for(int pixelIndex = 0; pixelIndex < pixelsPerChannel; pixelIndex++)
					{
						long pixelValue = 0;

						int currentSelectedChannel = 0;
						for(int i = 0; i < numChannels; i ++)
						{
							if(selectedChannels[i])
								pixelValue+= 0xffff & selectedChannelPixels[currentSelectedChannel++][pixelIndex];
						}

						//Scale pixel value so each channel has equal weight within original range
						double doublePixelValue = pixelValue / numSelectedChannels;
						mergedChannelPixels[pixelIndex] = (short) (0xffff & ((long) doublePixelValue));
					}

					int offset = ((channelMask - 1) * pixelsPerChannel) + (z * pixelsPerChannelSlice) + (t * pixelsPerChannelFrame);
					System.arraycopy(mergedChannelPixels, 0,  mergedPixels, offset, pixelsPerChannel);
				}
		}

		return mergedPixels;
	}

	public Object getMergedChannelPixels(int channelsMask, int z, int t)
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

		int offset = (channelsMask * pixelsPerChannel) + (z * pixelsPerChannelSlice) + (t * pixelsPerChannelFrame);
		System.arraycopy(pixels, offset, channelPixels, 0, pixelsPerChannel);

		return channelPixels;
	}

	public Object getChannelPixelsByIndex(int c, int z, int t)
	{
		//get the actual channel location from channel bitmask
		return getMergedChannelPixels(1 << (c-1), z, t);
	}

	private int getFeatureIndex(int z, int t)
	{
		return (z) + (t * numSlices);
	}

	public Object getFeaturePixels(int z, int t, FeatureCalculator feature)
	{
		final int featureMergedChannelIndex = getFeatureMergedChannelIndex(feature);

		Map<FeatureCalculator, Object> featureCache = features[getFeatureIndex(z, t)];

		if(!featureCache.containsKey(feature))
		{
			long startTime = System.currentTimeMillis();
			featureCache.put(feature, feature.calculate(getMergedChannelPixels(featureMergedChannelIndex, z, t), width, height, featureCache));
			recordComputationTime(feature, System.currentTimeMillis() - startTime, pixelsPerChannel*numChannels);
		}

		return featureCache.get(feature);

	}

	private int getFeatureMergedChannelIndex(FeatureCalculator feature) {
		final int featureMergedChannelIndex;
		{
			if(numChannels > 1 && !feature.hasTag(FEATURE_TAG_CHANNEL_INDEX))
				throw new RuntimeException("Feature "+feature.getDescriptionWithTags()+" does not have channel tag. Channel " +
						"tag is required for multi-channel images.");

			if(numChannels == 1)
				featureMergedChannelIndex = 0;
			else
				featureMergedChannelIndex = (Integer) feature.getTag(FEATURE_TAG_CHANNEL_INDEX);
		}
		return featureMergedChannelIndex;
	}

	public FeatureReader getReader(int z, int t, FeatureCalculator[] features) throws ExecutionException, InterruptedException
	{
		int[] classes = new int[pixelsPerChannel];
		Arrays.fill(classes, -1);

		return getReader(z, t, features, classes);
	}

	public FeatureReader getReader(int z, int t, FeatureCalculator[] features, int[] classes) throws ExecutionException, InterruptedException
	{
		if(classes.length!=pixelsPerChannel)
			throw new IllegalArgumentException("Classes array does not match slice size. Expected size: "+
					pixelsPerChannel+" Actual size: "+classes.length);

		int numFeatureImages = 0;
		for(FeatureCalculator f : features)
			numFeatureImages+= f.getNumImagesReturned();

		calculateFeatures(z, t, features);

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
			for(Object featureImage : (Object[]) getFeaturePixels(z, t, featureCalculator))
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
		Collections.sort(featureList, (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getDescriptionWithTags(),
				o2.getDescriptionWithTags()));

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

		switch (Utility.getPixelType(image))
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

	public boolean calculateFeatures(int z, int t, FeatureCalculator[] features) throws ExecutionException, InterruptedException
	{
		ExecutorService executorService = Executors.newFixedThreadPool(Prefs.getThreads());
		try
		{
			return calculateFeatures(z, t, features, executorService);
		}
		finally
		{
			executorService.shutdown();
		}
	}

	/**
	 *
	 * @param z
	 * @param t
	 * @param features
	 * @param executorService
	 * @return true if any features were calculated, otherwise false
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public boolean calculateFeatures(int z, int t, FeatureCalculator[] features, ExecutorService executorService) throws ExecutionException, InterruptedException
	{
		List<Future> waitFutures = new ArrayList<Future>();

		final Map<FeatureCalculator, Object> featureCache = this.features[getFeatureIndex(z, t)];
		final FeatureCalculator[] featuresToCalculate = Arrays.stream(features)
				.filter(f -> !featureCache.containsKey(f))
				.toArray(FeatureCalculator[]::new);

		if(featuresToCalculate.length == 0)
			return false;

		final Object monitor = featureCache;

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
					for (FeatureCalculator dependency : featureCalculator.getDependenciesWithTags())
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

						final int featureMergedChannelIndex = getFeatureMergedChannelIndex(featureCalculator);
						Object imagePixels = getMergedChannelPixels(featureMergedChannelIndex, z, t);

						featureCalculator.calculate(imagePixels, width, height, featureCache);

						processingFeatureCalculators.remove(featureCalculator);

						long computationTime = System.currentTimeMillis() - start;
						synchronized (monitor)
						{
							recordComputationTime(featureCalculator, computationTime, pixelsPerChannel*numChannels);
							int numRemaining = processingFeatureCalculators.size() + remainingFeatureCalculators.size();

							if(verbose)
								System.out.println("Calculated imagesurf.feature "+(featuresToCalculate.length -
										numRemaining)+"/"+featuresToCalculate.length+" for " + title + ": " + featureCalculator.getDescriptionWithTags() + " in " + (System.currentTimeMillis() - start) + "ms. [" + numRemaining + " remaining]");

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

	public void clearFeatureCache(int t, int z)
	{
		final Map<FeatureCalculator, Object> featureCache = this.features[getFeatureIndex(z, t)];

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
