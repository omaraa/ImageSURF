package imagesurf.feature;

import ij.ImagePlus;
import ij.process.ShortProcessor;
import imagesurf.feature.calculator.Identity;
import org.junit.Assert;
import org.junit.Test;

import java.awt.image.ColorModel;

public class SurfImageTest {

    @Test
    public void getImageAsSubImage() {

        short[] pixels = new short[10 * 10];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (short) i;

        SurfImage image = new SurfImage(new ImagePlus("", new ShortProcessor(10, 10, pixels, ColorModel.getRGBdefault())));

        short[][] subImagePixels = (short[][]) image.getSubImage(0, 0, 10, 10).getFeaturePixels(0, 0, Identity.get());


        Assert.assertArrayEquals(pixels, subImagePixels[0]);
    }

    @Test
    public void getBottomHalfImageAsSubImage() {

        short[] pixels = new short[10 * 10];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (short) i;

        SurfImage image = new SurfImage(new ImagePlus("", new ShortProcessor(10, 10, pixels, ColorModel.getRGBdefault())));

        short[][] subImagePixels = (short[][]) image.getSubImage(0, 5, 10, 5).getFeaturePixels(0, 0, Identity.get());


        short[] expected = new short[50];
        for(int i = 0; i < 50; i++)
            expected[i] = (short) (i + 50);

        Assert.assertArrayEquals(expected, subImagePixels[0]);
    }

    @Test
    public void getBottomRightQuarterImageAsSubImage() {

        short[] pixels = new short[10 * 10];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (short) i;

        SurfImage image = new SurfImage(new ImagePlus("", new ShortProcessor(10, 10, pixels, ColorModel.getRGBdefault())));

        short[][] subImagePixels = (short[][]) image.getSubImage(5, 5, 5, 5).getFeaturePixels(0, 0, Identity.get());



        short[] expected = new short[25];
        int c = 0;
        for(int row = 0; row < 5; row++)
            for(int col = 0; col < 5; col++)
            expected[c++] = (short) (row * 10 + 55 + col);

        Assert.assertArrayEquals(expected, subImagePixels[0]);
    }
}