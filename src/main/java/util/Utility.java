package util;

import ij.ImagePlus;
import net.imagej.ImgPlus;
import net.imglib2.img.Img;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
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
}
