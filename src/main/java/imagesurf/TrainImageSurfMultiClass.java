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

package imagesurf;

import ij.*;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.classifier.RandomForest;
import imagesurf.feature.FeatureReader;
import imagesurf.feature.ImageFeatures;
import imagesurf.feature.PixelType;
import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.util.Utility;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.mintern.primitive.Primitive;
import net.mintern.primitive.comparators.IntComparator;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

@Plugin(type = Command.class, headless = true,
		menuPath = "Plugins>Segmentation>ImageSURF>3. Train ImageSURF Classifier")
public class TrainImageSurfMultiClass implements Command{

	private static final String AFTER_TRAINING_OPTION_NOTHING = "Do nothing";
	private static final String AFTER_TRAINING_OPTION_DISPLAY = "Segment training images and display as stacks";
	private static final String AFTER_TRAINING_OPTION_SAVE = "Segment training images and save ";

	public static final String IMAGESURF_DATA_FOLDER_NAME = "imagesurf-data";
	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private UIService ui;

	@Parameter
	private PrefService prefService;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelTrainingSet = "----- Training Set -----";

	@Parameter(label = "Raw training images path", type = ItemIO.INPUT, style=FileWidget.DIRECTORY_STYLE,
			description = "Folder of images to use as training input. Images must be single-plane greyscale images with the same bit-depth.")
	private File rawImagePath;

	@Parameter(label = "Un-annotated training images path", type = ItemIO.INPUT, style=FileWidget.DIRECTORY_STYLE,
			description = "Folder of un-annotated images to use as training input. These images should be a copy " +
					"of the raw training images with identical names, converted to an RGB format. Brightness and " +
					"contrast should be scaled appropriately to accurately annotate the images.")
	private File unlabelledImagePath;


	@Parameter(label = "Annotated training images path", type = ItemIO.INPUT, style=FileWidget.DIRECTORY_STYLE,
			description = "Folder of annotated images to use as training input. These images should be a copy " +
					"of the un-annotated training images with identical names, annotated with a distinct color for " +
					"each class.")
	private File labelPath;

	@Parameter(label = "Input file names contain", type = ItemIO.INPUT, required = false,
			description = "A pattern string to limit the input image and label files. ONLY files that contain this exact, " +
					"case-sensitive, string will be used as training input. e.g., \".tif\" will exclude all files that do not " +
					"contain \".tif\" in the file name")
	private String imagePattern;

	@Parameter(label = "Save calculated features", type = ItemIO.INPUT,
			description = "If checked, calculated images features will be saved as a \".features\" file in the input " +
					"images directory. Saving image features can drastically reduce the time required to re-train and " +
					"re-segment image but may use large amounts of disk space.")
	private boolean saveCalculatedFeatures = false;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelClassifier = "----- Classifier (See ImageSURF Classifier Settings for options) -----";

	@Parameter(label = "Classifier output path", type = ItemIO.INPUT, style= FileWidget.SAVE_STYLE,
			description = "Where the classifier will be saved. A \".imagesurf\" file extension is recommended.")
	private File classifierOutputPath = new File(System.getProperty("user.home"), "ImageSURF.imagesurf");

	@Parameter(label="After training",
			choices={AFTER_TRAINING_OPTION_NOTHING,AFTER_TRAINING_OPTION_DISPLAY,AFTER_TRAINING_OPTION_SAVE},
			visibility = ItemVisibility.INVISIBLE)
	String afterTraining;

	@Parameter(type = ItemIO.OUTPUT)
	Dataset validationImage;

	@Parameter(type = ItemIO.OUTPUT)
	String ImageSURF;


	FeatureCalculator[] selectedFeatures;

	private Random random;
	private RandomForest randomForest;
	private PixelType pixelType = null;
	private int numChannels = -1;

	File[] labelFiles;
	File[] unlabelledFiles;
	File[] rawImageFiles;

	File imageSurfDataPath;
	File featuresPath;
	File[] featureFiles;

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(TrainImageSurfMultiClass.class, true);
	}

	@Override
	public void run()
	{
		if(imagePattern == null)
			imagePattern = "";

		if(unlabelledImagePath == null)
			unlabelledImagePath = rawImagePath;

		if(rawImagePath == null || labelPath == null)
			throw new IllegalArgumentException("Paths must be set for both raw images and annotated images.");

		if(!rawImagePath.exists())
			throw new IllegalArgumentException("Raw image path '"+rawImagePath.getAbsolutePath()+"' does not exist.");

		if(!unlabelledImagePath.exists())
			throw new IllegalArgumentException("Un-annotated image path '"+unlabelledImagePath.getAbsolutePath()
					+"' does not exist.");

		if(!labelPath.exists())
			throw new IllegalArgumentException("Annotated image path '"+labelPath.getAbsolutePath()+"' does" +
					" not exist.");

		if(!rawImagePath.canRead())
			throw new IllegalArgumentException("Raw image path '"+rawImagePath.getAbsolutePath()+"' can not be read.");

		if(!unlabelledImagePath.canRead())
			throw new IllegalArgumentException("Un-annotated image path '"+unlabelledImagePath.getAbsolutePath()
					+"' can not be read.");

		if(!labelPath.canRead())
			throw new IllegalArgumentException("Annotated image path '"+labelPath.getAbsolutePath()
					+"' can not be read.");

		labelFiles = labelPath.listFiles(imageLabelFileFilter);
		unlabelledFiles = Arrays.stream(labelFiles)
				.map((l) -> new File(unlabelledImagePath, l.getName()))
				.toArray(File[]::new);
		rawImageFiles = Arrays.stream(labelFiles)
				.map((l) -> new File(rawImagePath, l.getName()))
				.toArray(File[]::new);

		imageSurfDataPath = new File(rawImagePath, IMAGESURF_DATA_FOLDER_NAME);
		featuresPath = new File(imageSurfDataPath, "features");
		featureFiles = Arrays.stream(rawImageFiles)
				.map((i) -> new File(featuresPath, i.getName() + ".features")).toArray(File[]::new);

		if(saveCalculatedFeatures)
			featuresPath.mkdirs();

		{
			ImageFeatures prototype = new ImageFeatures(new ImagePlus(rawImageFiles[0].getAbsolutePath()));
			numChannels = prototype.numChannels;
			pixelType = prototype.pixelType;
		}

		selectedFeatures = getSelectedFeatures();

		if(selectedFeatures == null || selectedFeatures.length == 0)
			throw new RuntimeException("Cannot build imagesurf.classifier with no features.");

		String randomSeedString= prefService.get(ImageSurfSettings.IMAGESURF_RANDOM_SEED, null);
		random = (randomSeedString == null || randomSeedString.isEmpty()) ? new Random() : new Random(randomSeedString.hashCode());

		configureClassifier();

		FeatureReader reader;
		final Object[] trainingExamples;
		try
		{
			trainingExamples = getTrainingExamples();
			switch (pixelType)
			{
				case GRAY_8_BIT:
					reader = new ImageFeatures.ByteReader((byte[][]) trainingExamples, trainingExamples.length-1);
					break;
				case GRAY_16_BIT:
					reader = new ImageFeatures.ShortReader((short[][]) trainingExamples, trainingExamples.length-1);
					break;
				default:
					throw new RuntimeException("Pixel type "+pixelType+" not supported.");
			}
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to get training examples", e);
		}

		if(reader.getNumInstances() == 0)
			throw new RuntimeException("No example pixels provided - cannot train classifier.");

		final int numClasses;
		{
			Set<Integer> classes = new HashSet<>();
			for(short classIndex : reader.getClasses())
				classes.add((int) classIndex);

			numClasses = classes.size();
		}

		{
			final int[] classCounts = new int[numClasses];
			for (short c : reader.getClasses())
				classCounts[c]++;

			log.info("Class examples:");
			for (int i = 0; i < numClasses; i++)
				log.info("\t"+i+": "+classCounts[i]);
		}

		RandomForest.ProgressListener randomForestProgressListener = (current, max, message) ->
				statusService.showStatus(current, max, message);
		randomForest.addProgressListener(randomForestProgressListener);
		randomForest.buildClassifier(reader, numClasses);

		final int maxFeatures = getMaxFeatures();
		if(maxFeatures < selectedFeatures.length && maxFeatures > 0)
		{
			selectFeatures(maxFeatures, reader, trainingExamples);
		}

		try
		{
			int[] verificationClasses = randomForest.classForInstances(reader);
			int[] verificationClassCount = new int[numClasses];

			int[] classCount = new int[numClasses];

			int correct = 0;
			for(int i = 0;i<verificationClasses.length;i++)
			{
				if (verificationClasses[i] == reader.getClassValue(i))
					correct++;

				verificationClassCount[verificationClasses[i]]++;
				classCount[reader.getClassValue(i)]++;
			}

			//Output some info about training to the log and output text
			{
				StringBuilder info = new StringBuilder("Classes in training set - ");
				for(int i=0;i<numClasses;i++)
					info.append(i+": "+classCount[i]+"\t");

				info.append("\nClasses in verification set - ");
				for(int i=0;i<numClasses;i++)
					info.append(i+": "+verificationClassCount[i]+"\t");

				info.append("\n\nSegmenter classifies "+correct+"/"+verificationClasses.length+" "+(((double)correct)
						/verificationClasses.length)*100+"%) of the training pixels correctly.");

				log.info(info.toString());
				ImageSURF = info.toString();
			}
		}
		catch (InterruptedException e)
		{
			log.error(e);
		}

		randomForest.removeprogressListener(randomForestProgressListener);

		ImageSurfClassifier imageSurfClassifier = new ImageSurfClassifier(randomForest, selectedFeatures, pixelType, numChannels);

		writeClassifier(imageSurfClassifier);

		ImageSURF = "ImageSURF classifier successfully trained and saved to "+classifierOutputPath.getAbsolutePath()
				+"\n\n" + ImageSURF+"\n\n";
		ImageSURF += Utility.describeClassifier(imageSurfClassifier);

		switch (afterTraining)
		{
			case AFTER_TRAINING_OPTION_DISPLAY:
				segmentTrainingImagesAndDisplay(imageSurfClassifier);

				break;
			case AFTER_TRAINING_OPTION_SAVE:
				segmentTrainingImagesAndSave(imageSurfClassifier);
				break;
			case AFTER_TRAINING_OPTION_NOTHING:
			default:
				break;
		}

		if(imageSurfDataPath.exists())
			IJ.showMessage("ImageSURF training data files " +
					(AFTER_TRAINING_OPTION_SAVE.equals(afterTraining) ?  "and segmented training images " : "") +
					"have been created in the folder\n\n"+imageSurfDataPath.getAbsolutePath()+"\n\nIt is recommended " +
					"that you delete these files after the imagesurf.classifier has been finalised to save disk space."
			);
	}

	private FeatureCalculator[] getSelectedFeatures() {
		FeatureCalculator[] baseCalculators = ImageSurfImageFilterSelection.getFeatureCalculators(
				pixelType,
				prefService.getInt(ImageSurfSettings.IMAGESURF_MIN_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MIN_FEATURE_RADIUS),
				prefService.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MAX_FEATURE_RADIUS),
				prefService);

		final int numMergedChannels = Utility.calculateNumMergedChannels(numChannels);

		List<FeatureCalculator> selectedFeatures = new ArrayList<>(baseCalculators.length*numMergedChannels);

		for(int c = 0 ; c < numMergedChannels; c++)
		{
			for(FeatureCalculator f : baseCalculators)
			{
				FeatureCalculator tagged = f.duplicate();
				tagged.setTag(ImageFeatures.FEATURE_TAG_CHANNEL_INDEX, c);
				selectedFeatures.add(tagged);
			}
		}

		return selectedFeatures.stream().toArray(FeatureCalculator[]::new);
	}

	private void segmentTrainingImagesAndDisplay(ImageSurfClassifier imageSurfClassifier)
	{
		final PixelType pixelType = imageSurfClassifier.getPixelType();

		final int numImages = rawImageFiles.length;
		final ImageStack[] imageStacks = new ImageStack[numImages];
		final ImageStack[] segmentationStacks = new ImageStack[numImages];

		int maxHeight = 0;
		int maxWidth = 0;

		for (int imageIndex = 0; imageIndex < numImages; imageIndex++)
		{
			final File imagePath = rawImageFiles[imageIndex];
			try
			{
				ImagePlus image = new ImagePlus(imagePath.getAbsolutePath());

				final ImageFeatures imageFeatures;
				if (featureFiles[imageIndex] == null || !featureFiles[imageIndex].exists())
				{
					imageFeatures = new ImageFeatures(image);
					log.error(featureFiles[imageIndex].getAbsolutePath()+" doesn't exist.");

				}
				else
				{
					statusService.showStatus("Reading features for image " + (imageIndex + 1) + "/" + numImages);
					log.info("Reading features for image " + (imageIndex + 1) + "/" + numImages);
					imageFeatures = ImageFeatures.deserialize(featureFiles[imageIndex].toPath());
				}

				ImageStack segmentation = Utility.segmentImage(imageSurfClassifier, imageFeatures, image, statusService);

				segmentationStacks[imageIndex] = segmentation;
				imageStacks[imageIndex] = image.getStack();

				maxWidth = Math.max(maxWidth, image.getWidth());
				maxHeight= Math.max(maxHeight, image.getHeight());
			}
			catch (Exception e)
			{
				log.error(e);
			}
		}

		List<ImageProcessor> resizedImages = new ArrayList<>();
		List<ImageProcessor> resizedSegmentations = new ArrayList<>();

		for(int imageIndex=0;imageIndex<numImages;imageIndex++)
		{
			ImageStack segmentationStack = segmentationStacks[imageIndex];
			ImageStack imageStack = imageStacks[imageIndex];

			int xOffset=(maxWidth-segmentationStack.getWidth())/2;
			int yOffset=(maxHeight-segmentationStack.getHeight())/2;

			for(int sliceIndex=0; sliceIndex < segmentationStack.size(); sliceIndex++)
			{
				final ImageProcessor segmentationProcessor;
				final ImageProcessor imageProcessor;
				switch (pixelType) {
					case GRAY_8_BIT:
						segmentationProcessor = new ByteProcessor(maxWidth, maxHeight);
						imageProcessor = new ByteProcessor(maxWidth, maxHeight);

						imageProcessor.insert(imageStack.getProcessor(sliceIndex+1), xOffset, yOffset);
						segmentationProcessor.insert(segmentationStack.getProcessor(sliceIndex+1), xOffset, yOffset);

						break;
					case GRAY_16_BIT:
						segmentationProcessor = new ShortProcessor(maxWidth, maxHeight);
						imageProcessor = new ShortProcessor(maxWidth, maxHeight);

						imageProcessor.insert(imageStack.getProcessor(sliceIndex+1), xOffset, yOffset);
						segmentationProcessor.insert(segmentationStack.getProcessor(sliceIndex+1), xOffset, yOffset);
						break;
					default:
						throw new RuntimeException("Pixel type "+pixelType+ "not supported.");
				}




				resizedImages.add(imageProcessor);
				resizedSegmentations.add(segmentationProcessor);
			}
		}

		ImageStack imageStack = new ImageStack(maxWidth, maxHeight);
		ImageStack segmentationStack = new ImageStack(maxWidth, maxHeight);
		for(int processorIndex=0;processorIndex<resizedImages.size();processorIndex++)
		{
			imageStack.addSlice(resizedImages.get(processorIndex));
			segmentationStack.addSlice(resizedSegmentations.get(processorIndex));
		}

		new ImagePlus("Segmented Training Images", segmentationStack).show();
		new ImagePlus("Training Images", imageStack).show();
	}

	private void segmentTrainingImagesAndSave(ImageSurfClassifier imageSurfClassifier)
	{
		File outputFolder = new File(imageSurfDataPath, "segmented");
		outputFolder.mkdirs();

		final int numImages = rawImageFiles.length;

		for (int imageIndex = 0; imageIndex < numImages; imageIndex++)
		{
			final File imagePath = rawImageFiles[imageIndex];
			try
			{
				ImagePlus image = new ImagePlus(imagePath.getAbsolutePath());

				final ImageFeatures imageFeatures;
				if (featureFiles[imageIndex] == null || !featureFiles[imageIndex].exists())
				{
					imageFeatures = new ImageFeatures(image);
				}
				else
				{
					statusService.showStatus("Reading features for image " + (imageIndex + 1) + "/" + numImages);
					log.info("Reading features for image " + (imageIndex + 1) + "/" + numImages);
					imageFeatures = ImageFeatures.deserialize(featureFiles[imageIndex].toPath());
				}

				ImageStack segmentation = Utility.segmentImage(imageSurfClassifier, imageFeatures, image, statusService);
				ImagePlus segmentationImage = new ImagePlus("segmentation", segmentation);

				if(segmentation.size() > 1)
					new FileSaver(segmentationImage).saveAsTiffStack(new File(outputFolder, imagePath.getName()).getAbsolutePath());
				else
					new FileSaver(segmentationImage).saveAsTiff(new File(outputFolder, imagePath.getName()).getAbsolutePath());
			}
			catch (Exception e)
			{
				log.error(e);
			}
		}
	}

	private void configureClassifier()
	{
		int numTrees = prefService.getInt(ImageSurfSettings.IMAGESURF_NUM_TREES, ImageSurfSettings.DEFAULT_NUM_TREES);
		int treeDepth = prefService.getInt(ImageSurfSettings.IMAGESURF_TREE_DEPTH, ImageSurfSettings.DEFAULT_TREE_DEPTH);
		int numAttributes = prefService.getInt(ImageSurfSettings.IMAGESURF_NUM_ATTRIBUTES, ImageSurfSettings.DEFAULT_NUM_ATTRIBUTES);
		int bagSize = prefService.getInt(ImageSurfSettings.IMAGESURF_BAG_SIZE, ImageSurfSettings.DEFAULT_BAG_SIZE);

		if(numAttributes <= 0)
		{
			numAttributes = (int) (Utility.log2(selectedFeatures.length - 1) + 1);
			if(numAttributes <= 0)
				numAttributes = 1;
		}

		randomForest = new RandomForest();
		randomForest.setNumThreads(Prefs.getThreads());
		randomForest.setNumTrees(numTrees);
		randomForest.setMaxDepth(treeDepth);
		randomForest.setNumAttributes(numAttributes);
		randomForest.setBagSizePercent(bagSize);
		randomForest.setSeed(random.nextInt());
	}

	private int getMaxFeatures()
	{
		return prefService.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURES, ImageSurfSettings.DEFAULT_MAX_FEATURES);
	}

	private void writeClassifier(ImageSurfClassifier imageSurfClassifier)
	{

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
				DialogPrompt.Result result = ui.showDialog("Failed to write imagesurf.classifier to "+classifierOutputPath.getAbsolutePath()+". Try another path?", DialogPrompt.MessageType.ERROR_MESSAGE, DialogPrompt.OptionType.OK_CANCEL_OPTION);

				switch(result)
				{
					case CANCEL_OPTION:
						if(ui.showDialog("Trained imagesurf.classifier will be lost. Are you sure?", DialogPrompt.MessageType.WARNING_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION) == DialogPrompt.Result.YES_OPTION)
						{
							log.error("Failed to save imagesurf.classifier", e);
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

	private void selectFeatures(int maxFeatures, FeatureReader reader, Object[] trainingExamples)
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
		int[] rankedFeatures = IntStream.range(0, reader.getNumFeatures())
				.filter(i -> reader.getClassIndex() != i)
				.toArray();

		Primitive.sort(rankedFeatures, (i1, i2) -> {
			return Double.compare(featureImportance[i2], featureImportance[i1]);
		});


		log.info("Feature Importance:");
		for(int i : rankedFeatures)
		{
			log.info(selectedFeatures[i].getDescriptionWithTags() +": "+featureImportance[i]);
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

		//Add class annotations
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

		randomForest.buildClassifier(optimisedFeaturesReader, reader.getNumClasses());
	}

	private Object[] getTrainingExamples() throws Exception
	{
		//Reset channels and pixeltype just in case
		numChannels = -1;
		pixelType = null;

		if(labelFiles.length == 0)
			throw new RuntimeException("No valid label files");

		final int numImages = labelFiles.length;
		int[] numLabelledPixels = countLabelledPixels(labelFiles);
		int totalLabelledPixels = Utility.sum(numLabelledPixels);
		final Collection<Object[]> examples = new ArrayList<>();

		if(totalLabelledPixels == 0)
			throw new RuntimeException("No labels found in label files");

		final int[] examplePixelIndices = selectExamplePixels(totalLabelledPixels);

		final Map<Integer, Integer> classColors = new HashMap<>();

		int currentImageFirstExampleIndex = 0;
		for (int imageIndex = 0; imageIndex < numImages; imageIndex++)
		{
			if(numLabelledPixels[imageIndex] == 0)
				continue;

			final int currentImageIndex = imageIndex;
			final int firstExampleIndex = currentImageFirstExampleIndex;

			final ImagePlus rawImage;
			{
				ImagePlus imagePlus = new ImagePlus(rawImageFiles[imageIndex].getAbsolutePath());

				if(imagePlus.getType() == ImagePlus.COLOR_RGB)
				{
					if(Utility.isGrayScale(imagePlus))
					{
						rawImage = new ImagePlus(imagePlus.getTitle(), imagePlus.getChannelProcessor());
					}
					else
					{
						rawImage = new CompositeImage(imagePlus, CompositeImage.GRAYSCALE);
					}
				}
				else
					rawImage = imagePlus;
			}

			if (rawImage.getNFrames() * rawImage.getNSlices() > 1)
				throw new RuntimeException("Training image " + rawImage.getTitle() + " not valid. Images must be single plane.");


			if(numChannels < 0)
			{
				numChannels = rawImage.getNChannels();
			}

			if(rawImage.getNChannels() != numChannels)
				throw new RuntimeException("Training image " + rawImage.getTitle() + " not valid. Image has "+rawImage.getNChannels()+" channels. Expected "+numChannels+".");

			final ImageFeatures imageFeatures;
			if (featureFiles[imageIndex] == null || !featureFiles[imageIndex].exists())
			{
				log.info("Reading image " + (currentImageIndex + 1) + "/" + numImages);
				imageFeatures = new ImageFeatures(rawImage);
			}
			else
			{
				statusService.showStatus("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
				log.info("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
				imageFeatures = ImageFeatures.deserialize(featureFiles[imageIndex].toPath());
			}

			if (imageIndex == 0 || pixelType == null)
			{
				pixelType = imageFeatures.pixelType;
				selectedFeatures = getSelectedFeatures();
			}

			if (imageFeatures.pixelType != pixelType)
				throw new RuntimeException("Training images must all be either 8 or 16 bit greyscale format. "+featureFiles[imageIndex].getName()+" is "+imageFeatures.pixelType+", expected "+pixelType);

			ImageFeatures.ProgressListener progressListener = new ImageFeatures.ProgressListener()
			{
				long lastUpdate = -1;
				@Override
				public void onProgress(int current, int max, String message)
				{
					long currentTime = System.currentTimeMillis();

					statusService.showStatus(current, max, "Calculating features for image " + (currentImageIndex + 1) + "/" + numImages);
					lastUpdate = currentTime;
				}
			};

			boolean calculatedFeatures = false;
			imageFeatures.addProgressListener(progressListener);
			if(imageFeatures.calculateFeatures(0, 0, selectedFeatures))
				calculatedFeatures = true;
			imageFeatures.removeProgressListener(progressListener);

			if (calculatedFeatures && saveCalculatedFeatures)
			{
				if(!imageSurfDataPath.exists())
					imageSurfDataPath.mkdirs();

				statusService.showStatus("Writing features for image " + (currentImageIndex + 1) + "/" + numImages);
				log.info("Writing features to "+featureFiles[imageIndex].toPath());
				imageFeatures.serialize(featureFiles[imageIndex].toPath());
				log.info("Wrote features to "+featureFiles[imageIndex].toPath());
			}

			final int numFeatureImages;
			{
				int featureImageCount = 0;
				for( FeatureCalculator f : selectedFeatures)
					featureImageCount+= f.getNumImagesReturned();

				numFeatureImages = featureImageCount;
			}

			//Assumes each feature calculate only produces one
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
				featurePixels[i] = ((Object[])imageFeatures.getFeaturePixels(0, 0, selectedFeatures[i]))[0];


			statusService.showStatus("Extracting examples from image " + (currentImageIndex + 1) + "/" + numImages);

			final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);
			final int[] unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex]);
			int currentPixelClass;
			final int[] labelledPixelIndices;
			{
				final Object pixelClasses = createPixelArray(imageFeatures);

				Collection<Integer> labelledPixelIndicesList = new ArrayList<Integer>();

				for (int pixelIndex = 0; pixelIndex < labelImagePixels.length; pixelIndex++)
				{
					final int labelPixelValue = labelImagePixels[pixelIndex];
					final int unlabelledPixelValue = unlabelledImagePixels[pixelIndex];

					if (labelPixelValue != unlabelledPixelValue)
					{
						Integer boxedlabelPixelValue = new Integer(labelPixelValue);

						if(!classColors.containsKey(boxedlabelPixelValue))
							classColors.put(boxedlabelPixelValue, classColors.size());

						currentPixelClass = classColors.get(boxedlabelPixelValue);
						labelledPixelIndicesList.add(pixelIndex);
					}
					else
					{
						currentPixelClass = -1;
					}

					switch (pixelType)
					{
						case GRAY_8_BIT:
							((byte[])pixelClasses)[pixelIndex] = (byte) currentPixelClass;
							break;
						case GRAY_16_BIT:
							((short[])pixelClasses)[pixelIndex] = (short) currentPixelClass;
							break;
					}
				}

				featurePixels[featurePixels.length-1] = pixelClasses;

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



		//Sort classes in descending order by ARGB value
		{
			Map<Integer, Integer> inverseClassColors = new HashMap<>();
			for(Integer i : classColors.keySet())
				inverseClassColors.put(classColors.get(i), i);

			int[] classOrder = IntStream.range(0, classColors.keySet().size()).toArray();
			Primitive.sort(classOrder, (i, i1) -> inverseClassColors.get(i1) - inverseClassColors.get(i));

//			System.out.println("classcolors keyset");
//			for(Integer i : classColors.keySet())
//				System.out.println(Integer.toHexString(i)+" : "+classColors.get(i));
//
//			System.out.println("classorder: "+Arrays.toString(classOrder));

			int[] finalClassOrder = new int[classOrder.length];
			for(int i=0;i<classOrder.length;i++)
				finalClassOrder[classOrder[i]] = i;

			if(classOrder.length > 127)
				throw new RuntimeException("Detected "+classOrder.length+" classes. Maximum allowed is 128. Is there " +
						"a discrepancy between pixel values in the annotated and un-annotated images? This may be " +
						"caused by differing colour profiles.");

			switch (pixelType)
			{
				case GRAY_8_BIT:
					for(Object features : examples)
					{
						final byte[] pixelClasses = ((byte[][]) features)[selectedFeatures.length];

						for(int pixelIndex = 0; pixelIndex < pixelClasses.length; pixelIndex++)
							pixelClasses[pixelIndex] = (byte) finalClassOrder[pixelClasses[pixelIndex]];
					}

					break;
				case GRAY_16_BIT:
					for(Object features : examples)
					{
						final short[] pixelClasses = ((short[][]) features)[selectedFeatures.length];

						for(int pixelIndex = 0; pixelIndex < pixelClasses.length; pixelIndex++)
							pixelClasses[pixelIndex] = (short) finalClassOrder[pixelClasses[pixelIndex]];
					}
					break;
			}
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

	private Object createPixelArray(ImageFeatures imageFeatures) {
		switch (pixelType)
        {
            case GRAY_8_BIT:
                return new byte[imageFeatures.pixelsPerChannel];
            case GRAY_16_BIT:
                return new short[imageFeatures.pixelsPerChannel];
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

			final int[] unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex]);
			final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);

			if(unlabelledImagePixels.length!=labelImagePixels.length)
			{
				log.error("Un-annotated and annotated images '"+unlabelledFiles[imageIndex].getName()+"' differ in " +
						"size");
			}

			int labelCount = 0;

			for(int pixelIndex = 0; pixelIndex < unlabelledImagePixels.length;pixelIndex++)
			{
				if(labelImagePixels[pixelIndex] != unlabelledImagePixels[pixelIndex])
					labelCount++;
			}

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
		int examplePortion = prefService.getInt(ImageSurfSettings.IMAGESURF_EXAMPLE_PORTION, ImageSurfSettings.DEFAULT_EXAMPLE_PORTION);

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
			if(!labelPath.isFile() || labelPath.isHidden() || !labelPath.canRead())
				return false;

			final String imageName = labelPath.getName();

			//Check for matching image. If it doesn't exist or isn't suitable, exclude this label image
			File imagePath = new File(TrainImageSurfMultiClass.this.rawImagePath, imageName);

			if(!imagePath.exists() || imagePath.isHidden() || !imagePath.isFile() || !imageName.contains(imagePattern))
				return false;

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
