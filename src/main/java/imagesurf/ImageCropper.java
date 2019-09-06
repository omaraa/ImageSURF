package imagesurf;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;

import java.io.File;
import java.io.FileFilter;

public class ImageCropper {

    public static void main(String[] args) {
        final File imagePath = new File("/home/omaraa/Downloads/ellie/raw-full");
        final File roiPath = new File("/home/omaraa/Downloads/ellie/roi-resized");
        final File outputRawImagePath = new File("/home/omaraa/Downloads/ellie/raw-cropped");
        final File outputRoiPath = new File("/home/omaraa/Downloads/ellie/roi-resized-cropped");
        final int border = 200;

        final FileFilter imageFileFilter = file -> file.isFile()
                && !file.isHidden()
                && file.canRead()
                && file.getName().contains(".tif");

        final File[] imageFiles = imagePath.listFiles(imageFileFilter);

        for(final File imageFile : imageFiles) {
            final File outputImageFile = new File(outputRawImagePath,
                    imageFile.getName());

            final File roiFile = new File (roiPath, outputImageFile.getName());
            final File outputRoiFile = new File(outputRoiPath, outputImageFile.getName());

            if(!roiFile.exists() || outputRoiFile.exists())
                continue;

            System.out.println("Cropping "+imageFile.getName());

            ByteProcessor roiImage = new ImagePlus(roiFile.getAbsolutePath()).getChannelProcessor().convertToByteProcessor(false);
            final int width = roiImage.getWidth();
            final int height = roiImage.getHeight();

            int minX = width, maxX = 0, minY = height, maxY = 0;

            for(int x = 0; x < width; x+=1)
                for(int y=0; y < height; y+=1) {
                    if(roiImage.getPixel(x,y) == 255){
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }

            int finalWidth = maxX - minX + 1 + border*2;
            int finalHeight = maxY - minY + 1 + border*2;

            minX = Math.max(0, minX - border);
            minY = Math.max(0, minY - border);

            finalWidth = Math.min(finalWidth, width - minX);
            finalHeight = Math.min(finalHeight, height - minY);

            System.out.printf("X: %d Y: %d width: %d height: %d\n", minX, minY, finalWidth, finalHeight);

            roiImage.setRoi(minX, minY, finalWidth, finalHeight);
            ImagePlus croppedRoi = new ImagePlus("", roiImage.crop());
            new FileSaver(croppedRoi).saveAsTiff(outputRoiFile.getAbsolutePath());

            ImagePlus rawImage = new ImagePlus("", new ImagePlus(imageFile.getAbsolutePath()).getChannelProcessor());
            rawImage.setRoi(minX, minY, finalWidth, finalHeight);
            new FileSaver(rawImage.crop()).saveAsTiff(outputImageFile.getAbsolutePath());
        }
    }

}
