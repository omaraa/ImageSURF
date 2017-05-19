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

package imagesurf.classifier;


import imagesurf.feature.calculator.FeatureCalculator;
import imagesurf.feature.PixelType;

import java.io.Serializable;
import java.util.Arrays;

public class ImageSurfClassifier implements Serializable
{
	static final long serialVersionUID = 42L;

	private final FeatureCalculator[] features;
	private final int numChannels;
	private final RandomForest randomForest;
	private final PixelType pixelType;

	public ImageSurfClassifier(RandomForest randomForest, FeatureCalculator[] features, PixelType pixelType, int numChannels)
	{
		if(!randomForest.isTrained())
			throw new IllegalArgumentException("Classifier has not been trained - cannot use.");

		this.randomForest = randomForest;
		this.features = Arrays.stream(features).toArray(FeatureCalculator[]::new);
		this.pixelType = pixelType;
		this.numChannels = numChannels;
	}

	public PixelType getPixelType()
	{
		return pixelType;
	}

	public int getNumChannels()
	{
		return numChannels;
	}

	public FeatureCalculator[] getFeatures()
	{
		return Arrays.stream(features).toArray(FeatureCalculator[]::new);
	}

	public RandomForest getRandomForest()
	{
		return randomForest;
	}
}
