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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.classifier.RandomForest;
import imagesurf.feature.FeatureReader;
import imagesurf.feature.FeatureReaderFactory;
import imagesurf.feature.PixelType;
import imagesurf.feature.SurfImage;
import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.feature.importance.FeatureImportanceCalculator;
import imagesurf.feature.importance.ScrambleFeatureImportanceCalculator;
import imagesurf.util.ProgressListener;
import imagesurf.util.Training;
import imagesurf.util.Utility;
import net.imagej.Dataset;
import net.imagej.ImageJ;
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Plugin(type = Command.class, headless = true,
        menuPath = "Plugins>Segmentation>ImageSURF>3. Train ImageSURF Classifier")
public class TrainImageSurfMultiClass implements Command {

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

    @Parameter(label = "Raw training images path", type = ItemIO.INPUT, style = FileWidget.DIRECTORY_STYLE,
            description = "Folder of images to use as training input. Images must be single-plane greyscale images with the same bit-depth.")
    private File rawImagePath;

    @Parameter(label = "Un-annotated training images path", type = ItemIO.INPUT, style = FileWidget.DIRECTORY_STYLE,
            description = "Folder of un-annotated images to use as training input. These images should be a copy " +
                    "of the raw training images with identical names, converted to an RGB format. Brightness and " +
                    "contrast should be scaled appropriately to accurately annotate the images.")
    private File unlabelledImagePath;


    @Parameter(label = "Annotated training images path", type = ItemIO.INPUT, style = FileWidget.DIRECTORY_STYLE,
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

    @Parameter(label = "Classifier output path", type = ItemIO.INPUT, style = FileWidget.SAVE_STYLE,
            description = "Where the classifier will be saved. A \".imagesurf\" file extension is recommended.")
    private File classifierOutputPath = new File(System.getProperty("user.home"), "ImageSURF.imagesurf");

    @Parameter(label = "After training",
            choices = {AFTER_TRAINING_OPTION_NOTHING, AFTER_TRAINING_OPTION_DISPLAY, AFTER_TRAINING_OPTION_SAVE},
            visibility = ItemVisibility.INVISIBLE)
    String afterTraining;

    @Parameter(type = ItemIO.OUTPUT)
    Dataset validationImage;

    @Parameter(type = ItemIO.OUTPUT)
    String ImageSURF;

    private final ProgressListener randomForestProgressListener = (current, max, message) -> {
        if (statusService != null) statusService.showStatus(current, max, message);
    };

    private final Training.TrainingProgressListener progressListener = new Training.TrainingProgressListener() {
        @Override
        public void logInfo(String message) {
            if (log != null)
                log.info(message);
        }

        @Override
        public void logError(String message) {
            if (log != null)
                log.error(message);
        }

        @Override
        public void showStatus(int progress, int total, String message) {
            if (statusService != null)
                statusService.showStatus(progress, total, message);
        }

        @Override
        public void showStatus(String message) {
            if (statusService != null)
                statusService.showStatus(message);
        }
    };

    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = net.imagej.Main.launch(args);

        ij.command().run(TrainImageSurfMultiClass.class, true);
    }

    @Override
    public void run() {
        checkParameters();

        Training.Paths paths = new Training.Paths(labelPath, rawImagePath, unlabelledImagePath, imagePattern);
        paths.validate();

        if (saveCalculatedFeatures)
            paths.getFeaturesPath().mkdirs();

        final PixelType pixelType;
        final int numChannels;
        {
            SurfImage prototype = new SurfImage(new ImagePlus(paths.getRawImageFiles().get(0).getAbsolutePath()));
            numChannels = prototype.numChannels;
            pixelType = prototype.pixelType;
        }

        final FeatureCalculator[] selectedFeatures = getSelectedFeatures(pixelType, numChannels);
        ensureEnoughRamForFeatureImages(paths, numChannels, selectedFeatures);

        final Random random = getRandom();

        final FeatureReader reader;
        try {
            final FeatureReaderFactory readerFactory = new FeatureReaderFactory(pixelType);
            int examplePortion = prefService.getInt(ImageSurfSettings.IMAGESURF_EXAMPLE_PORTION, ImageSurfSettings.DEFAULT_EXAMPLE_PORTION);
            final Object[] trainingExamples = Training.INSTANCE.getTrainingExamples(paths,
                    random, progressListener, examplePortion, saveCalculatedFeatures,
                    pixelType, selectedFeatures);

            reader = readerFactory.getReader(trainingExamples);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get training examples", e);
        }

        if (reader.getNumInstances() == 0)
            throw new RuntimeException("No example pixels provided - cannot train classifier.");

        final int numClasses = reader.getNumClasses();

        logClassDetails(reader, numClasses);

        final RandomForest.Builder builder = getClassifierBuilder(random, selectedFeatures.length)
                .withProgressListener(randomForestProgressListener)
                .withData(reader);

        final FeatureCalculator[] optimalFeatures;
        final RandomForest randomForest;
        if (getMaxFeatures() < selectedFeatures.length && getMaxFeatures() > 0) {
            FeatureImportanceCalculator featureImportanceCalculator = new ScrambleFeatureImportanceCalculator(random.nextLong());
            optimalFeatures = featureImportanceCalculator.selectOptimalFeatures(getMaxFeatures(), reader, builder.build(), selectedFeatures, message -> {
                 log.info(message);
                 return null;
            });

            final FeatureReader optimisedFeaturesReader = Training.INSTANCE.getSelectedFeaturesReader(optimalFeatures, selectedFeatures, reader);

            randomForest = builder.withData(optimisedFeaturesReader).build();
        } else {
            optimalFeatures = selectedFeatures;
            randomForest = builder.build();
        }

        randomForest.addProgressListener(randomForestProgressListener);

        try {
            String verification = Training.INSTANCE.verifySegmentation(reader, numClasses, randomForest).describe();
            log.info(verification);
            ImageSURF = verification;
        } catch( Exception e) {
            log.error(e);
            IJ.showMessage("Failed to verify ImageSURF classifier. Check error log for details.");
        }

        randomForest.removeProgressListener(randomForestProgressListener);

        ImageSurfClassifier imageSurfClassifier = new ImageSurfClassifier(randomForest, optimalFeatures, pixelType, numChannels);
        writeClassifier(imageSurfClassifier);

        ImageSURF = "ImageSURF classifier successfully trained and saved to " + classifierOutputPath.getAbsolutePath()
                + "\n\n" + ImageSURF + "\n\n";
        ImageSURF += Utility.INSTANCE.describeClassifier(imageSurfClassifier);

        try {
            switch (afterTraining) {
                case AFTER_TRAINING_OPTION_DISPLAY:
                    segmentTrainingImagesAndDisplay(imageSurfClassifier, paths);
                    break;
                case AFTER_TRAINING_OPTION_SAVE:
                    segmentTrainingImagesAndSave(imageSurfClassifier, paths);
                    break;
                case AFTER_TRAINING_OPTION_NOTHING:
                default:
                    break;
            }
        } catch (Exception e) {
            log.error(e);
            IJ.showMessage("Failed to train ImageSURF classifier. Check error log for details.");
        }

        if (paths.getImageSurfDataPath().exists())
            IJ.showMessage("ImageSURF training data files " +
                    (AFTER_TRAINING_OPTION_SAVE.equals(afterTraining) ? "and segmented training images " : "") +
                    "have been created in the folder\n\n" + paths.getImageSurfDataPath().getAbsolutePath() + "\n\nIt is recommended " +
                    "that you delete these files after the imagesurf.classifier has been finalised to save disk space."
            );
    }

    private Random getRandom() {
        String randomSeedString = prefService.get(ImageSurfSettings.IMAGESURF_RANDOM_SEED, null);
        final Random random = (randomSeedString == null || randomSeedString.isEmpty()) ? new Random() : new Random(randomSeedString.hashCode());
        return random;
    }

    private void logClassDetails(FeatureReader reader, int numClasses) {
        int unclassified = 0;
        final int[] classCounts = new int[numClasses];
        for (short c : reader.getClasses())
            if (c < 0 || c >= 255)
                unclassified++;
            else
                classCounts[c]++;

        log.info("Class examples:");
        for (int i = 0; i < numClasses; i++)
            log.info("\t" + i + ": " + classCounts[i]);
        log.info("\tUnclassified: " + unclassified);
    }

    private void checkParameters() {
        if (imagePattern == null)
            imagePattern = "";

        if (unlabelledImagePath == null)
            unlabelledImagePath = rawImagePath;

        if (rawImagePath == null || labelPath == null)
            throw new IllegalArgumentException("Paths must be set for both raw images and annotated images.");

        if (!rawImagePath.exists())
            throw new IllegalArgumentException("Raw image path '" + rawImagePath.getAbsolutePath() + "' does not exist.");

        if (!unlabelledImagePath.exists())
            throw new IllegalArgumentException("Un-annotated image path '" + unlabelledImagePath.getAbsolutePath()
                    + "' does not exist.");

        if (!labelPath.exists())
            throw new IllegalArgumentException("Annotated image path '" + labelPath.getAbsolutePath() + "' does" +
                    " not exist.");

        if (!rawImagePath.canRead())
            throw new IllegalArgumentException("Raw image path '" + rawImagePath.getAbsolutePath() + "' can not be read.");

        if (!unlabelledImagePath.canRead())
            throw new IllegalArgumentException("Un-annotated image path '" + unlabelledImagePath.getAbsolutePath()
                    + "' can not be read.");

        if (!labelPath.canRead())
            throw new IllegalArgumentException("Annotated image path '" + labelPath.getAbsolutePath()
                    + "' can not be read.");
    }

    private FeatureCalculator[] getSelectedFeatures(PixelType pixelType, int numChannels) {
        final int numMergedChannels = Utility.INSTANCE.calculateNumMergedChannels(numChannels);

        FeatureCalculator[] selected = ImageSurfImageFilterSelection.getFeatureCalculators(
                pixelType,
                prefService.getInt(ImageSurfSettings.IMAGESURF_MIN_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MIN_FEATURE_RADIUS),
                prefService.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MAX_FEATURE_RADIUS),
                numMergedChannels,
                prefService);

        if (selected.length == 0)
            throw new RuntimeException("Cannot build imagesurf.classifier with no features.");

        return selected;
    }



    Function<ImageStack, Stream<? extends ImageProcessor>> getResizeImageFunction(final int maxWidth, final int maxHeight, final PixelType pixelType) {
        return stack -> {
            int xOffset = (maxWidth - stack.getWidth()) / 2;
            int yOffset = (maxHeight - stack.getHeight()) / 2;

            return IntStream.range(0, stack.size()).mapToObj(sliceIndex -> {
                final ImageProcessor imageProcessor;
                switch (pixelType) {
                    case GRAY_8_BIT:
                        imageProcessor = new ByteProcessor(maxWidth, maxHeight);
                        imageProcessor.insert(stack.getProcessor(sliceIndex + 1), xOffset, yOffset);
                        break;
                    case GRAY_16_BIT:
                        imageProcessor = new ShortProcessor(maxWidth, maxHeight);
                        imageProcessor.insert(stack.getProcessor(sliceIndex + 1), xOffset, yOffset);
                        break;
                    default:
                        throw new RuntimeException("Pixel type " + pixelType + "not supported.");
                }
                return imageProcessor;
            });
        };
    }

    private void segmentTrainingImagesAndDisplay(ImageSurfClassifier imageSurfClassifier, Training.Paths paths) throws Exception {
        final PixelType pixelType = imageSurfClassifier.getPixelType();

        final List<ImageStack> imageStacks = paths.getRawImageFiles().stream()
                .map( f -> new ImagePlus(f.getAbsolutePath()))
                .map(ImagePlus::getStack)
                .collect(Collectors.toList());

        final List<ImageStack> segmentationStacks = Training.INSTANCE.segmentTrainingImages(imageSurfClassifier, paths, log, prefService, statusService).stream()
                .map( f -> new ImagePlus(f.getAbsolutePath()))
                .map(ImagePlus::getStack)
                .collect(Collectors.toList());

        final int maxHeight = imageStacks.stream().mapToInt(ImageStack::getHeight).max().getAsInt();
        final int maxWidth = imageStacks.stream().mapToInt(ImageStack::getWidth).max().getAsInt();

        final ImageStack imageStack = new ImageStack(maxWidth, maxHeight);
        final ImageStack segmentationStack = new ImageStack(maxWidth, maxHeight);

        imageStacks.stream()
            .flatMap(getResizeImageFunction(maxWidth, maxHeight, pixelType))
            .forEach(imageStack::addSlice);

        segmentationStacks.stream()
                .flatMap(getResizeImageFunction(maxWidth, maxHeight, pixelType))
                .forEach(segmentationStack::addSlice);

        new ImagePlus("Segmented imagesurf.util.Training Images", segmentationStack).show();
        new ImagePlus("imagesurf.util.Training Images", imageStack).show();
    }

    private void segmentTrainingImagesAndSave(ImageSurfClassifier imageSurfClassifier, Training.Paths paths) throws Exception {
        File outputFolder = new File(paths.getImageSurfDataPath(), "segmented");
        outputFolder.mkdirs();

        List<File> segmented = Training.INSTANCE.segmentTrainingImages(imageSurfClassifier, paths, log, prefService, statusService);

        segmented.forEach(file -> {
            try {
                Files.move(file.toPath(), new File(outputFolder, file.getName()).toPath());
            } catch (IOException e) {
                log.error(e);
            }
        });
    }

    private RandomForest.Builder getClassifierBuilder(Random random, int numFeatures) {
        int numTrees = prefService.getInt(ImageSurfSettings.IMAGESURF_NUM_TREES, ImageSurfSettings.DEFAULT_NUM_TREES);
        int treeDepth = prefService.getInt(ImageSurfSettings.IMAGESURF_TREE_DEPTH, ImageSurfSettings.DEFAULT_TREE_DEPTH);
        int numAttributes = prefService.getInt(ImageSurfSettings.IMAGESURF_NUM_ATTRIBUTES, ImageSurfSettings.DEFAULT_NUM_ATTRIBUTES);
        int bagSize = prefService.getInt(ImageSurfSettings.IMAGESURF_BAG_SIZE, ImageSurfSettings.DEFAULT_BAG_SIZE);

        if (numAttributes <= 0) {
            numAttributes = (int) (Utility.INSTANCE.log2(numFeatures - 1) + 1);
            if (numAttributes <= 0)
                numAttributes = 1;
        }

        return new RandomForest.Builder()
                .withNumTrees(numTrees)
                .withMaxDepth(treeDepth)
                .withNumAttributes(numAttributes)
                .withBagSize(bagSize)
                .withRandomSeed(random.nextInt())
                .onNumThreads(Prefs.getThreads());

    }

    private int getMaxFeatures() {
        return prefService.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURES, ImageSurfSettings.DEFAULT_MAX_FEATURES);
    }

    private void writeClassifier(ImageSurfClassifier imageSurfClassifier) {

        boolean writeSuccessful = false;

        while (!writeSuccessful) {
            try {
                Utility.INSTANCE.serializeObject(imageSurfClassifier, classifierOutputPath, true);
                writeSuccessful = true;
                log.trace("Classifier saved.");
            } catch (IOException e) {
                DialogPrompt.Result result = ui.showDialog("Failed to write imagesurf.classifier to " + classifierOutputPath.getAbsolutePath() + ". Try another path?", DialogPrompt.MessageType.ERROR_MESSAGE, DialogPrompt.OptionType.OK_CANCEL_OPTION);

                switch (result) {
                    case CANCEL_OPTION:
                        if (ui.showDialog("Trained imagesurf.classifier will be lost. Are you sure?", DialogPrompt.MessageType.WARNING_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION) == DialogPrompt.Result.YES_OPTION) {
                            log.error("Failed to save imagesurf.classifier", e);
                            return;
                        }
                        break;
                    case OK_OPTION: {
                        classifierOutputPath = ui.chooseFile(classifierOutputPath, FileWidget.SAVE_STYLE);
                    }
                    break;
                }
            }
        }
    }

    private final void ensureEnoughRamForFeatureImages(Training.Paths paths, int numChannels, FeatureCalculator[] featureCalculators) {

        final Optional<Long> featureFilesSize = paths.getRawImageFiles().stream()
                .map((file) -> {
                    if(!file.canRead()) return 0L;
                    else return file.length();
                })
                .max(Long::compare);

        final long ramAvailable = Runtime.getRuntime().maxMemory();
        final long threads = Prefs.getThreads();
        final long estimatedRamRequired = (featureFilesSize.orElse(0l)/numChannels) * (featureCalculators.length+threads);

        if(estimatedRamRequired > ramAvailable) {
            final String errorMessage = String.format("Not enough memory available to calculate features. " +
                    "Estimated memory required: %,d MiB " +
                    "Memory available: %,d MiB",
                    estimatedRamRequired / (1024 * 1024),
                    ramAvailable / (1024 * 1024)
            );

            ui.showDialog(errorMessage, DialogPrompt.MessageType.ERROR_MESSAGE);
            throw new RuntimeException(errorMessage);
        }
    }
}
