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

import java.io.File;

import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.feature.ImageFeatures;
import ij.ImagePlus;
import ij.ImageStack;
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
import imagesurf.util.Utility;

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

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String labelTrainingSet = "----- Classifier -----";

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
			final ImageStack outputStack = Utility.segmentImage(imageSurfClassifier, features, image, statusService);

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
