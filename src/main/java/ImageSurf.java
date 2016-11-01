import classifier.ImageSurfClassifier;
import classifier.RandomForest;
import feature.FeatureReader;
import feature.ImageFeatures;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import org.scijava.app.StatusService;

import java.util.concurrent.ExecutionException;

public class ImageSurf
{
	protected static ImageStack segmentImage(ImageSurfClassifier imageSurfClassifier, ImageFeatures features, ImagePlus image, StatusService statusService) throws ExecutionException, InterruptedException
	{
		if(imageSurfClassifier.getPixelType() != features.pixelType)
			throw new RuntimeException("Classifier pixel type ("+
					imageSurfClassifier.getPixelType()+") does not match image pixel type ("+features.pixelType+")");

		final RandomForest randomForest = imageSurfClassifier.getRandomForest();
		randomForest.setNumThreads(Prefs.getThreads());


		final ImageStack outputStack = new ImageStack(image.getWidth(), image.getHeight());
		final int numPixels = image.getWidth()*image.getHeight();

		//todo: merge channels in multi-channel images and expand feature set. e.g., features sets for R, G, B, RG, RB, GB and RGB
		//			for(int c = 0; c< image.getNChannels(); c++)
		int currentSlice = 1;
		int c = 0;
		for(int z = 0; z< image.getNSlices(); z++)
			for(int t = 0; t< image.getNFrames(); t++)
			{

				ImageFeatures.ProgressListener imageFeaturesProgressListener = (current, max, message) ->
						statusService.showStatus(current, max, "Calculating features for plane "+currentSlice+"/"+
								(image.getNChannels()*image.getNSlices()*image.getNFrames()));

				features.addProgressListener(imageFeaturesProgressListener);
				if(features.calculateFeatures(c, z, t, imageSurfClassifier.getFeatures()))
					features.removeProgressListener(imageFeaturesProgressListener);

				final FeatureReader featureReader = features.getReader(c, z, t, imageSurfClassifier.getFeatures());

				RandomForest.ProgressListener randomForestProgressListener = (current, max, message) ->
						statusService.showStatus(current, max, "Segmenting plane "+currentSlice+"/"+
								(image.getNChannels()*image.getNSlices()*image.getNFrames()));

				randomForest.addProgressListener(randomForestProgressListener);
				int[] classes = randomForest.classForInstances(featureReader);
				randomForest.removeprogressListener(randomForestProgressListener);
				byte[] segmentationPixels = new byte[numPixels];

				for(int i=0;i<numPixels;i++)
				{
					segmentationPixels[i] = (byte) (classes[i] == 0 ? 0 : 0xff);
				}

				outputStack.addSlice("", segmentationPixels);
			}
		return outputStack;
	}

}
