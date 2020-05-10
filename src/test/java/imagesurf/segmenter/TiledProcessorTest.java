package imagesurf.segmenter;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import imagesurf.feature.PixelType;
import imagesurf.feature.SurfImage;
import imagesurf.feature.calculator.Identity;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class TiledProcessorTest {

    @Test
    public void testTiling() {
        int width = 200, height = 300;

        TiledProcessor processor = new TiledProcessor(29, 11);
        byte[] pixels = new byte[width * height];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (byte) i;

        SurfImage image = new SurfImage(new ImagePlus("", new ByteProcessor(width, height, pixels)));

        byte[] outputPixels = (byte[]) processor.process(image, PixelType.GRAY_8_BIT, null, surfImage -> {
            Object[] result = (Object[]) surfImage.getFeaturePixels(0, 0, Identity.get());
            List list = new LinkedList();

            for(Object o : result)
                list.add(o);

            return list;
        }).getProcessor(1).getPixels();

        Assert.assertArrayEquals(pixels, outputPixels);
    }
}