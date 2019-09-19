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
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.NumberWidget;

/**
 * Runs the Plugins::Segmentation::ImageSURF::Advanced::ImageSURF Classifier Settings dialog.
 *
 * @author Aidan O'Mara
 */
@Plugin(type = Command.class, menuPath = "Plugins>Segmentation>ImageSURF>1. ImageSURF Classifier Settings")

public class ImageSurfSettings implements Command
{

	public static final int DEFAULT_BAG_SIZE = 30;
	public static final int DEFAULT_EXAMPLE_PORTION = 100;
	public static final int DEFAULT_TREE_DEPTH = 30;
	public static final int DEFAULT_NUM_TREES = 100;
	public static final int DEFAULT_NUM_ATTRIBUTES = 0;
	public static final int DEFAULT_MIN_FEATURE_RADIUS = 0;
	public static final int DEFAULT_MAX_FEATURE_RADIUS = 35;
	public static final int DEFAULT_MAX_FEATURES = PixelType.GRAY_8_BIT.getAllFeatureCalculators(DEFAULT_MIN_FEATURE_RADIUS, DEFAULT_MAX_FEATURE_RADIUS, 1).length;


	public static final String IMAGESURF_BAG_SIZE = "ImageSURF Bag Size";
	public static final String IMAGESURF_EXAMPLE_PORTION = "ImageSURF Example Portion";
	public static final String IMAGESURF_TREE_DEPTH = "ImageSURF Tree Depth";
	public static final String IMAGESURF_NUM_TREES = "ImageSURF Num Trees";
	public static final String IMAGESURF_NUM_ATTRIBUTES = "ImageSURF Num Attributes";
	public static final String IMAGESURF_RANDOM_SEED = "ImageSURF Random Seed";
	public static final String IMAGESURF_MAX_FEATURES = "ImageSURF Max Features";
	public static final String IMAGESURF_MIN_FEATURE_RADIUS = "ImageSURF Min Feature Radius";
	public static final String IMAGESURF_MAX_FEATURE_RADIUS = "ImageSURF Max Feature Radius";

	public static final String IMAGESURF_USE_IDENTITY = "ImageSURF use identity";
	public static final String IMAGESURF_USE_MEAN = "ImageSURF use mean";
	public static final String IMAGESURF_USE_MIN = "ImageSURF use min";
	public static final String IMAGESURF_USE_MAX = "ImageSURF use max";
	public static final String IMAGESURF_USE_MEDIAN = "ImageSURF use median";
	public static final String IMAGESURF_USE_STANDARD_DEVIATION = "ImageSURF use standard deviation";
	public static final String IMAGESURF_USE_RANGE = "ImageSURF use range";
	public static final String IMAGESURF_USE_GAUSSIAN = "ImageSURF use gaussian";
	public static final String IMAGESURF_USE_DIFFERENCE_OF_GAUSSIANS = "ImageSURF use difference of gaussians";
	public static final String IMAGESURF_USE_DIFFERENCE_FROM_MEAN = "ImageSURF use difference from mean";
	public static final String IMAGESURF_USE_DIFFERENCE_FROM_MIN = "ImageSURF use difference from min";
	public static final String IMAGESURF_USE_DIFFERENCE_FROM_MAX = "ImageSURF use difference from max";
	public static final String IMAGESURF_USE_DIFFERENCE_FROM_MEDIAN = "ImageSURF use difference from median";
	public static final String IMAGESURF_USE_LOCAL_INTENSITY = "ImageSURF use local intensity";
	public static final String IMAGESURF_USE_ENTROPY = "ImageSURF use entropy";
	public static final String IMAGESURF_USE_DIFFERENCE_OF_ENTROPY = "ImageSURF use difference of entropy";
	public static final String IMAGESURF_USE_DIFFERENCE_FROM_GAUSSIAN = "ImageSURF use difference from gaussian";

	@Parameter
	private PrefService preferences;

	@Parameter(label = "Number of trees", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "1", initializer = "initialiseValues",
			description = "The number of random trees to build for the classifier. More trees will result in a" +
					"more robust classifier, but require more computing time to build.")
	private int numTrees = DEFAULT_NUM_TREES;

	@Parameter(label = "Maximum tree depth", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "0", initializer = "initialiseValues",
			description = "The maximum depth of random trees in the classifier. Deeper trees can capture more subtle " +
					"information, but are more likely to over-fit to the training input.")
	private int treeDepth = DEFAULT_TREE_DEPTH;

	@Parameter(label = "Feature choices per branch",
			type = ItemIO.INPUT,
			initializer = "initialiseValues",
			callback = "onFeatureChoicesChanged",
			description = "The number of randomly selected features to consider at each decision branch, less features " +
					"results in a more random (and likely more robust) imagesurf.classifier, but more features results in a more " +
					"concise tree. If zero is selected the number of features will be calculated as" +
					" log2(numFeatures - 1) + 1.")
	private int numAttributes = DEFAULT_NUM_ATTRIBUTES;

	@Parameter(label = "Bag size (%)", type = ItemIO.INPUT,
			style = NumberWidget.SCROLL_BAR_STYLE, min = "1", max = "100", initializer = "initialiseValues",
			description = "The bag size (number of examples) to use as training input to each random tree in the " +
					"imagesurf.classifier. Smaller bag sizes reduce the likelihood of over-fitting to the training set, but may " +
					"also reduce the iclassifier accuracy.")
	private int bagSize = DEFAULT_BAG_SIZE;

	@Parameter(label = "Training examples to consider (%)", type = ItemIO.INPUT,
			style = NumberWidget.SCROLL_BAR_STYLE, min = "1", max = "100", initializer = "initialiseValues",
			description = "The number of training examples to consider. If less than 100%, example pixels are selected " +
					"randomly with replacement. Reducing the number of training examples will reduce computation time, " +
					"and may be necessary to prevent 'out-of-memory errors' with large densely annotated training images " +
					"sets.")
	private int examplePortion = DEFAULT_EXAMPLE_PORTION;

	@Parameter(label = "Maximum features", type = ItemIO.INPUT,
			style = NumberWidget.SPINNER_STYLE, min = "0", callback = "onMaxFeaturesChanged",
			initializer = "initialiseValues",
			description = "The maximum number of features to use in the ImageSURF classifier. If the selected value is " +
					"less than the maximum number of features, a classifier will be trained with all selected features, " +
					"the importance of each feature calculated and only the most important features used to re-train " +
					"the classifier. Less features will result in less computation to calculate the features when " +
					"applying the ImageSURF classifier, but too few features may result in unsatisfactory classification " +
					"accuracy. If 0 is selected, all features will be used.")
	private int maxFeatures = DEFAULT_MAX_FEATURES;

	@Parameter(label = "Random seed", type = ItemIO.INPUT, required = false,
			description = "A random seed to ensure repeatability when training ImageSURF classifiers. A seed will " +
					"be randomly created if none is entered.")
	private String randomSeedString = "";

	protected void initialiseValues()
	{
		numTrees = preferences.getInt(ImageSurfSettings.IMAGESURF_NUM_TREES, ImageSurfSettings.DEFAULT_NUM_TREES);
		treeDepth = preferences.getInt(ImageSurfSettings.IMAGESURF_TREE_DEPTH, ImageSurfSettings.DEFAULT_TREE_DEPTH);
		numAttributes = preferences.getInt(ImageSurfSettings.IMAGESURF_NUM_ATTRIBUTES, ImageSurfSettings.DEFAULT_NUM_ATTRIBUTES);
		bagSize = preferences.getInt(ImageSurfSettings.IMAGESURF_BAG_SIZE, ImageSurfSettings.DEFAULT_BAG_SIZE);
		maxFeatures = preferences.getInt(ImageSurfSettings.IMAGESURF_MAX_FEATURES, ImageSurfSettings.DEFAULT_MAX_FEATURES);
		randomSeedString= preferences.get(ImageSurfSettings.IMAGESURF_RANDOM_SEED, null);
		examplePortion = preferences.getInt(ImageSurfSettings.IMAGESURF_EXAMPLE_PORTION, ImageSurfSettings.DEFAULT_EXAMPLE_PORTION);
	}

	@Override
	public void run()
	{
		preferences.put(IMAGESURF_BAG_SIZE, bagSize);
		preferences.put(IMAGESURF_EXAMPLE_PORTION, examplePortion);
		preferences.put(IMAGESURF_TREE_DEPTH, treeDepth);
		preferences.put(IMAGESURF_NUM_TREES, numTrees);
		preferences.put(IMAGESURF_NUM_ATTRIBUTES, numAttributes);
		preferences.put(IMAGESURF_RANDOM_SEED, randomSeedString == null ? "" : randomSeedString);
		preferences.put(IMAGESURF_MAX_FEATURES, maxFeatures);
	}

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(ImageSurfSettings.class, true);
	}
}