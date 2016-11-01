import classifier.ImageSurfClassifier;
import classifier.RandomForest;
import feature.FeatureReader;
import feature.ImageFeatures;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileSaver;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;
import util.Utility;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>ImageSURF>Batch Apply ImageSURF Classifier")
public class BatchApplyImageSurf implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter(label = "ImageSURF Classifier Path", type = ItemIO.INPUT,
			description = "ImageSURF classifier file. If you have not yet trained a classifier, train it using the \"Train ImageSURF Classifier command")
	private File classifierFile;

	@Parameter(label = "Input images path",
			type = ItemIO.INPUT,
			style= FileWidget.DIRECTORY_STYLE,
			description = "Folder of images to segment. ImageSURF will attempt to segment all images in this folder, unless a file name pattern is set below")
	private File imagesPath;

	@Parameter(label = "Input image file names contain",
			type = ItemIO.INPUT,
			required = false,
			description = "A pattern string to limit the input files. ONLY files that contain this exact, " +
					"case-sensitive, string will be segmented. e.g., \".tif\" will exclude all files that do not " +
					"contain \".tif\" in the file name")
	private String imagesPattern = "";

	@Parameter(label = "Segmented image output path",
			type = ItemIO.INPUT,
			style= FileWidget.DIRECTORY_STYLE,
			description = "Folder to save image segmentation output to. Segmentation output images are saved with the same name as input images. Any files with matching names WILL BE OVER-WRITTEN.")
	private File imagesOutputPath;

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(BatchApplyImageSurf.class, true);
	}

	@Override
	public void run()
	{
		if(imagesPattern == null)
			imagesPattern = "";

		final ImageSurfClassifier imageSurfClassifier;
		try
		{
			imageSurfClassifier = (ImageSurfClassifier) Utility.deserializeObject(classifierFile, true);
		}
		catch (IOException | ClassNotFoundException e)
		{
			log.error("Failed to load classifier", e);
			return;
		}

		final RandomForest randomForest = imageSurfClassifier.getRandomForest();
		randomForest.setNumThreads(Prefs.getThreads());

		File[] imageFiles = imagesPath.listFiles(new FileFilter()
		{
			@Override
			public boolean accept(File pathname)
			{
				if (pathname.isDirectory() || pathname.isHidden())
					return false;

				if (imagesPattern != null && !imagesPattern.isEmpty() && !pathname.getName().contains(imagesPattern))
					return false;

				if(pathname.getName().endsWith(".features"))
					return false;

				return true;
			}
		});

		for (int imageIndex = 0; imageIndex < imageFiles.length; imageIndex++)
		{
			File imageFile = imageFiles[imageIndex];

			try
			{
				log.info("Opening "+imageFile.getAbsolutePath());
				ImagePlus image = new ImagePlus(imageFile.getAbsolutePath());
				final String imageProgressString = " [image " + (imageIndex + 1) + "/" + imageFiles.length + "]";

				if (image.getNChannels() > 1)
					throw new RuntimeException("ImageSURF does not yet support multi-channel images.");

				File featuresInputFile = new File(imageFile.getParentFile(), imageFile.getName() + ".features");

				final ImageFeatures features;
				if (featuresInputFile == null || !featuresInputFile.exists() || !featuresInputFile.isFile())
				{
					log.info("Features "+featuresInputFile.getAbsolutePath()+" does not exist.");

					features = new ImageFeatures(image);
				}
				else
				{
					log.info("Reading features "+featuresInputFile.getAbsolutePath());
					statusService.showStatus("Reading features "+featuresInputFile.getAbsolutePath());
					features = ImageFeatures.deserialize(featuresInputFile.toPath());
				}

				if (imageSurfClassifier.getPixelType() != features.pixelType)
					throw new Exception("Classifier pixel type (" +
							imageSurfClassifier.getPixelType() + ") does not match image pixel type (" + features.pixelType + ")");

				final ImageStack outputStack = new ImageStack(image.getWidth(), image.getHeight());
				final int numPixels = image.getWidth() * image.getHeight();
				boolean featuresCalculated = false;

				//todo: merge channels in multi-channel images and expand feature set. e.g., features sets for R, G, B, RG, RB, GB and RGB
				//			for(int c = 0; c< image.getNChannels(); c++)
				int currentSlice = 1;
				int c = 0;
				for (int z = 0; z < image.getNSlices(); z++)
					for (int t = 0; t < image.getNFrames(); t++)
					{
						ImageFeatures.ProgressListener imageFeaturesProgressListener = (current, max, message) ->
								statusService.showStatus(current, max, "Calculating features for plane " + currentSlice + "/" +
										(image.getNChannels() * image.getNSlices() * image.getNFrames()) + imageProgressString);

						features.addProgressListener(imageFeaturesProgressListener);
						if (features.calculateFeatures(c, z, t, imageSurfClassifier.getFeatures()))
							featuresCalculated = true;
						features.removeProgressListener(imageFeaturesProgressListener);

						final FeatureReader featureReader = features.getReader(c, z, t, imageSurfClassifier.getFeatures());

						RandomForest.ProgressListener randomForestProgressListener = (current, max, message) ->
								statusService.showStatus(current, max, "Segmenting plane " + currentSlice + "/" +
										(image.getNChannels() * image.getNSlices() * image.getNFrames()) + imageProgressString);

						randomForest.addProgressListener(randomForestProgressListener);
						int[] classes = randomForest.classForInstances(featureReader);
						randomForest.removeprogressListener(randomForestProgressListener);
						byte[] segmentationPixels = new byte[numPixels];

						for (int i = 0; i < numPixels; i++)
						{
							segmentationPixels[i] = (byte) (classes[i] == 0 ? 0 : 0xff);
						}

						outputStack.addSlice("", segmentationPixels);
					}

				image.setStack(outputStack);

				File imageOutputFile = new File(imagesOutputPath, imageFile.getName());
				if (image.getNSlices() > 1)
					new FileSaver(image).saveAsTiffStack(imageOutputFile.getAbsolutePath());
				else
					new FileSaver(image).saveAsTiff(imageOutputFile.getAbsolutePath());
			}
			catch (Exception e)
			{
				log.error("Failed to segment image "+imageFile.getAbsolutePath(), e);
			}
		}
	}
}
