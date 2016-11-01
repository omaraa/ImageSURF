import java.io.File;
import java.util.concurrent.ExecutionException;

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
	menuPath = "Plugins>Segmentation>ImageSURF>Apply ImageSURF Classifier")
public class ApplyImageSurf implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter(label = "ImageSURF classifier", type = ItemIO.INPUT,
	description = "ImageSURF classifier file. If you have not yet trained a classifier, train it using the \"Train ImageSURF Classifier command")
	private File classifierFile;

	@Parameter(label = "Input image", type = ItemIO.BOTH)
	private ImagePlus image;

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
			final ImageFeatures features = new ImageFeatures(image);
			final ImageStack outputStack = ImageSurf.segmentImage(imageSurfClassifier, features, image, statusService);

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
