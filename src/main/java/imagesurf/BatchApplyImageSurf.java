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

import imagesurf.classifier.ImageSurfClassifier;
import imagesurf.classifier.RandomForest;
import imagesurf.feature.SurfImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.io.FileSaver;
import imagesurf.util.ProgressListener;
import io.scif.services.DatasetIOService;
import net.imagej.DatasetService;
import net.imagej.ImageJ;
import org.jetbrains.annotations.NotNull;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.FileWidget;
import imagesurf.util.UtilityKt;
import util.UtilityJava;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Plugin(type = Command.class, headless = true,
	menuPath = "Plugins>Segmentation>ImageSURF>4b. Batch Apply ImageSURF Classifier")
public class BatchApplyImageSurf implements Command{

	@Parameter
	private LogService log;

	@Parameter
	private StatusService statusService;

	@Parameter
	private PrefService prefService;

	@Parameter
	private DatasetService datasetService;

	@Parameter
	private DatasetIOService datasetIOService;

	@Parameter(label = "ImageSURF Classifier", type = ItemIO.INPUT,
			description = "ImageSURF classifier file. If you have not yet trained a classifier, train it using the \"Train ImageSURF Classifier\" command.")
	private File classifierFile;

	@Parameter(label = "Input images folder",
			type = ItemIO.INPUT,
			style= FileWidget.DIRECTORY_STYLE,
			description = "Folder of images to segment. ImageSURF will attempt to segment all images in this folder, unless a file name pattern is set below.")
	private File imagesPath;

	@Parameter(label = "Input image file names contain",
			type = ItemIO.INPUT,
			required = false,
			description = "A pattern string to limit the input files. ONLY files that contain this exact, " +
					"case-sensitive, string will be segmented. e.g., \".tif\" will exclude all files that do not " +
					"contain \".tif\" in the file name")
	private String imagesPattern = "";

	@Parameter(label = "Segmented image output path",
			type = ItemIO.INPUT,
			style= FileWidget.DIRECTORY_STYLE,
			description = "Folder to save image segmentation output to. Segmentation output images are saved with the same name as input images. Any files with matching names WILL BE OVER-WRITTEN.")
	private File imagesOutputPath;

	public static void main(final String... args) throws Exception {
		// create the ImageJ application context with all available services
		final ImageJ ij = net.imagej.Main.launch(args);

		ij.command().run(BatchApplyImageSurf.class, true);
	}

	@Override
	public void run()
	{
		final int tileSize = prefService.getInt(ImageSurfSettings.IMAGESURF_TILE_SIZE, ImageSurfSettings.DEFAULT_TILE_SIZE);

		batchApplyImageSurf(classifierFile, imagesOutputPath, imagesPath, imagesPattern, tileSize, progressListener, log, statusService);
	}

	public static File[] batchApplyImageSurf(File classifierFile, File imagesOutputPath, File imagesPath, final String imagesPattern, int tileSize, ProgressListener progressListener, LogService log, StatusService statusService) {

		final ImageSurfClassifier imageSurfClassifier;
		try
		{
			imageSurfClassifier = (ImageSurfClassifier) UtilityJava.deserializeObject(classifierFile, true);

			final RandomForest rf = imageSurfClassifier.getRandomForest();
			rf.setNumThreads(Prefs.getThreads());
			rf.addProgressListener(progressListener);

		}
		catch (IOException | ClassNotFoundException e)
		{
			log.error("Failed to load imagesurf.classifier", e);
			return null;
		}

		File[] imageFiles = imagesPath.listFiles(pathname -> {
			if (pathname.isDirectory() || pathname.isHidden())
				return false;

			if (imagesPattern != null && !imagesPattern.isEmpty() && !pathname.getName().contains(imagesPattern))
				return false;

			return !pathname.getName().endsWith(".features");
		});

		File[] outputFiles = Arrays.stream(imageFiles)
				.map( imageFile -> new File(imagesOutputPath, imageFile.getName()))
				.toArray(File[]::new);

		for (int imageIndex = 0; imageIndex < imageFiles.length; imageIndex++)
		{
			File imageFile = imageFiles[imageIndex];

			try
			{
				log.info("Opening "+imageFile.getAbsolutePath());
				ImagePlus image = new ImagePlus(imageFile.getAbsolutePath());

				File featuresInputFile = new File(imageFile.getParentFile(), imageFile.getName() + ".features");

				final SurfImage features;
				if (featuresInputFile == null || !featuresInputFile.exists() || !featuresInputFile.isFile())
				{
					log.info("Features "+featuresInputFile.getAbsolutePath()+" does not exist.");
					features = new SurfImage(image);
				}
				else
				{
					log.info("Reading features "+featuresInputFile.getAbsolutePath());
					statusService.showStatus("Reading features "+featuresInputFile.getAbsolutePath());
					features = SurfImage.deserialize(featuresInputFile.toPath());
				}

				if (imageSurfClassifier.getPixelType() != features.pixelType)
					throw new Exception("Classifier pixel type (" +
							imageSurfClassifier.getPixelType() + ") does not match image pixel type (" + features.pixelType + ")");

				if (imageSurfClassifier.getNumChannels() != features.numChannels)
					throw new Exception("Classifier trained for "+imageSurfClassifier.getNumChannels()+" channels. Image has "+features.numChannels+" - cannot segment.");

				final ImageStack outputStack = ApplyImageSurf.run(imageSurfClassifier, image, statusService, tileSize);
				final ImagePlus outputImage = new ImagePlus(image.getTitle(), outputStack);

				File imageOutputFile = new File(imagesOutputPath, imageFile.getName());
				String imageOutputPath = imageOutputFile.getAbsolutePath();
				String imageOutputPathLower = imageOutputPath.toLowerCase();
				FileSaver fileSaver = new FileSaver(outputImage);

				if (image.getNSlices() > 1)
					fileSaver.saveAsTiffStack(imageOutputPath);
				else if(imageOutputPathLower.endsWith(".tif") || imageOutputPathLower.endsWith(".tiff"))
					new FileSaver(image).saveAsTiff(imageOutputPath);
				else if(imageOutputPathLower.endsWith(".png"))
					fileSaver.saveAsPng(imageOutputPath);
				else
					new FileSaver(image).saveAsTiff(imageOutputPath);

			}
			catch (Exception e)
			{
				log.error("Failed to segment image "+imageFile.getAbsolutePath(), e);
			}
		}

		return outputFiles;
	}

	private final BatchApplyProgressListener progressListener = new BatchApplyProgressListener();

	private class BatchApplyProgressListener implements ProgressListener {

		@Override
		public void onProgress(int current, int max, @NotNull String message) {
			statusService.showStatus(current, max, message);
		}
	}
}
