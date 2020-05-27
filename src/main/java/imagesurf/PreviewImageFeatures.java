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

import ij.ImagePlus;
import ij.ImageStack;
import imagesurf.feature.SurfImage;
import imagesurf.feature.PixelType;
import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.util.UtilityKt;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>ImageSURF>Advanced>Preview Image Features")
public class PreviewImageFeatures implements Command{

	@Parameter
	private StatusService statusService;

	@Parameter
	private LogService log;

	@Parameter
	private PrefService prefs;

	@Parameter(label = "Input image", type = ItemIO.BOTH)
	private ImagePlus image;

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		final Dataset dataset =
				ij.dataset().open("/home/omaraa/Downloads/imagesurf-2-channel-plaque/training/001.tif");
		ij.ui().show(dataset);

		ij.command().run(PreviewImageFeatures.class, true);
	}

	@Override
	public void run()
	{
		try
		{

			final int minFeatureRadius = prefs.getInt(ImageSurfSettings.IMAGESURF_MIN_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MIN_FEATURE_RADIUS);
			final int maxFeatureRadius = prefs.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURE_RADIUS, ImageSurfSettings.DEFAULT_MAX_FEATURE_RADIUS);

			final SurfImage features = new SurfImage(image);
			FeatureCalculator[] featureCalculators = ImageSurfImageFilterSelection.getFeatureCalculators(features.pixelType,
					minFeatureRadius, maxFeatureRadius, features.numMergedChannels, prefs);

			if(featureCalculators.length == 0)
				throw new RuntimeException("No image filters have been selected.");

			System.out.println("Selected filters: ");
			for(FeatureCalculator f : featureCalculators)
				System.out.println(f.getDescriptionWithTags());

			PixelType pixelType = UtilityKt.INSTANCE.getPixelType(image);

			final ImageStack imageStack = UtilityKt.INSTANCE.calculateImageFeatures(featureCalculators,
					features,
			statusService, pixelType);
			image.setStack(imageStack);
			image.setDimensions(1, imageStack.getSize(), 1);
		}
		catch (Exception e)
		{
			log.error(e);

			//todo show message
			throw new RuntimeException(e);
		}
	}

}
