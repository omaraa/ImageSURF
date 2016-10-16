import classifier.ImageSurfClassifier;
import classifier.RandomForest;
import feature.FeatureReader;
import feature.ImageFeatures;
import feature.PixelType;
import feature.calculator.FeatureCalculator;
import ij.ImagePlus;
import ij.Prefs;
import io.scif.services.DatasetIOService;
import net.imagej.ImageJ;
import net.mintern.primitive.Primitive;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.util.ColorRGB;
import org.scijava.widget.FileWidget;
import org.scijava.widget.NumberWidget;
import util.Utility;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Plugin(type = Command.class, headless = true,
		menuPath = "Plugins>Segmentation>Train ImageSURF Classifier")
public class TrainImageSurf implements Command{

	public static final int DEFAULT_BAG_SIZE = 30;
	public static final int DEFAULT_EXAMPLE_PORTION = 100;
	public static final int DEFAULT_TREE_DEPTH = 30;
	public static final int DEFAULT_NUM_TREES = 100;
	public static final int DEFAULT_NUM_ATTRIBUTES = 30;

	public static final String DEFAULT_FEATURES_SUFFIX = ".features";

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService ui;

	@Parameter
	private DatasetIOService datasetIOService;


	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelTrainingSet = "----- Training Set -----";

	@Parameter(label = "Training images path", type = ItemIO.INPUT, style=FileWidget.DIRECTORY_STYLE)
	private File imagePath;

	@Parameter(label = "Training images pattern", type = ItemIO.INPUT, required = false)
	private String imagePattern;

	@Parameter(label = "Training labels path", type = ItemIO.INPUT, style=FileWidget.DIRECTORY_STYLE)
	private File labelPath;

	@Parameter(label = "Training labels path suffix", type = ItemIO.INPUT, required = false)
	private String labelSuffix;

	@Parameter(label = "Signal pixel label color", type = ItemIO.INPUT)
	private ColorRGB signalLabelColor = ColorRGB.fromHTMLColor("#ff0000");

	@Parameter(label = "Background pixel label color", type = ItemIO.INPUT)
	private ColorRGB backgroundLabelColor = ColorRGB.fromHTMLColor("#0000ff");

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelImageFeatures= "----- Training Image Features -----";

	@Parameter(label = "Training image features input path", type = ItemIO.INPUT, required = false, style= FileWidget.DIRECTORY_STYLE)
	private File featuresInputPath;

	@Parameter(label = "Training image features output path", type = ItemIO.INPUT, required = false, style= FileWidget.DIRECTORY_STYLE)
	private File featuresOutputPath;

	@Parameter(label = "Training image features path suffix", type = ItemIO.INPUT, required = false)
	private String featuresPathSuffix = DEFAULT_FEATURES_SUFFIX;

	@Parameter(label = "Minimum feature radius",
			style = NumberWidget.SCROLL_BAR_STYLE, min = "0", max = "250",
			type = ItemIO.INPUT,
			callback = "onRadiiChanged")
	private int minFeatureRadius = 0;

	@Parameter(label = "Maximum feature radius",
			style = NumberWidget.SCROLL_BAR_STYLE, min = "0", max = "250",
			type = ItemIO.INPUT,
			callback = "onRadiiChanged",
			initializer = "initialiseDefaults"
	)
	private int maxFeatureRadius;

	@Parameter(label = "Selected feature radii:", initializer = "onRadiiChanged", visibility = ItemVisibility.MESSAGE)
	private String selectedRadii;

	@Parameter(visibility = ItemVisibility.MESSAGE)
 	private final String labelClassifier= "----- Classifier Settings -----";

	@Parameter(label = "Number of trees", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "1", initializer = "initialiseDefaults")
	private int numTrees;

	@Parameter(label = "Maximum tree depth", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "0", initializer = "initialiseDefaults")
	private int treeDepth;

	@Parameter(label = "Feature choices per branch",
			type = ItemIO.INPUT,
			initializer = "onRadiiChanged",
			callback = "onFeatureChoicesChanged")
	private int numAttributes = DEFAULT_NUM_ATTRIBUTES;

	@Parameter(label = "Bag size (%)", type = ItemIO.INPUT,
			style = NumberWidget.SCROLL_BAR_STYLE, min = "1", max = "100", initializer = "initialiseDefaults")
	private int bagSize;

	@Parameter(label = "Training examples to consider (%)", type = ItemIO.INPUT,
			style = NumberWidget.SCROLL_BAR_STYLE, min = "1", max = "100", initializer = "initialiseDefaults")
	private int examplePortion;

	@Parameter(label = "Maximum features", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "0", callback = "onMaxFeaturesChanged")
	private int maxFeatures;

	@Parameter(label = "Random seed", type = ItemIO.INPUT, required = false)
	private String randomSeedString;

	@Parameter(label = "Classifier output path", type = ItemIO.INPUT, style= FileWidget.SAVE_STYLE)
	private File classifierOutputPath;


	FeatureCalculator[] selectedFeatures;
	boolean numAttributesManuallySet = false;
	private boolean maxFeaturesManuallySet;

	private Random random;
	private RandomForest randomForest;
	private PixelType pixelType = null;

	protected void initialiseDefaults()
	{
		bagSize = DEFAULT_BAG_SIZE;
		examplePortion = DEFAULT_EXAMPLE_PORTION;
		treeDepth = DEFAULT_TREE_DEPTH;
		numTrees = DEFAULT_NUM_TREES;

		maxFeatureRadius = Arrays.stream(PixelType.GRAY_16_BIT.getDefaultFeatureCalculators())
				.map((f) -> f.getRadius())
				.max(Integer::compare).get();

		classifierOutputPath = new File(System.getProperty("user.home"), "ImageSURF.classifier");

	}

	protected FeatureCalculator[] getFeatureCalculators(PixelType pixelType, int minFeatureRadius, int maxFeatureRadius)
	{
		return Arrays.stream(pixelType.getDefaultFeatureCalculators())
				.filter((f) -> f.getRadius() >= minFeatureRadius && f.getRadius() <= maxFeatureRadius)
				.toArray(FeatureCalculator[]::new);
	}

	protected void onRadiiChanged() {

		selectedFeatures = getFeatureCalculators(PixelType.GRAY_8_BIT, minFeatureRadius, maxFeatureRadius);

		List<String> radii = Arrays.stream(selectedFeatures)
						.map((f) -> f.getRadius())
						.distinct()
						.sorted()
						.map(Object::toString)
						.collect(Collectors.toCollection(ArrayList::new));

		selectedRadii = String.join(", ", radii);

		if(selectedRadii.isEmpty())
			selectedRadii = "NO FEATURE RADII SELECTED";

		if(!numAttributesManuallySet)
		{
			numAttributes = (int) (weka.core.Utils.log2(selectedFeatures.length - 1) + 1);
			if(numAttributes <= 0)
				numAttributes = 1;
		}

		if(!maxFeaturesManuallySet)
		{
			maxFeatures = selectedFeatures.length;
		}
	}

	protected void onMaxFeaturesChanged()
	{
		maxFeaturesManuallySet = true;

		if(maxFeatures > selectedFeatures.length)
			maxFeatures = selectedFeatures.length;
	}

	protected void onFeatureChoicesChanges()
	{
		numAttributesManuallySet = true;

		if(numAttributes > selectedFeatures.length)
			numAttributes = selectedFeatures.length;
	}


	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(TrainImageSurf.class, true);
	}

	@Override
	public void run()
	{
		if(selectedFeatures == null || selectedFeatures.length == 0)
			throw new RuntimeException("Cannot built classifier with no features.");

		//Sync shared parameters
		SyncedParameters.classifierPath = classifierOutputPath;
		SyncedParameters.featuresSuffix = featuresPathSuffix;
		SyncedParameters.featuresInputPath = featuresInputPath;
		SyncedParameters.featuresOutputPath = featuresOutputPath;

		random = (randomSeedString == null || randomSeedString.isEmpty()) ? new Random() : new Random(randomSeedString.hashCode());

		randomForest = new RandomForest();
		randomForest.setNumThreads(Prefs.getThreads());
		randomForest.setNumTrees(numTrees);
		randomForest.setMaxDepth(treeDepth);
		randomForest.setNumAttributes(numAttributes);
		randomForest.setBagSizePercent(bagSize);
		randomForest.setSeed(random.nextInt());

		FeatureReader reader;
		final Object[] trainingExamples;
		try
		{
			trainingExamples = getTrainingExamples();
			switch (pixelType)
			{
				case GRAY_8_BIT:
					reader = new ImageFeatures.ByteReader((byte[][]) trainingExamples, selectedFeatures.length);
					break;
				case GRAY_16_BIT:
					reader = new ImageFeatures.ShortReader((short[][]) trainingExamples, selectedFeatures.length);
					break;
				default:
					throw new RuntimeException("Pixel type "+pixelType+" not supported.");
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to get training examples", e);
		}

		RandomForest.ProgressListener randomForestProgressListener = (current, max, message) ->
				statusService.showStatus(current, max, message);
		randomForest.addProgressListener(randomForestProgressListener);
		randomForest.buildClassifier(reader, 2);

		if(maxFeatures < selectedFeatures.length)
		{
			final double[] featureImportance;
			try
			{
				featureImportance = randomForest.calculateFeatureImportance(reader);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException("Failed to calculate feature importance", e);
			}
			int[] rankedFeatures = IntStream.range(0, selectedFeatures.length).toArray();

			Primitive.sort(rankedFeatures, (i1, i2) -> {
				return Double.compare(featureImportance[i2], featureImportance[i1]);
			});

			log.info("Feature Importance:");
			for(int i : rankedFeatures)
			{
				log.info(selectedFeatures[i].getDescription() +": "+featureImportance[i]);
			}

			selectedFeatures = Arrays.stream(rankedFeatures, 0, maxFeatures)
					.mapToObj(i -> selectedFeatures[i])
					.toArray(FeatureCalculator[]::new);


			final Object[] optimisedTrainingExamples;
			switch (pixelType)
			{
				case GRAY_8_BIT:
					optimisedTrainingExamples = new byte[maxFeatures+1][];
					break;
				case GRAY_16_BIT:
					optimisedTrainingExamples = new short[maxFeatures+1][];
					break;
				default:
					throw new RuntimeException("Pixel type "+pixelType+" not supported");
			}
			for(int i = 0; i < maxFeatures; i++)
				optimisedTrainingExamples[i] = trainingExamples[rankedFeatures[i]];
			//Add example classes
			optimisedTrainingExamples[maxFeatures] = trainingExamples[trainingExamples.length-1];

			final FeatureReader optimisedFeaturesReader;
			switch (pixelType)
			{
				case GRAY_8_BIT:
					optimisedFeaturesReader = new ImageFeatures.ByteReader((byte[][]) optimisedTrainingExamples, selectedFeatures.length);
					break;
				case GRAY_16_BIT:
					optimisedFeaturesReader = new ImageFeatures.ShortReader((short[][]) optimisedTrainingExamples, selectedFeatures.length);
					break;
				default:
					throw new RuntimeException("Pixel type "+pixelType+" not supported.");
			}

			randomForest.buildClassifier(optimisedFeaturesReader, 2);
		}

		randomForest.removeprogressListener(randomForestProgressListener);

		ImageSurfClassifier imageSurfClassifier = new ImageSurfClassifier(randomForest, selectedFeatures, pixelType);

		boolean writeSuccessful = false;

		while(!writeSuccessful)
		{
			try
			{
				Utility.serializeObject(imageSurfClassifier, classifierOutputPath, true);
				writeSuccessful = true;
				log.trace("Classifier saved.");
			}
			catch (IOException e)
			{
				DialogPrompt.Result result = ui.showDialog("Failed to write classifier to "+classifierOutputPath.getAbsolutePath()+". Try another path?", DialogPrompt.MessageType.ERROR_MESSAGE, DialogPrompt.OptionType.OK_CANCEL_OPTION);

				switch(result)
				{
					case CANCEL_OPTION:
						if(ui.showDialog("Trained classifier will be lost. Are you sure?", DialogPrompt.MessageType.WARNING_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION) == DialogPrompt.Result.YES_OPTION)
						{
							log.error("Failed to save classifier", e);
								return;
						}
						break;
					case OK_OPTION:
					{
						classifierOutputPath = ui.chooseFile(classifierOutputPath, FileWidget.SAVE_STYLE);
					}
					break;
				}
			}
		}

	}

	private Object[] getTrainingExamples() throws Exception
	{
		File[] labelFiles = labelPath.listFiles(imageLabelFileFilter);
		File[] imageFiles = Arrays.stream(labelFiles)
				.map((l) -> {
					String imageName = l.getName();
					if (null != labelSuffix && !labelSuffix.isEmpty())
						imageName = imageName.substring(0, imageName.length() - labelSuffix.length());

					return new File(imagePath, imageName);
				}).toArray(File[]::new);
		File[] featureInputFiles = Arrays.stream(imageFiles)
				.map((i) -> {
					if (featuresInputPath == null || !featuresInputPath.exists() || !featuresInputPath.isDirectory())
						return null;

					return new File(featuresInputPath, i.getName() + (featuresPathSuffix == null ? "" : featuresPathSuffix));
				}).toArray(File[]::new);
		File[] featureOutputFiles = Arrays.stream(imageFiles)
				.map((i) -> {
					if (featuresOutputPath == null || !featuresOutputPath.exists() || !featuresOutputPath.isDirectory())
						return null;

					return new File(featuresOutputPath, i.getName() + (featuresPathSuffix == null ? "" : featuresPathSuffix));
				}).toArray(File[]::new);

		if(labelFiles.length == 0)
			throw new RuntimeException("No valid label files");

		final int numImages = labelFiles.length;
		int[] numLabelledPixels = countLabelledPixels(labelFiles);
		int totalLabelledPixels = Utility.sum(numLabelledPixels);
		Collection<Object[]> examples = new ArrayList<>();

		final int[] examplePixelIndices = selectExamplePixels(totalLabelledPixels);

		int currentImageFirstExampleIndex = 0;
		for (int imageIndex = 0; imageIndex < numImages; imageIndex++)
		{
			final int currentImageIndex = imageIndex;
			final int firstExampleIndex = currentImageFirstExampleIndex;


			final ImagePlus image = new ImagePlus(imageFiles[imageIndex].getAbsolutePath());
			if (image.getNFrames() * image.getNSlices() > 1)
				throw new RuntimeException("Training image " + image.getTitle() + " not valid. Images must be single plane.");

			if (image.getNChannels() != 1)
				throw new RuntimeException("ImageSURF does not yet support multi-channel images.");

			final ImageFeatures imageFeatures;
			if (featureInputFiles[imageIndex] == null || !featureInputFiles[imageIndex].exists())
			{
				imageFeatures = new ImageFeatures(image);
			}
			else
			{
				statusService.showStatus("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
				System.out.println("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
				imageFeatures = ImageFeatures.deserialize(featureInputFiles[imageIndex].toPath());
			}

			if (imageIndex == 0)
			{
				pixelType = imageFeatures.pixelType;
				selectedFeatures = getFeatureCalculators(pixelType, minFeatureRadius, maxFeatureRadius);
			}

			if (imageFeatures.pixelType != pixelType)
				throw new RuntimeException("Training images must all be either 8 or 16 bit greyscale format.");

			ImageFeatures.ProgressListener progressListener = new ImageFeatures.ProgressListener()
			{
				long lastUpdate = -1;
				@Override
				public void onProgress(int current, int max, String message)
				{
					long currentTime = System.currentTimeMillis();

					if(current!=max && (currentTime-lastUpdate) < 3000)
						return;

					statusService.showStatus(current, max, "Calculating features for image " + (currentImageIndex + 1) + "/" + numImages);
					System.out.println(current+"/"+max);
					lastUpdate = currentTime;
				}
			};

			boolean calculatedFeatures = false;
			imageFeatures.addProgressListener(progressListener);
			if(imageFeatures.calculateFeatures(0, 0, 0, selectedFeatures))
				calculatedFeatures = true;
			imageFeatures.removeProgressListener(progressListener);

			if (featureOutputFiles[imageIndex] != null && calculatedFeatures)
			{
				statusService.showStatus("Writing features for image " + (currentImageIndex + 1) + "/" + numImages);
				System.out.println("Writing features to "+featureOutputFiles[imageIndex].toPath());
				imageFeatures.serialize(featureOutputFiles[imageIndex].toPath());
				System.out.println("Wrote features to "+featureOutputFiles[imageIndex].toPath());
			}

			//fixme: only grabbing first set of feature pixels from calculators. Current calculators only produce one
			// feature image, but future ones may produce more.
			Object[] featurePixels;
			switch (pixelType)
			{
				case GRAY_8_BIT:
					featurePixels = new byte[selectedFeatures.length + 1][];
					break;
				case GRAY_16_BIT:
					featurePixels = new short[selectedFeatures.length + 1][];
					break;

				default:
					throw new RuntimeException("Pixel type "+pixelType+" not supported.");
			}
			for (int i = 0; i < selectedFeatures.length; i++)
				featurePixels[i] = ((Object[])imageFeatures.getFeaturePixels(0, 0, 0, selectedFeatures[i]))[0];


			statusService.showStatus("Extracting examples from image " + (currentImageIndex + 1) + "/" + numImages);

			final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);
			final int signalColor = signalLabelColor.getARGB();
			final int backgroundColor = backgroundLabelColor.getARGB();
			final int[] labelledPixelIndices;
			{
				final Object pixelClasses;
				switch (pixelType)
				{
					case GRAY_8_BIT:
						pixelClasses = new byte[imageFeatures.pixelsPerChannel];
						break;
					case GRAY_16_BIT:
						pixelClasses = new short[imageFeatures.pixelsPerChannel];
						break;
					default:
						throw new RuntimeException("Pixel type "+pixelType+" not supported.");
				}

				Collection<Integer> labelledPixelIndicesList = new ArrayList<Integer>();

				for (int pixelIndex = 0; pixelIndex < labelImagePixels.length; pixelIndex++)
				{
					final int pixelValue = labelImagePixels[pixelIndex];
					int pixelClass;
					if (pixelValue == signalColor)
					{
						pixelClass = 0;
						labelledPixelIndicesList.add(pixelIndex);
					}
					else if (pixelValue == backgroundColor)
					{
						pixelClass = 1;
						labelledPixelIndicesList.add(pixelIndex);
					}
					else
					{
						pixelClass = -1;
					}

					switch (pixelType)
					{
						case GRAY_8_BIT:
							((byte[])pixelClasses)[pixelIndex] = (byte) pixelClass;
							break;
						case GRAY_16_BIT:
							((short[])pixelClasses)[pixelIndex] = (short) pixelClass;
							break;
					}
				}

				featurePixels[selectedFeatures.length] = pixelClasses;

				labelledPixelIndices = labelledPixelIndicesList.stream().mapToInt(Integer::intValue).toArray();
			}

			int[] selectedPixels = Arrays.stream(examplePixelIndices)
					.filter(i -> i >= firstExampleIndex && i < firstExampleIndex + numLabelledPixels[currentImageIndex])
					.map(i -> labelledPixelIndices[i - firstExampleIndex])
					.toArray();
			currentImageFirstExampleIndex+= numLabelledPixels[currentImageIndex];

			switch (pixelType)
			{
				case GRAY_8_BIT:
					subsetInstancesDestructive((byte[][]) featurePixels, selectedPixels);
					break;
				case GRAY_16_BIT:
					subsetInstancesDestructive((short[][]) featurePixels, selectedPixels);
					break;
			}

			examples.add(featurePixels);
		}

		switch (pixelType)
		{
			case GRAY_8_BIT:
				Collection<byte[][]> byteExamples = new ArrayList<>();
				for(Object be : examples)
					byteExamples.add((byte[][]) be);
				return mergeByteImageFeatures(byteExamples);
			case GRAY_16_BIT:
				Collection<short[][]> shortExamples = new ArrayList<>();
				for(Object be : examples)
					shortExamples.add((short[][]) be);
				return mergeShortImageFeatures(shortExamples);
			default:
				throw new RuntimeException("Pixel type "+pixelType+" not supported.");
		}
	}

	private int[] countLabelledPixels(File[] labelFiles)
	{
		final int numImages = labelFiles.length;
		final int[] numLabelledPixels = new int[numImages];

		for(int imageIndex = 0; imageIndex < numImages; imageIndex++)
		{
			statusService.showStatus(imageIndex+1, numImages, "Scanning image labels "+(imageIndex+1)+"/"+ numImages);

			final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);

			int labelCount = 0;

			for (int pixelValue : labelImagePixels)
				if (pixelValue == signalLabelColor.getARGB() || pixelValue == backgroundLabelColor.getARGB())
					labelCount++;

			numLabelledPixels[imageIndex] = labelCount;
		}

		return numLabelledPixels;
	}

	private int[] getLabelImagePixels(File labelFile)
	{
		final ImagePlus labelImage = new ImagePlus(labelFile.getAbsolutePath());
		if(!(labelImage.getNChannels() == 1 || labelImage.getNChannels() == 3) || labelImage.getNFrames()*labelImage.getNSlices() > 1)
			throw new RuntimeException("Label image "+labelImage.getTitle()+" not valid. Label images must be single plane RGB format.");

		return (int[]) labelImage
				.getProcessor().convertToRGB().getPixels();
	}

	private int[] selectExamplePixels(int totalLabelledPixels)
	{
		final int[] examplePixelIndices;
		if(examplePortion < 100)
		{
			examplePixelIndices = new int[(totalLabelledPixels * examplePortion) / 100];
			for(int i = 0; i < examplePixelIndices.length; i++)
				examplePixelIndices[i] = random.nextInt(totalLabelledPixels);

			Arrays.sort(examplePixelIndices);
		}
		else
		{
			examplePixelIndices = IntStream.range(0, totalLabelledPixels).toArray();
		}

		return examplePixelIndices;
	}

	private final FileFilter imageLabelFileFilter = new FileFilter()
	{
		@Override
		public boolean accept(File labelPath)
		{
			System.out.println("Checking "+labelPath.getAbsolutePath());

			if(!labelPath.isFile() || labelPath.isHidden() || !labelPath.canRead())
				return false;

			System.out.println("Can read.");

			final String labelName = labelPath.getName();
			final String imageName;

			if(null!= labelSuffix && !labelSuffix.isEmpty())
			{
				if(!labelName.endsWith(labelSuffix))
					return false;

				imageName = labelName.substring(0, labelName.length() - labelSuffix.length());
			}
			else
			{
				imageName = labelName;
			}

			System.out.println("Has suffix.");

			//Check for matching image. If it doesn't exist or isn't suitable, exclude this label image
			File imagePath = new File(TrainImageSurf.this.imagePath, imageName);

			if(!imagePath.exists() || imagePath.isHidden() || !imagePath.isFile() || !imageName.contains(imagePattern))
				return false;

			System.out.println("Has matching image file.");

			//If image and label image dimensions don't match or we can't get this info, exclude this label image
//			try
//			{
//				if(!Utility.getImageDimensions(labelPath).equals(Utility.getImageDimensions(imagePath)))
//					return false;
//			}
//			catch (IOException e)
//			{
//				System.err.println("Failed to get image dimensions");
//				log.error("Failed to get image dimensions", e);
//				return false;
//			}

			System.out.println("Image matches.");
			System.out.printf("Accepting "+labelPath.getAbsolutePath());

			return true;
		}
	};

	private static void subsetInstancesDestructive(short[][] features, int[] indicesToKeep)
	{
		for(int featureIndex = 0; featureIndex < features.length; featureIndex++ )
		{
			short[] feature = features[featureIndex];

			short[] featureSubset = new short[indicesToKeep.length];

			int numKept = 0;
			for(int i : indicesToKeep)
				featureSubset[numKept++] = feature[i];

			features[featureIndex] = featureSubset;
		}
	}

	private static void subsetInstancesDestructive(byte[][] features, int[] indicesToKeep)
	{
		for(int featureIndex = 0; featureIndex < features.length; featureIndex++ )
		{
			byte[] feature = features[featureIndex];

			byte[] featureSubset = new byte[indicesToKeep.length];

			int numKept = 0;
			for(int i : indicesToKeep)
				featureSubset[numKept++] = feature[i];

			features[featureIndex] = featureSubset;
		}
	}

	/**
	 * Merge collection of image feature arrays into one. Input arrays are nullified to free memory.
	 * @param imageFeatures
	 * @return
	 */
	static short[][] mergeShortImageFeatures(Collection<short[][]> imageFeatures)
	{
		int numFeatures = imageFeatures.iterator().next().length;

		int numPixels = 0;
		for(short[][] image : imageFeatures)
		{
			numPixels+= image[0].length;
		}

		short[][] merged = new short[numFeatures][];

		for(int featureIndex = 0; featureIndex < numFeatures; featureIndex++)
		{
			short[] mergedFeature = new short[numPixels];
			int numMerged = 0;
			for (short[][] image : imageFeatures)
			{
				short[] feature = image[featureIndex];
				System.arraycopy(feature, 0, mergedFeature, numMerged, feature.length);

				numMerged+= feature.length;

				image[featureIndex] = null;
			}

			merged[featureIndex] = mergedFeature;
		}

		return merged;
	}

	/**
	 * Merge collection of image feature arrays into one. Input arrays are nullified to free memory.
	 * @param imageFeatures
	 * @return
	 */
	static byte[][] mergeByteImageFeatures(Collection<byte[][]> imageFeatures)
	{
		int numFeatures = imageFeatures.iterator().next().length;

		int numPixels = 0;
		for(byte[][] image : imageFeatures)
		{
			numPixels+= image[0].length;
		}

		byte[][] merged = new byte[numFeatures][];

		for(int featureIndex = 0; featureIndex < numFeatures; featureIndex++)
		{
			byte[] mergedFeature = new byte[numPixels];
			int numMerged = 0;
			for (byte[][] image : imageFeatures)
			{
				byte[] feature = image[featureIndex];
				System.arraycopy(feature, 0, mergedFeature, numMerged, feature.length);

				numMerged+= feature.length;

				image[featureIndex] = null;
			}

			merged[featureIndex] = mergedFeature;
		}

		return merged;
	}
}
