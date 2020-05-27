package imagesurf;

import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.util.UtilityKt;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import util.UtilityJava;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>ImageSURF>Advanced>Get Classifier Details",
		headless = true)
public class AboutImageSurfClassifier implements Command {

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelClassifier = "----- Classifier -----";

	@Parameter(label = "ImageSURF classifier", type = ItemIO.INPUT,
			description = "ImageSURF classifier file. If you have not yet trained a classifier, train it using the \"Train ImageSURF Classifier command")
	private File classifierFile;

	@Parameter(type = ItemIO.OUTPUT)
	private String ImageSurf;

	@Parameter
	private LogService log;

	@Override
	public void run() {
		try
		{
			ImageSurfClassifier classifier = (ImageSurfClassifier) UtilityJava.deserializeObject(classifierFile, true);
			ImageSurf = UtilityKt.INSTANCE.describeClassifier(classifier);
		}
		catch (Exception e)
		{
			log.error(e);

			//todo show message
			throw new RuntimeException(e);
		}

	}

	public static void main(String[] args)
	{
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(AboutImageSurfClassifier.class, true);
	}
}
