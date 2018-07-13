package imagesurf.util;

import ij.CompositeImage;
import ij.ImagePlus;
import imagesurf.feature.FeatureReader;
import imagesurf.feature.ImageFeatures;
import imagesurf.feature.PixelType;
import imagesurf.feature.calculator.FeatureCalculator;
import net.mintern.primitive.Primitive;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

public class Training {
    private static <T> int indexOf(T[] haystack, T needle) {
        for(int i = 0; i< haystack.length; i++)
            if(haystack[i].equals(needle))
                return i;

        return -1;
    }

    public static FeatureReader getSelectedFeaturesReader(FeatureCalculator[] optimalFeatures, FeatureCalculator[] allFeatures, Object[] trainingExamples, PixelType pixelType) {

        final int numFeatures = optimalFeatures.length;

        final Object[] optimisedTrainingExamples;
        switch (pixelType) {
            case GRAY_8_BIT:
                optimisedTrainingExamples = new byte[numFeatures + 1][];
                break;
            case GRAY_16_BIT:
                optimisedTrainingExamples = new short[numFeatures + 1][];
                break;
            default:
                throw new RuntimeException("Pixel type " + pixelType + " not supported");
        }
        for (int i = 0; i < numFeatures; i++)
            optimisedTrainingExamples[i] = trainingExamples[indexOf(allFeatures, optimalFeatures[i])];

        System.out.println("Selected: asdfdgf");
        for (int i = 0; i < numFeatures; i++)
            System.out.println(i+": "+allFeatures[indexOf(allFeatures, optimalFeatures[i])].getDescriptionWithTags());

        //Add class annotations
        optimisedTrainingExamples[numFeatures] = trainingExamples[trainingExamples.length - 1];

        switch (pixelType) {
            case GRAY_8_BIT:
                return new ImageFeatures.ByteReader((byte[][]) optimisedTrainingExamples, numFeatures);
            case GRAY_16_BIT:
                return new ImageFeatures.ShortReader((short[][]) optimisedTrainingExamples, numFeatures);
            default:
                throw new RuntimeException("Pixel type " + pixelType + " not supported.");
        }
    }

    public static Object[] getTrainingExamples(File[] labelFiles, File[] unlabelledFiles, File[] rawImageFiles,
                                               File[] featureFiles, File imageSurfDataPath, Random random,
                                               ProgressListener progressListener, int examplePortion,
                                               boolean saveCalculatedFeatures, PixelType pixelType,
                                               FeatureCalculator[] selectedFeatures)  {
        if(progressListener == null)
            progressListener = ProgressListener.getDummy();

        if (labelFiles.length == 0)
            throw new RuntimeException("No valid label files");

        final int numImages = labelFiles.length;
        int[] numLabelledPixels = countLabelledPixels(labelFiles, unlabelledFiles, progressListener);
        int totalLabelledPixels = Utility.sum(numLabelledPixels);
        final Collection<Object[]> examples = new ArrayList<>();

        if (totalLabelledPixels == 0)
            throw new RuntimeException("No labels found in label files");

        final int[] examplePixelIndices = selectExamplePixelIndices(totalLabelledPixels, random, examplePortion);

        final Map<Integer, Integer> classColors = new HashMap<>();

        int currentImageFirstExampleIndex = 0;
        for (int imageIndex = 0; imageIndex < numImages; imageIndex++) {
            if (numLabelledPixels[imageIndex] == 0)
                continue;

            final int currentImageIndex = imageIndex;
            final int firstExampleIndex = currentImageFirstExampleIndex;

            final ImagePlus rawImage = getImagePlus(rawImageFiles[imageIndex]);

            if (rawImage.getNFrames() * rawImage.getNSlices() > 1)
                throw new RuntimeException("Training image " + rawImage.getTitle() + " not valid. Images must be single plane.");

            final int numChannels = rawImage.getNChannels();

//            if (rawImage.getNChannels() != numChannels)
//                throw new RuntimeException("Training image " + rawImage.getTitle() + " not valid. Image has " + rawImage.getNChannels() + " channels. Expected " + numChannels + ".");

            final ImageFeatures imageFeatures;
            final Collection<FeatureCalculator> savedFeatures;
            if (featureFiles[imageIndex] == null || !featureFiles[imageIndex].exists()) {
                progressListener.logInfo("Reading image " + (currentImageIndex + 1) + "/" + numImages);
                imageFeatures = new ImageFeatures(rawImage);
                savedFeatures = new ArrayList<>(0);
            } else {
                progressListener.showStatus("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
                progressListener.logInfo("Reading features for image " + (currentImageIndex + 1) + "/" + numImages);
                try {
                    imageFeatures = ImageFeatures.deserialize(featureFiles[imageIndex].toPath());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to read image features", e);
                }
                savedFeatures = imageFeatures.getFeatures();
            }

            if (imageFeatures.pixelType != pixelType)
                throw new RuntimeException("Training images must all be either 8 or 16 bit greyscale format. " + featureFiles[imageIndex].getName() + " is " + imageFeatures.pixelType + ", expected " + pixelType);

            final ImageFeatures.ProgressListener ifProgressListener;
            {
                final ProgressListener finalProgressListener = progressListener;
                ifProgressListener =new ImageFeatures.ProgressListener() {
                long lastUpdate = -1;

                @Override
                public void onProgress(int current, int max, String message) {
                    long currentTime = System.currentTimeMillis();

                    finalProgressListener.showStatus(current, max, "Calculating features for image " + (currentImageIndex + 1) + "/" + numImages);
                    lastUpdate = currentTime;
                }
            };

            }
            boolean calculatedFeatures = false;
            imageFeatures.addProgressListener(ifProgressListener);

            try {
                if (imageFeatures.calculateFeatures(0, 0, selectedFeatures))
                    calculatedFeatures = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to calculate features", e);
            }
            imageFeatures.removeProgressListener(ifProgressListener);

            if (calculatedFeatures && saveCalculatedFeatures && !savedFeatures.containsAll(imageFeatures.getEasilyComputedFeatures())) {
                if (!imageSurfDataPath.exists())
                    imageSurfDataPath.mkdirs();

                progressListener.showStatus("Writing features for image " + (currentImageIndex + 1) + "/" + numImages);
                progressListener.logInfo("Writing features to " + featureFiles[imageIndex].toPath());
                try {
                    imageFeatures.serialize(featureFiles[imageIndex].toPath());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to save features to file "+featureFiles[imageIndex].getAbsolutePath(), e);
                }
                progressListener.logInfo("Wrote features to " + featureFiles[imageIndex].toPath());
            }

            //Assumes each feature calculate only produces one
            // feature image, but future ones may produce more.
            Object[] featurePixels;
            switch (pixelType) {
                case GRAY_8_BIT:
                    featurePixels = new byte[selectedFeatures.length + 1][];
                    break;
                case GRAY_16_BIT:
                    featurePixels = new short[selectedFeatures.length + 1][];
                    break;

                default:
                    throw new RuntimeException("Pixel type " + pixelType + " not supported.");
            }
            for (int i = 0; i < selectedFeatures.length; i++)
                featurePixels[i] = ((Object[]) imageFeatures.getFeaturePixels(0, 0, selectedFeatures[i]))[0];


            progressListener.showStatus("Extracting examples from image " + (currentImageIndex + 1) + "/" + numImages);

            final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);
            final int[] unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex]);
            int currentPixelClass;
            final int[] labelledPixelIndices;
            {
                final Object pixelClasses = createPixelArray(imageFeatures, pixelType);

                Collection<Integer> labelledPixelIndicesList = new ArrayList<Integer>();

                for (int pixelIndex = 0; pixelIndex < labelImagePixels.length; pixelIndex++) {
                    final int labelPixelValue = labelImagePixels[pixelIndex];
                    final int unlabelledPixelValue = unlabelledImagePixels[pixelIndex];

                    if (labelPixelValue != unlabelledPixelValue) {
                        Integer boxedlabelPixelValue = new Integer(labelPixelValue);

                        if (!classColors.containsKey(boxedlabelPixelValue))
                            classColors.put(boxedlabelPixelValue, classColors.size());

                        currentPixelClass = classColors.get(boxedlabelPixelValue);
                        labelledPixelIndicesList.add(pixelIndex);
                    } else {
                        currentPixelClass = -1;
                    }

                    switch (pixelType) {
                        case GRAY_8_BIT:
                            ((byte[]) pixelClasses)[pixelIndex] = (byte) currentPixelClass;
                            break;
                        case GRAY_16_BIT:
                            ((short[]) pixelClasses)[pixelIndex] = (short) currentPixelClass;
                            break;
                    }
                }

                featurePixels[featurePixels.length - 1] = pixelClasses;

                labelledPixelIndices = labelledPixelIndicesList.stream().mapToInt(Integer::intValue).toArray();
            }

            int[] selectedPixels = Arrays.stream(examplePixelIndices)
                    .filter(i -> i >= firstExampleIndex && i < firstExampleIndex + numLabelledPixels[currentImageIndex])
                    .map(i -> labelledPixelIndices[i - firstExampleIndex])
                    .toArray();
            currentImageFirstExampleIndex += numLabelledPixels[currentImageIndex];

            switch (pixelType) {
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
            for (Integer i : classColors.keySet())
                inverseClassColors.put(classColors.get(i), i);

            int[] classOrder = IntStream.range(0, classColors.keySet().size()).toArray();
            Primitive.sort(classOrder, (i, i1) -> inverseClassColors.get(i1) - inverseClassColors.get(i));

            int[] finalClassOrder = new int[classOrder.length];
            for (int i = 0; i < classOrder.length; i++)
                finalClassOrder[classOrder[i]] = i;

            if (classOrder.length > 127)
                throw new RuntimeException("Detected " + classOrder.length + " classes. Maximum allowed is 128. Is there " +
                        "a discrepancy between pixel values in the annotated and un-annotated images? This may be " +
                        "caused by differing colour profiles.");

            switch (pixelType) {
                case GRAY_8_BIT:
                    for (Object features : examples) {
                        final byte[] pixelClasses = ((byte[][]) features)[selectedFeatures.length];

                        for (int pixelIndex = 0; pixelIndex < pixelClasses.length; pixelIndex++)
                            pixelClasses[pixelIndex] = (byte) finalClassOrder[pixelClasses[pixelIndex]];
                    }

                    break;
                case GRAY_16_BIT:
                    for (Object features : examples) {
                        final short[] pixelClasses = ((short[][]) features)[selectedFeatures.length];

                        for (int pixelIndex = 0; pixelIndex < pixelClasses.length; pixelIndex++)
                            pixelClasses[pixelIndex] = (short) finalClassOrder[pixelClasses[pixelIndex]];
                    }
                    break;
            }
        }

        switch (pixelType) {
            case GRAY_8_BIT:
                Collection<byte[][]> byteExamples = new ArrayList<>();
                for (Object be : examples)
                    byteExamples.add((byte[][]) be);
                return mergeByteImageFeatures(byteExamples);
            case GRAY_16_BIT:
                Collection<short[][]> shortExamples = new ArrayList<>();
                for (Object be : examples)
                    shortExamples.add((short[][]) be);
                return mergeShortImageFeatures(shortExamples);
            default:
                throw new RuntimeException("Pixel type " + pixelType + " not supported.");
        }
    }

    private static ImagePlus getImagePlus(File rawImageFile) {
        final ImagePlus rawImage;
        {
            ImagePlus imagePlus = new ImagePlus(rawImageFile.getAbsolutePath());

            if (imagePlus.getType() == ImagePlus.COLOR_RGB) {
                if (Utility.isGrayScale(imagePlus)) {
                    rawImage = new ImagePlus(imagePlus.getTitle(), imagePlus.getChannelProcessor());
                } else {
                    rawImage = new CompositeImage(imagePlus, CompositeImage.GRAYSCALE);
                }
            } else
                rawImage = imagePlus;
        }
        return rawImage;
    }

    private static Object createPixelArray(ImageFeatures imageFeatures, PixelType pixelType) {
        switch (pixelType) {
            case GRAY_8_BIT:
                return new byte[imageFeatures.pixelsPerChannel];
            case GRAY_16_BIT:
                return new short[imageFeatures.pixelsPerChannel];
            default:
                throw new RuntimeException("Pixel type " + pixelType + " not supported.");
        }
    }

    private static int[] countLabelledPixels(File[] labelFiles, File[] unlabelledFiles, ProgressListener progressListener) {
        if(progressListener == null)
            progressListener = ProgressListener.getDummy();

        final int numImages = labelFiles.length;
        final int[] numLabelledPixels = new int[numImages];

        for (int imageIndex = 0; imageIndex < numImages; imageIndex++) {
            progressListener.showStatus(imageIndex + 1, numImages, "Scanning image labels " + (imageIndex + 1) + "/" + numImages);

            final int[] unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex]);
            final int[] labelImagePixels = getLabelImagePixels(labelFiles[imageIndex]);

            if (unlabelledImagePixels.length != labelImagePixels.length) {
                progressListener.logError("Un-annotated and annotated images '" + unlabelledFiles[imageIndex].getName() + "' differ in " +
                        "size");
            }

            int labelCount = 0;

            for (int pixelIndex = 0; pixelIndex < unlabelledImagePixels.length; pixelIndex++) {
                if (labelImagePixels[pixelIndex] != unlabelledImagePixels[pixelIndex])
                    labelCount++;
            }

            numLabelledPixels[imageIndex] = labelCount;
        }

        return numLabelledPixels;
    }

    private static int[] getLabelImagePixels(File labelFile) {
        final ImagePlus labelImage = new ImagePlus(labelFile.getAbsolutePath());
        if (!(labelImage.getNChannels() == 1 || labelImage.getNChannels() == 3) || labelImage.getNFrames() * labelImage.getNSlices() > 1)
            throw new RuntimeException("Label image " + labelImage.getTitle() + " not valid. Label images must be single plane RGB format.");

        return (int[]) labelImage
                .getProcessor().convertToRGB().getPixels();
    }

    private static int[] selectExamplePixelIndices(int totalLabelledPixels, Random random, int examplePortion) {
        final int[] examplePixelIndices;
        if (examplePortion < 100) {
            examplePixelIndices = new int[(totalLabelledPixels * examplePortion) / 100];
            for (int i = 0; i < examplePixelIndices.length; i++)
                examplePixelIndices[i] = random.nextInt(totalLabelledPixels);

            Arrays.sort(examplePixelIndices);
        } else {
            examplePixelIndices = IntStream.range(0, totalLabelledPixels).toArray();
        }

        return examplePixelIndices;
    }

    private static void subsetInstancesDestructive(short[][] features, int[] indicesToKeep) {
        for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
            short[] feature = features[featureIndex];

            short[] featureSubset = new short[indicesToKeep.length];

            int numKept = 0;
            for (int i : indicesToKeep)
                featureSubset[numKept++] = feature[i];

            features[featureIndex] = featureSubset;
        }
    }

    private static void subsetInstancesDestructive(byte[][] features, int[] indicesToKeep) {
        for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
            byte[] feature = features[featureIndex];

            byte[] featureSubset = new byte[indicesToKeep.length];

            int numKept = 0;
            for (int i : indicesToKeep)
                featureSubset[numKept++] = feature[i];

            features[featureIndex] = featureSubset;
        }
    }

    /**
     * Merge collection of image feature arrays into one. Input arrays are nullified to free memory.
     *
     * @param imageFeatures
     * @return
     */
    static short[][] mergeShortImageFeatures(Collection<short[][]> imageFeatures) {
        int numFeatures = imageFeatures.iterator().next().length;

        int numPixels = 0;
        for (short[][] image : imageFeatures) {
            numPixels += image[0].length;
        }

        short[][] merged = new short[numFeatures][];

        for (int featureIndex = 0; featureIndex < numFeatures; featureIndex++) {
            short[] mergedFeature = new short[numPixels];
            int numMerged = 0;
            for (short[][] image : imageFeatures) {
                short[] feature = image[featureIndex];
                System.arraycopy(feature, 0, mergedFeature, numMerged, feature.length);

                numMerged += feature.length;

                image[featureIndex] = null;
            }

            merged[featureIndex] = mergedFeature;
        }

        return merged;
    }

    /**
     * Merge collection of image feature arrays into one. Input arrays are nullified to free memory.
     *
     * @param imageFeatures
     * @return
     */
    static byte[][] mergeByteImageFeatures(Collection<byte[][]> imageFeatures) {
        int numFeatures = imageFeatures.iterator().next().length;

        int numPixels = 0;
        for (byte[][] image : imageFeatures) {
            numPixels += image[0].length;
        }

        byte[][] merged = new byte[numFeatures][];

        for (int featureIndex = 0; featureIndex < numFeatures; featureIndex++) {
            byte[] mergedFeature = new byte[numPixels];
            int numMerged = 0;
            for (byte[][] image : imageFeatures) {
                byte[] feature = image[featureIndex];
                System.arraycopy(feature, 0, mergedFeature, numMerged, feature.length);

                numMerged += feature.length;

                image[featureIndex] = null;
            }

            merged[featureIndex] = mergedFeature;
        }

        return merged;
    }

    public interface ProgressListener {
        void logInfo(String Message);
        void logError(String message);
        void showStatus(int progress, int total, String message);
        void showStatus(String message);

        static ProgressListener getDummy() {
           return new  ProgressListener() {
                @Override
                public void logInfo(String Message) {

                }

                @Override
                public void logError(String message) {

                }

                @Override
                public void showStatus(int progress, int total, String message) {

                }

               @Override
               public void showStatus(String message) {

               }

           };
        }
    }
}
