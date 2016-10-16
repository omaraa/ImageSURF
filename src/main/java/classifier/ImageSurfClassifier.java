package classifier;


import feature.calculator.FeatureCalculator;
import feature.PixelType;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ImageSurfClassifier implements Serializable
{
	static final long serialVersionUID = 42L;

	private final FeatureCalculator[] features;
	private final RandomForest randomForest;
	private final PixelType pixelType;

	public ImageSurfClassifier(RandomForest randomForest, FeatureCalculator[] features, PixelType pixelType)
	{
		if(!randomForest.isTrained())
			throw new IllegalArgumentException("Classifier has not been trained - cannot use.");

		this.randomForest = randomForest;
		this.features = Arrays.stream(features).toArray(FeatureCalculator[]::new);
		this.pixelType = pixelType;
	}

	public PixelType getPixelType()
	{
		return pixelType;
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
