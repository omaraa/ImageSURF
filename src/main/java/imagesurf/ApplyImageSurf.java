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
import imagesurf.feature.SurfImage;
import ij.ImagePlus;
import ij.ImageStack;
import imagesurf.segmenter.ImageSegmenter;
import imagesurf.segmenter.TiledImageSegmenter;
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
import imagesurf.util.UtilityKt;
import org.scijava.prefs.PrefService;
import util.UtilityJava;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>ImageSURF>4a. Apply ImageSURF Classifier")
public class ApplyImageSurf implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private PrefService prefService;

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
				ij.dataset().open("/home/omaraa/Downloads/imagesurf-2-channel-plaque/training/001.tif");
		ij.ui().show(dataset);

		ij.command().run(ApplyImageSurf.class, true);
	}

	public static ImageStack run(ImageSurfClassifier imageSurfClassifier, SurfImage image, StatusService statusService, int tileSize) throws Exception {
		if (imageSurfClassifier.getPixelType() != image.pixelType)
			throw new Exception("Classifier pixel type (" +
					imageSurfClassifier.getPixelType() + ") does not match image pixel type (" + image.pixelType + ")");

		if (imageSurfClassifier.getNumChannels() != image.numChannels)
			throw new Exception("Classifier trained for "+imageSurfClassifier.getNumChannels()+" channels. Image has "+image.numChannels+" - cannot segment.");

		final ImageSegmenter imageSegmenter = new TiledImageSegmenter(tileSize);

		return imageSegmenter.segmentImage(imageSurfClassifier, image, statusService);
	}

	public static ImageStack run(ImageSurfClassifier imageSurfClassifier, ImagePlus image, StatusService statusService, int tileSize) throws Exception {
		final SurfImage surfImage = new SurfImage(image);
		return run(imageSurfClassifier, surfImage, statusService, tileSize);
	}

	@Override
	public void run()
	{
		try
		{
			final int tileSize = prefService.getInt(ImageSurfSettings.IMAGESURF_TILE_SIZE, ImageSurfSettings.DEFAULT_TILE_SIZE);
			final ImageSurfClassifier imageSurfClassifier = (ImageSurfClassifier) UtilityJava.deserializeObject(classifierFile, true);
			final ImageStack outputStack = run(imageSurfClassifier, image, statusService, tileSize);
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
