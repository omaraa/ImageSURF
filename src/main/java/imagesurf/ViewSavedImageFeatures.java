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
import imagesurf.feature.ImageFeatures;
import imagesurf.feature.PixelType;
import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.util.Utility;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import java.io.File;

import static imagesurf.feature.PixelType.GRAY_16_BIT;
import static imagesurf.feature.PixelType.GRAY_8_BIT;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>ImageSURF>Advanced>View Saved Image Features")
public class ViewSavedImageFeatures implements Command{

	@Parameter
	private StatusService statusService;

	@Parameter
	private LogService log;

	@Parameter
	private PrefService prefs;

	@Parameter(label = "Image Feature File", type = ItemIO.INPUT)
	private File inputImage;

	@Parameter(label = "Image Features", type = ItemIO.OUTPUT)
	private ImagePlus outputImage;

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(ViewSavedImageFeatures.class, true);
	}

	@Override
	public void run()
	{
		try
		{
			ImageFeatures features = (ImageFeatures) Utility.deserializeObject(inputImage, false);

			final ImageStack outputStack = new ImageStack(features.width, features.height);

			for(int z = 0; z< features.numSlices; z++)
				for(int t = 0; t< features.numFrames; t++)
				{
					for(int mergedChannelIndex = 0; mergedChannelIndex < features.numMergedChannels; mergedChannelIndex++)
						for(FeatureCalculator f : features.getFeatures())
						{
							if(!f.hasTag(ImageFeatures.FEATURE_TAG_CHANNEL_INDEX) || ((Integer) f.getTag(ImageFeatures.FEATURE_TAG_CHANNEL_INDEX)) != mergedChannelIndex)
								continue;

							switch (features.pixelType)
							{

								case GRAY_8_BIT:
									outputStack.addSlice(mergedChannelIndex+": "+f.getDescriptionWithTags(),
											((byte[][]) features.getFeaturePixels(z,t,f))[0]);
									break;
								case GRAY_16_BIT:
									outputStack.addSlice(mergedChannelIndex+": "+f.getDescriptionWithTags(),(
											(short[][]) features.getFeaturePixels(z,t,f))[0]);
									break;
								default:
									throw new RuntimeException("Unsupported pixel type: "+features.pixelType);
							}

						}

				}

			outputImage = new ImagePlus("Image Features",outputStack);
		}
		catch (Exception e)
		{
			log.error(e);

			//todo show message
			throw new RuntimeException(e);
		}
	}

}
