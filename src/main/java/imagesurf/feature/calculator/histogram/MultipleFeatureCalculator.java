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

package imagesurf.feature.calculator.histogram;

import imagesurf.feature.calculator.FeatureCalculator;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class MultipleFeatureCalculator extends NeighbourhoodHistogramCalculator implements Serializable
{
	static final long serialVersionUID = 42L;
	
	private final NeighbourhoodHistogramCalculator[] features;
	private final int numImagesReturned;

	public MultipleFeatureCalculator(NeighbourhoodHistogramCalculator[] features)
	{
		this(Arrays.copyOf(features, features.length),
				Arrays.stream(features).mapToInt(NeighbourhoodHistogramCalculator::getNumImagesReturned).sum(),
				findRadius(features) );
	}

	public MultipleFeatureCalculator(Collection<NeighbourhoodHistogramCalculator> features)
	{
		this(features.toArray(new NeighbourhoodHistogramCalculator[0]));
	}

	private MultipleFeatureCalculator(NeighbourhoodHistogramCalculator[] features, int numImagesReturned, int radius) {
		super(radius);
		this.features = features;
		this.numImagesReturned = numImagesReturned;

		features[0].tags.forEach(super::setTag);

		for(NeighbourhoodHistogramCalculator f : features) {
			if(f.tags.size() != tags.size())
				throw new IllegalArgumentException("All features must have the same tags");

			f.tags.forEach((k, v) -> {
				if (!getTag(k).equals(v))
					throw new IllegalArgumentException("All features must have the same tags");
			});
		}
	}

	public NeighbourhoodHistogramCalculator[] getFeatures() {
		return features;
	}
	
	private static int findRadius(NeighbourhoodHistogramCalculator[] features) {
		final int[] radii = Arrays.stream(features).mapToInt(NeighbourhoodHistogramCalculator::getRadius).distinct().toArray();
		
		if(radii.length != 1)
			throw new IllegalArgumentException("Features must have the same radius for multiple calculation");
		
		return radii[0];
	}

	@Override
	protected Calculator getCalculator(final PixelReader reader, final int numBins) {

		return new Calculator() {

			final Calculator[] calculators = Arrays.stream(features)
					.map( f -> f.getCalculator(reader, numBins))
					.toArray(Calculator[]::new);

			@Override
			public int[] calculate(PixelWindow pixelWindow) {
				final int[] outputBuffer = new int[numImagesReturned];

				int outIndex = 0;
				for(Calculator c : calculators)
					for(int v : c.calculate(pixelWindow))
						outputBuffer[outIndex++] = v;

				return outputBuffer;
			}
		};
	}

	@Override
	public String getName()
	{
		return "Multiple ["+ Arrays.stream(features).map(FeatureCalculator::getName).collect(Collectors.joining(", "))+"]";
	}

	@Override
	public int getNumImagesReturned() {
		return numImagesReturned;
	}

	@Override
	public String[] getResultDescriptions() {
		return Arrays.stream(features).flatMap(f -> Arrays.stream(f.getResultDescriptions())).toArray(String[]::new);
	}

	@Override
	public void setTag(String tagName, Object tagValue) {
		throw new RuntimeException("Cannot set tag on MultipleFeatureCalculator");
	}

	@Override
	public void removeTag(String tagName) {
		throw new RuntimeException("Cannot remove tag from MultipleFeatureCalculator");
	}

	@Override
	public void removeTags(Collection<String> tagNames) {
		throw new RuntimeException("Cannot remove tags from MultipleFeatureCalculator");
	}

	@Override
	public FeatureCalculator duplicate()
	{
		return new MultipleFeatureCalculator(features, numImagesReturned, getRadius());
	}
}