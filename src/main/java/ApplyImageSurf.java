import java.io.File;

import classifier.ImageSurfClassifier;
import classifier.RandomForest;
import feature.FeatureReader;
import feature.ImageFeatures;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
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

import io.scif.services.DatasetIOService;
import org.scijava.widget.FileWidget;
import util.Utility;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>Apply ImageSURF Classifier")
public class ApplyImageSurf implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter(label = "ImageSURF Classifier Path", type = ItemIO.INPUT)
	private File classifierFile;

	@Parameter(label = "Input image", type = ItemIO.BOTH)
	private ImagePlus image;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelImageFeatures= "----- Training Image Features -----";

	@Parameter(label = "Training image features input path",
			type = ItemIO.INPUT,
			required = false,
			style= FileWidget.DIRECTORY_STYLE,
			initializer = "initialiseDefaults")
	private File featuresInputPath;

	@Parameter(label = "Training image features output path",
			type = ItemIO.INPUT,
			required = false,
			style = FileWidget.DIRECTORY_STYLE,
			initializer = "initialiseDefaults")
	private File featuresOutputPath;

	@Parameter(label = "Training image features path suffix",
			type = ItemIO.INPUT,
			required = false,
			initializer = "initialiseDefaults")
	private String featuresPathSuffix = ".features";

	void initialiseDefaults()
	{
		featuresInputPath = SyncedParameters.featuresInputPath;
		featuresOutputPath = SyncedParameters.featuresOutputPath;
		featuresPathSuffix = SyncedParameters.featuresSuffix;
		classifierFile = SyncedParameters.classifierPath;

	}

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		final Dataset dataset =
				ij.dataset().open("/Users/omaraa/Desktop/imageSURF test/images/137964 A.ome-9.tif");
		ij.ui().show(dataset);

		ij.command().run(ApplyImageSurf.class, true);
	}

	@Override
	public void run()
	{
		try
		{
			if(image.getNChannels()>1)
				throw new RuntimeException("ImageSURF does not yet support multi-channel images.");

			final ImageSurfClassifier imageSurfClassifier = (ImageSurfClassifier) Utility.deserializeObject(classifierFile, true);

			File featureOutputFile = (featuresOutputPath== null) ? null : new File(featuresOutputPath, image.getTitle()+(featuresPathSuffix == null ? "" : featuresPathSuffix));
			File featuresInputFile = (featuresInputPath == null) ? null : new File(featuresInputPath, image.getTitle()+(featuresPathSuffix == null ? "" : featuresPathSuffix));

			final ImageFeatures features = (featuresInputFile == null || !featuresInputFile.exists() || !featuresInputFile.isFile()) ? new ImageFeatures(image) : (ImageFeatures) Utility.deserializeObject(featuresInputFile, true);

			if(imageSurfClassifier.getPixelType() != features.pixelType)
				throw new RuntimeException("Classifier pixel type ("+
						imageSurfClassifier.getPixelType()+") does not match image pixel type ("+features.pixelType+")");

			final RandomForest randomForest = imageSurfClassifier.getRandomForest();
			randomForest.setNumThreads(Prefs.getThreads());


			final ImageStack outputStack = new ImageStack(image.getWidth(), image.getHeight());
			final int numPixels = image.getWidth()*image.getHeight();
			boolean featuresCalculated = false;

			//todo: merge channels in multi-channel images and expand feature set. e.g., features sets for R, G, B, RG, RB, GB and RGB
			//			for(int c = 0; c< image.getNChannels(); c++)
			int currentSlice = 1;
			int c = 0;
				for(int z = 0; z< image.getNSlices(); z++)
					for(int t = 0; t< image.getNFrames(); t++)
					{

						ImageFeatures.ProgressListener imageFeaturesProgressListener = (current, max, message) ->
								statusService.showStatus(current, max, "Calculating features for plane "+currentSlice+"/"+
								(image.getNChannels()*image.getNSlices()*image.getNFrames()));

						features.addProgressListener(imageFeaturesProgressListener);
						if(features.calculateFeatures(c, z, t, imageSurfClassifier.getFeatures()))
							featuresCalculated = true;
						features.removeProgressListener(imageFeaturesProgressListener);

						final FeatureReader featureReader = features.getReader(c, z, t, imageSurfClassifier.getFeatures());

						RandomForest.ProgressListener randomForestProgressListener = (current, max, message) ->
								statusService.showStatus(current, max, "Segmenting plane "+currentSlice+"/"+
								(image.getNChannels()*image.getNSlices()*image.getNFrames()));

						randomForest.addProgressListener(randomForestProgressListener);
						int[] classes = randomForest.classForInstances(featureReader);
						randomForest.removeprogressListener(randomForestProgressListener);
						byte[] segmentationPixels = new byte[numPixels];

						for(int i=0;i<numPixels;i++)
						{
							segmentationPixels[i] = (byte) (classes[i] == 0 ? 0 : 0xff);
						}

						outputStack.addSlice("", segmentationPixels);
					}

			if(featureOutputFile!=null && featuresCalculated)
				features.serialize(featureOutputFile.toPath());

			image.setStack(outputStack);
		}
		catch (Exception e)
		{
			log.error(e);

			//todo show message
			throw new RuntimeException(e);
		}
	}
}
