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

import imagesurf.feature.PixelType;
import imagesurf.feature.calculator.*;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.NumberWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the Plugins::Segmentation::ImageSURF::Advanced::ImageSURF Feature Selection dialog.
 *
 * @author Aidan O'Mara
 */
@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>ImageSURF>2. Select ImageSURF Features")

public class ImageSurfImageFilterSelection implements Command
{

	@Parameter
	private PrefService preferences;

	@Parameter(label = "Identity", type = ItemIO.INPUT,	description = "The original unfiltered image.")
	private boolean identity = true;

	@Parameter(label = "Mean", type = ItemIO.INPUT,	description = "The mean pixel value in the near neighborhood of the target pixel.")
	private boolean mean = true;

	@Parameter(label = "Minimum", type = ItemIO.INPUT,	description = "The minimum pixel value in the near neighborhood of the target pixel.")
	private boolean min = true;

	@Parameter(label = "Maximum", type = ItemIO.INPUT,	description = "The maximum pixel value in the near neighborhood of the target pixel.")
	private boolean max = true;

	@Parameter(label = "Median", type = ItemIO.INPUT,	description = "The median pixel value in the near neighborhood of the target pixel. " +
			"WARNING: The median filter is computationally expensive and may substantially increase the processing time, especially when calculated with a large radius.")
	private boolean median = false;

	@Parameter(label = "Standard deviation", type = ItemIO.INPUT,	description = "The standard deviation of pixel values in the near neighborhood of the target pixel.")
	private boolean standardDeviation = true;

	@Parameter(label = "Range", type = ItemIO.INPUT,	description = "The difference between the maximum and minimum pixel values in the near neighborhood of the target pixel.")
	private boolean range = true;

	@Parameter(label = "Gaussian blur", type = ItemIO.INPUT,	description = "Applies Gaussian smoothing to the " +
			"image.")
	private boolean gaussian = true;

	@Parameter(label = "Difference of Gaussians (Edge detection)", type = ItemIO.INPUT,	description = "Difference of two Gaussian filters with different sigma. ")
	private boolean differenceOfGaussians = true;

	@Parameter(label = "Difference from mean", type = ItemIO.INPUT,	description = "Difference between the target pixel value and the mean pixel value in the near neighborhood of the target pixel.")
	private boolean differenceFromMean = true;

	@Parameter(label = "Difference from min", type = ItemIO.INPUT,	description = "Difference between the target pixel value and the minimum pixel value in the near neighborhood of the target pixel.")
	private boolean differenceFromMin = true;

	@Parameter(label = "Difference from maximum", type = ItemIO.INPUT,	description = "Difference between the target pixel value and the maximum pixel value in the near neighborhood of the target pixel.")
	private boolean differenceFromMax = true;

	@Parameter(label = "Difference from median", type = ItemIO.INPUT,	description = "Difference between the target pixel value and the median pixel value in the near neighborhood of the target pixel. " +
			"WARNING: The median filter is computationally expensive and may substantially increase the processing time, especially when calculated with a large radius.")
	private boolean differenceFromMedian = false;

	@Parameter(label = "Difference from Gaussian blur", type = ItemIO.INPUT,	description = "Difference between the" +
			" target pixel value and the Gaussian filtered pixel value in the near neighborhood of the target pixel.")
	private boolean differenceFromGaussian = true;


	@Parameter(label = "Locally scaled intensity", type = ItemIO.INPUT,	description = "Scales the target pixel value to the range of the maximum and minimum pixel values in the near neighborhood of the target pixel.")
	private boolean localIntensity = true;

	@Parameter(label = "Entropy", type = ItemIO.INPUT,	description = "Entropy of the histogram of pixel values in the near neighborhood of the target pixel.")
	private boolean entropy = true;

	@Parameter(label = "Difference of Entropy", type = ItemIO.INPUT,	description = "Difference of two Entropy filters with different radii.")
	private boolean differenceOfEntropy = true;

	@Parameter(label = "Minimum filter radius",
			style = NumberWidget.SCROLL_BAR_STYLE, min = "0", max = "257",
			type = ItemIO.INPUT,
			callback = "onRadiiChanged",
			description = "The minimum radius (in pixels) to be considered for feature calculation.")
	private int minFeatureRadius = 0;

	@Parameter(label = "Maximum filter radius",
			style = NumberWidget.SCROLL_BAR_STYLE, min = "0", max = "257",
			type = ItemIO.INPUT,
			callback = "onRadiiChanged",
			initializer = "initialiseMaxFeatureRadius",
			description = "The maximum radius (in pixels) to be considered for feature calculation. WARNING: Some " +
					"large radius feature calculations may require substantial computing time.")
	private int maxFeatureRadius = 35;

	@Parameter(label = "Selected filter radii:", initializer = "onRadiiChanged", visibility = ItemVisibility.MESSAGE)
	private String selectedRadii;


	@Override
	public void run()
	{
		preferences.put(ImageSurfSettings.IMAGESURF_MIN_FEATURE_RADIUS, minFeatureRadius);
		preferences.put(ImageSurfSettings.IMAGESURF_MAX_FEATURE_RADIUS, maxFeatureRadius);

		preferences.put(ImageSurfSettings.IMAGESURF_USE_IDENTITY, identity);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_MEAN, mean);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_MIN, min);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_MAX, max);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_MEDIAN, median);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_STANDARD_DEVIATION, standardDeviation);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_RANGE, range);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_GAUSSIAN, gaussian);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_OF_GAUSSIANS, differenceOfGaussians);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MEAN, differenceFromMean);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MIN, differenceFromMin);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MAX, differenceFromMax);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MEDIAN, differenceFromMedian);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_GAUSSIAN, differenceFromGaussian);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_LOCAL_INTENSITY, localIntensity);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_ENTROPY, entropy);
		preferences.put(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_OF_ENTROPY, differenceOfEntropy);

	}

	protected void onRadiiChanged() {

		FeatureCalculator[] selectedFeatures = PixelType.GRAY_8_BIT.getAllFeatureCalculators(minFeatureRadius, maxFeatureRadius);

		List<String> radii = Arrays.stream(selectedFeatures)
				.filter(f -> !f.equals(Identity.get()))
				.map((f) -> f.getRadius())
				.distinct()
				.sorted()
				.map(Object::toString)
				.collect(Collectors.toCollection(ArrayList::new));

		selectedRadii = String.join(", ", radii);

		if(selectedRadii.isEmpty())
			selectedRadii = "NO FILTER RADII SELECTED";
	}

	static FeatureCalculator[] getFeatureCalculators(PixelType pixelType, int minFeatureRadius, int maxFeatureRadius, int numChannels, PrefService prefs)
	{
		if(null == pixelType)
			throw new IllegalArgumentException("pixelType is null.");

		List<Class> toExclude = new ArrayList<>();
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_IDENTITY, true))
			toExclude.add(Identity.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_MEAN, true))
			toExclude.add(Mean.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_MIN, true))
			toExclude.add(Min.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_MAX, true))
			toExclude.add(Max.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_MEDIAN, false))
			toExclude.add(Median.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_STANDARD_DEVIATION, true))
			toExclude.add(StandardDeviation.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_RANGE, true))
			toExclude.add(Range.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_GAUSSIAN, true))
			toExclude.add(Gaussian.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_LOCAL_INTENSITY, true))
			toExclude.add(LocalIntensity.class);
		if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_ENTROPY, false))
			toExclude.add(Entropy.class);

		FeatureCalculator[] featureCalculators = Arrays.stream(
				numChannels == 1 ? pixelType.getAllFeatureCalculators(minFeatureRadius, maxFeatureRadius) :
						pixelType.getMultiChannelFeatureCalculators(minFeatureRadius, maxFeatureRadius, numChannels))
				.filter(f -> !toExclude.contains(f.getClass()))
				.filter(f -> {
					if(f instanceof DifferenceOf)
					{
						DifferenceOf diff = (DifferenceOf) f;
						Class[] classes = diff.getFeatureCalculatorClasses();

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MEAN, true)
							&& classes[0].equals(Identity.class) && classes[1].equals(Mean.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MIN, true)
								&& classes[0].equals(Identity.class) && classes[1].equals(Min.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MAX, true)
								&& classes[0].equals(Identity.class) && classes[1].equals(Max.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_MEDIAN, false)
								&& classes[0].equals(Identity.class) && classes[1].equals(Median.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_FROM_GAUSSIAN, true)
								&& classes[0].equals(Identity.class) && classes[1].equals(Gaussian.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_OF_GAUSSIANS, true)
								&& classes[0].equals(Gaussian.class) && classes[1].equals(Gaussian.class))
							return false;

						if(!prefs.getBoolean(ImageSurfSettings.IMAGESURF_USE_DIFFERENCE_OF_ENTROPY, false)
								&& classes[0].equals(Entropy.class) && classes[1].equals(Entropy.class))
							return false;
					}

					return true;
				})
				.toArray(FeatureCalculator[]::new);

		return featureCalculators;
	}

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(ImageSurfImageFilterSelection.class, true);
	}
}