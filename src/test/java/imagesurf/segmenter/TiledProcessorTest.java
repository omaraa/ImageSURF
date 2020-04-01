package imagesurf.segmenter;

import ij.ImagePlus;
import ij.process.ShortProcessor;
import imagesurf.feature.SurfImage;
import imagesurf.feature.calculator.Identity;
import kotlin.jvm.functions.Function1;
import org.junit.Assert;
import org.junit.Test;

import java.awt.image.ColorModel;
import java.util.LinkedList;
import java.util.List;

public class TiledProcessorTest {

    @Test
    public void testTiling() {
        int width = 2, height = 3;

        TiledProcessor processor = new TiledProcessor(2, 1);
        short[] pixels = new short[width * height];
        for (int i = 0; i < pixels.length; i++)
            pixels[i] = (short) i;

        SurfImage image = new SurfImage(new ImagePlus("", new ShortProcessor(width, height, pixels, ColorModel.getRGBdefault())));

        short[] outputPixels = (short[]) processor.process(image, null, new Function1<SurfImage, List<? extends Object>>() {
            @Override
            public List<? extends Object> invoke(SurfImage surfImage) {
                Object[] result = (Object[]) surfImage.getFeaturePixels(0, 0, Identity.get());
                List list = new LinkedList();

                for(Object o : result)
                    list.add(o);

                return list;
            }
        }).getProcessor(1).getPixels();

        Assert.assertArrayEquals(pixels, outputPixels);
    }
}