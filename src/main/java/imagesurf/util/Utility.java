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

package imagesurf.util;

import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.classifier.RandomForest;
import imagesurf.feature.FeatureReader;
import imagesurf.feature.ImageFeatures;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import imagesurf.feature.calculator.FeatureCalculator;
import org.scijava.app.StatusService;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.*;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utility
{
	public static boolean isGrayScale(ImagePlus imagePlus)
	{
		if(imagePlus.getNChannels()>1)
			return false;

		int[] pixels = imagePlus.getBufferedImage().getRGB(0, 0, imagePlus.getWidth(), imagePlus.getHeight(), null, 0, imagePlus.getWidth());

		for(int pixel : pixels)
			if((pixel & 0xff) != (pixel & 0xff00) >> 8 || (pixel & 0xff) != (pixel & 0xff0000) >> 16)
				return false;

		return true;
	}

	public static void serializeObject(Object object, File outputFile, boolean compress) throws IOException
	{
		if (outputFile.exists())
			outputFile.delete();

		if (compress)
		{
			FileOutputStream fos = new FileOutputStream(outputFile);
			GZIPOutputStream zos = new GZIPOutputStream(fos);
			ObjectOutputStream ous = new ObjectOutputStream(zos);

			ous.writeObject(object);
			zos.finish();
			fos.flush();

			zos.close();
			fos.close();
			ous.close();
		}
		else
		{
			OutputStream file = new FileOutputStream(outputFile);
			OutputStream buffer = new BufferedOutputStream(file);
			ObjectOutput output = new ObjectOutputStream(buffer);

			output.writeObject(object);

			output.flush();
			output.close();
			buffer.close();
			file.close();
		}
	}

	public static Object deserializeObject(File objectFile, boolean compressed) throws IOException, ClassNotFoundException
	{
		if (compressed)
		{
			InputStream file = new FileInputStream(objectFile);
			GZIPInputStream gzipInputStream = new GZIPInputStream(file);
			ObjectInput input = new ObjectInputStream(gzipInputStream);

			Object result = input.readObject();

			input.close();
			gzipInputStream.close();
			file.close();
			return result;
		}
		else
		{
			InputStream file = new FileInputStream(objectFile);
			InputStream buffer = new BufferedInputStream(file);
			ObjectInput input = new ObjectInputStream(buffer);

			Object result = input.readObject();

			buffer.close();
			file.close();
			input.close();
			return result;
		}
	}

	public static Dimension getImageDimensions(File imagePath) throws IOException
	{

		try (ImageInputStream input = ImageIO.createImageInputStream(imagePath)) {
			ImageReader reader = ImageIO.getImageReaders(input).next(); // TODO: Handle no reader
			try {
				reader.setInput(input);
				return new Dimension(reader.getWidth(0), reader.getHeight(0));
			}
			finally {
				reader.dispose();
			}
		}
		catch (Exception e)
		{
			throw new IOException("Failed to read dimensions", e);
		}

	}

	public static int sum(int[] labelledPixels)
	{
		int sum = 0;
		for(int i : labelledPixels)
			sum+=i;

		return sum;
	}

	public static void shuffleArray(int[] ar, Random random)
	{
		for (int i = ar.length - 1; i > 0; i--)
		{
			int index = random.nextInt(i + 1);

			// Simple swap
			int a = ar[index];
			ar[index] = ar[i];
			ar[i] = a;
		}
	}

	//todo: remove ImagePlus image parameter and use features members (e.g.,  width, height) instead.
	public static ImageStack segmentImage(ImageSurfClassifier imageSurfClassifier, ImageFeatures features, ImagePlus image, StatusService statusService) throws ExecutionException, InterruptedException
	{
		if(imageSurfClassifier.getPixelType() != features.pixelType)
			throw new RuntimeException("Classifier pixel type ("+
					imageSurfClassifier.getPixelType()+") does not match image pixel type ("+features.pixelType+")");

		final RandomForest randomForest = imageSurfClassifier.getRandomForest();
		randomForest.setNumThreads(Prefs.getThreads());


		final ImageStack outputStack = new ImageStack(image.getWidth(), image.getHeight());
		final int numPixels = image.getWidth()*image.getHeight();

		//todo: merge channels in multi-channel images and expand imagesurf.feature set. e.g., features sets for R, G, B, RG, RB, GB and RGB
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

	public static ImageStack calculateImageFeatures(FeatureCalculator[] featureCalculators, ImageFeatures features, StatusService statusService) throws ExecutionException, InterruptedException
	{

		final ImageStack outputStack = new ImageStack(features.width, features.height);

		//todo: merge channels in multi-channel images and expand imagesurf.feature set. e.g., features sets for R, G, B, RG, RB, GB and RGB
		//			for(int c = 0; c< image.getNChannels(); c++)
		int currentSlice = 1;
		int c = 0;
		for(int z = 0; z< features.numSlices; z++)
			for(int t = 0; t< features.numFrames; t++)
			{

				ImageFeatures.ProgressListener imageFeaturesProgressListener = (current, max, message) ->
						statusService.showStatus(current, max, "Calculating features for plane "+currentSlice+"/"+
								(features.numChannels*features.numSlices*features.numFrames));

				features.addProgressListener(imageFeaturesProgressListener);
				if(features.calculateFeatures(c, z, t, featureCalculators))
					features.removeProgressListener(imageFeaturesProgressListener);

				for(FeatureCalculator f : featureCalculators)
				{
					outputStack.addSlice(f.getDescription(),((short[][]) features.getFeaturePixels(0,0,0,f))[0]);
				}
			}

		return outputStack;
	}
}
