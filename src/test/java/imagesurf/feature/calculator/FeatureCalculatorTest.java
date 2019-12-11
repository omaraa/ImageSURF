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

package imagesurf.feature.calculator;

import imagesurf.feature.calculator.histogram.MultipleFeatureCalculator;
import imagesurf.feature.calculator.histogram.NeighbourhoodHistogramCalculator;
import io.scif.img.ImgOpener;
import io.scif.img.SCIFIOImgPlus;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;

public class FeatureCalculatorTest {

    private static final ImgOpener imgOpener = new ImgOpener();

    private byte[] bytePixels = null;
    private short[] shortPixels = null;
    private int byteWidth, byteHeight, shortWidth, shortHeight;

    final int[] radii = new int[]{3, 5, 33};

    @Before
    public void setUp() {
        String imagePath = getClass().getResource("/images/Nomarski-14DIV/raw.png").getFile();
        String shortImagePath = getClass().getResource("/images/abeta-16bit/Identity.tif").getFile();

        final SCIFIOImgPlus<UnsignedByteType> byteImage = (SCIFIOImgPlus<UnsignedByteType>) imgOpener.openImgs(imagePath).get(0);
        final SCIFIOImgPlus<UnsignedShortType> shortImage = (SCIFIOImgPlus<UnsignedShortType>) imgOpener.openImgs(shortImagePath).get(0);


        bytePixels = new byte[(int) byteImage.size()];
        shortPixels = new short[(int) shortImage.size()];

        int currentIndex = 0;
        for (UnsignedByteType b : byteImage)
            bytePixels[currentIndex++] = (byte) b.get();

        currentIndex = 0;
        for (UnsignedShortType s : shortImage)
            shortPixels[currentIndex++] = (short) (s.get());

        byteWidth = (int) byteImage.getImageMetadata().getAxisLength(0);
        byteHeight = (int) byteImage.getImageMetadata().getAxisLength(1);

        shortWidth = (int) shortImage.getImageMetadata().getAxisLength(0);
        shortHeight = (int) shortImage.getImageMetadata().getAxisLength(1);

    }

    @Test
    public void testCalculateMeans() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Mean(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateMedians() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Median(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateMins() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Min(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateMaxs() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Max(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateEntropys() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Entropy(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateGaussians() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Gaussian(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateRanges() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new Range(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateDifferenceOf() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new DifferenceOf(Identity.get(), new Mean(i));
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateStandardDeviation() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new StandardDeviation(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateLocalIntensity() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new LocalIntensity(i);
            testFeatureCalculator(featureCalculator);
        }
    }

    @Test
    public void testCalculateDifferenceOfGaussians() throws Exception {
        for (int i : radii)
            for (int j : radii)
                if (j < i) {
                    FeatureCalculator featureCalculator = new DifferenceOf(new Gaussian(j), new Gaussian(i));
                    testFeatureCalculator(featureCalculator);
                }
    }

    @Test
    public void testCalculateMulti() throws Exception {
        for (int i : radii) {
            FeatureCalculator featureCalculator = new MultipleFeatureCalculator(
                    new NeighbourhoodHistogramCalculator[] {new Entropy(i), new Median(i)});
            testFeatureCalculator(featureCalculator);
        }
    }

    private void testFeatureCalculator(FeatureCalculator featureCalculator) throws Exception {
        testByteFeatureCalculator(featureCalculator);
        testShortFeatureCalculator(featureCalculator);
    }

    private void testByteFeatureCalculator(FeatureCalculator f) throws Exception {
        byte[] bytePixelsCopy = Arrays.copyOf(bytePixels, bytePixels.length);

        Map<FeatureCalculator, byte[][]> calculated = new HashMap<>();

        FeatureCalculator[] dependencies = f.getDependencies();
        byte[][][] dependencyResults = new byte[dependencies.length][][];
        for (int i = 0; i < dependencies.length; i++) {
            byte[][] currentDependencyResults = dependencies[i].calculate(bytePixels, byteWidth, byteHeight, calculated);
            dependencyResults[i] = new byte[currentDependencyResults.length][];
            for (int j = 0; j < currentDependencyResults.length; j++) {
                dependencyResults[i][j] = Arrays.copyOf(currentDependencyResults[j], currentDependencyResults[j].length);
            }
        }

        byte[][] result = f.calculate(bytePixelsCopy, byteWidth, byteHeight, calculated);

        assertArrayEquals(f.getDescription() + " should not mutate input array", bytePixels, bytePixelsCopy);

        String[] resultDescriptions = f.getResultDescriptions();
        for (int i = 0; i < f.getNumImagesReturned(); i++) {
            byte[] expected = getBytes(resultDescriptions[i]);

            assertArrayEquals(resultDescriptions[i] + " should match pre-calculated result", expected, result[i]);
        }

        for (int i = 0; i < dependencies.length; i++) {
            byte[][] expectedResults = dependencyResults[i];
            byte[][] cachedResults = calculated.get(dependencies[i]);

            for (int j = 0; j < expectedResults.length; j++) {
                assertArrayEquals(dependencies[i].getResultDescriptions()[j] + " cached result should not be mutated", expectedResults[j], cachedResults[j]);
            }
        }
    }

    private void testShortFeatureCalculator(FeatureCalculator f) throws Exception {
        short[] shortPixelsCopy = Arrays.copyOf(shortPixels, shortPixels.length);

        Map<FeatureCalculator, short[][]> calculated = new HashMap<>();

        FeatureCalculator[] dependencies = f.getDependencies();
        short[][][] dependencyResults = new short[dependencies.length][][];
        for (int i = 0; i < dependencies.length; i++) {
            short[][] currentDependencyResults = dependencies[i].calculate(shortPixels, shortWidth, shortHeight, calculated);
            dependencyResults[i] = new short[currentDependencyResults.length][];
            for (int j = 0; j < currentDependencyResults.length; j++) {
                dependencyResults[i][j] = Arrays.copyOf(currentDependencyResults[j], currentDependencyResults[j].length);
            }
        }

        short[][] result = f.calculate(shortPixelsCopy, shortWidth, shortHeight, calculated);

        assertArrayEquals(f.getDescription() + " should not mutate input array", shortPixels, shortPixelsCopy);

        String[] resultDescriptions = f.getResultDescriptions();
        for (int i = 0; i < f.getNumImagesReturned(); i++) {
            short[] expected = getShorts(resultDescriptions[i]);

            assertArrayEquals(resultDescriptions[i] + " should match pre-calculated result", expected, result[i]);
        }

        for (int i = 0; i < dependencies.length; i++) {
            short[][] expectedResults = dependencyResults[i];
            short[][] cachedResults = calculated.get(dependencies[i]);

            for (int j = 0; j < expectedResults.length; j++) {
                assertArrayEquals(dependencies[i].getResultDescriptions()[j] + " cached result should not be mutated", expectedResults[j], cachedResults[j]);
            }
        }
    }

    private byte[] getBytes(String resultDescription) throws Exception {
        String path = "/images/Nomarski-14DIV/" + (resultDescription + ".png");
        URL url = getClass().getResource(path);
        String imagePath = url.getFile();
        imagePath = imagePath.replace("%20", " ");

        File file = new File(imagePath);
        if (!file.exists())
            throw new RuntimeException(file.getAbsolutePath() + " does not exist");

        SCIFIOImgPlus<UnsignedByteType> image = (SCIFIOImgPlus<UnsignedByteType>) imgOpener.openImgs(imagePath).get(0);

        byte[] bytes = new byte[(int) image.size()];
        int currentIndex = 0;
        for (UnsignedByteType b : image)
            bytes[currentIndex++] = (byte) b.get();

        return bytes;
    }

    private short[] getShorts(String resultDescription) throws Exception {
        String path = "/images/abeta-16bit/" + (resultDescription + ".tif");
        URL url = getClass().getResource(path);
        String imagePath = url.getFile();
        imagePath = imagePath.replace("%20", " ");

        File file = new File(imagePath);
        if (!file.exists())
            throw new RuntimeException(file.getAbsolutePath() + " does not exist");

        SCIFIOImgPlus<UnsignedShortType> image = (SCIFIOImgPlus<UnsignedShortType>) imgOpener.openImgs(imagePath).get(0);

        short[] shorts = new short[(int) image.size()];
        int currentIndex = 0;
        for (UnsignedShortType b : image)
            shorts[currentIndex++] = (short) b.get();

        return shorts;
    }
}