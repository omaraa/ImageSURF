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

package util;

import ij.ImagePlus;
import imagesurf.feature.PixelType;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class UtilityJava
{
    /** Cache of integer logs
     * FROM package weka.core.ContingencyTables.java;
     * */
    private static final double MAX_INT_FOR_CACHE_PLUS_ONE = 10000;
    private static final double[] INT_N_LOG_N_CACHE = new double[(int)MAX_INT_FOR_CACHE_PLUS_ONE];

    static
    {
        for (int i = 1; i < MAX_INT_FOR_CACHE_PLUS_ONE; i++) {
            double d = (double)i;
            INT_N_LOG_N_CACHE[i] = d * Math.log(d);
        }
    }

    /**
     * The natural logarithm of 2.
     */
    public static double log2 = Math.log(2);
    /**
     * The small deviation allowed in double comparisons.
     */
    public static double SMALL = 1e-6;

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

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Computes conditional entropy of the columns given
     * the rows.
     *
     * @param matrix the contingency table
     * @return the conditional entropy of the columns given the rows
     */
    public static double entropyConditionedOnRows(double[][] matrix) {

        double returnValue = 0, sumForRow, total = 0;

        for (int i = 0; i < matrix.length; i++) {
            sumForRow = 0;
            for (int j = 0; j < matrix[0].length; j++) {
                returnValue = returnValue + lnFunc(matrix[i][j]);
                sumForRow += matrix[i][j];
            }
            returnValue = returnValue - lnFunc(sumForRow);
            total += sumForRow;
        }
        if (eq(total, 0)) {
            return 0;
        }
        return -returnValue / (total * log2);
    }

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Computes the columns' entropy for the given contingency table.
     *
     * @param matrix the contingency table
     * @return the columns' entropy
     */
    public static double entropyOverColumns(double[][] matrix){

        double returnValue = 0, sumForColumn, total = 0;

        for (int j = 0; j < matrix[0].length; j++){
            sumForColumn = 0;
            for (int i = 0; i < matrix.length; i++) {
                sumForColumn += matrix[i][j];
            }
            returnValue = returnValue - lnFunc(sumForColumn);
            total += sumForColumn;
        }
        if (eq(total, 0)) {
            return 0;
        }
        return (returnValue + lnFunc(total)) / (total * log2);
    }

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Help method for computing entropy.
     */
    public static double lnFunc(double num){

        if (num <= 0) {
            return 0;
        } else {

            // Use cache if we have a sufficiently small integer
            if (num < MAX_INT_FOR_CACHE_PLUS_ONE) {
                int n = (int)num;
                if ((double)n == num) {
                    return INT_N_LOG_N_CACHE[n];
                }
            }
            return num * Math.log(num);
        }
    }

    /**
     * FROM package weka.core.Utils.java;
     * Tests if a is equal to b.
     *
     * @param a a double
     * @param b a double
     */
    public static/* @pure@ */boolean eq(double a, double b) {

        return (a == b) || ((a - b < SMALL) && (b - a < SMALL));
    }

    /**
     * FROM package weka.core.Utils.java;
     * Returns the logarithm of a for base 2.
     *
     * @param a a double
     * @return the logarithm for base 2
     */
    public static/* @pure@ */double log2(double a) {

        return Math.log(a) / log2;
    }

    /**
     * FROM package weka.core.Utils.java;
     * Computes the sum of the elements of an array of doubles.
     *
     * @param doubles the array of double
     * @return the sum of the elements
     */
    public static/* @pure@ */double sum(double[] doubles) {

        double sum = 0;

        for (double d : doubles) {
            sum += d;
        }
        return sum;
    }

    /**
     * FROM package weka.core.Utils.java;
     * Normalizes the doubles in the array by their sum.
     *
     * @param doubles the array of double
     * @exception IllegalArgumentException if sum is Zero or NaN
     */
    public static void normalize(double[] doubles) {

        double sum = 0;
        for (double d : doubles) {
            sum += d;
        }
        normalize(doubles, sum);
    }

    /**
     * FROM package weka.core.Utils.java;
     * Normalizes the doubles in the array using the given value.
     *
     * @param doubles the array of double
     * @param sum the value by which the doubles are to be normalized
     * @exception IllegalArgumentException if sum is zero or NaN
     */
    public static void normalize(double[] doubles, double sum) {

        if (Double.isNaN(sum)) {
            throw new IllegalArgumentException("Can't normalize array. Sum is NaN.");
        }
        if (sum == 0) {
            // Maybe this should just be a return.
            throw new IllegalArgumentException("Can't normalize array. Sum is zero.");
        }
        for (int i = 0; i < doubles.length; i++) {
            doubles[i] /= sum;
        }
    }

    /**
     * FROM package weka.core.Utils.java;
     * Returns index of maximum element in a given array of doubles. First maximum
     * is returned.
     *
     * @param doubles the array of doubles
     * @return the index of the maximum element
     */
    public static/* @pure@ */int maxIndex(double[] doubles) {

        double maximum = 0;
        int maxIndex = 0;

        for (int i = 0; i < doubles.length; i++) {
            if ((i == 0) || (doubles[i] > maximum)) {
                maxIndex = i;
                maximum = doubles[i];
            }
        }

        return maxIndex;
    }

    /**
     * FROM package weka.core.Utils.java;
     * Tests if a is greater than b.
     *
     * @param a a double
     * @param b a double
     */
    public static/* @pure@ */boolean gr(double a, double b) {

        return (a - b > SMALL);
    }

    public static PixelType getPixelType(ImagePlus imagePlus)
    {
        switch (imagePlus.getType())
        {
            case ImagePlus.COLOR_256:
            case ImagePlus.COLOR_RGB:
            case ImagePlus.GRAY8:
                return PixelType.GRAY_8_BIT;

            case ImagePlus.GRAY16:
                return PixelType.GRAY_16_BIT;

            case ImagePlus.GRAY32:
                throw new IllegalArgumentException("32-bit grayscale images are not yet supported.");

            default:
                throw new IllegalArgumentException("Image type not supported.");
        }
    }

    public static int calculateNumMergedChannels(int numChannels) {
        return (1 << numChannels) - 1;
    }

    public static int[] differentIndices(@NotNull int[] labelImagePixels, @NotNull int[] unlabelledImagePixels) {
        int[] indices = new int[unlabelledImagePixels.length];

        int numLabels = 0;
        for(int i = 0; i < unlabelledImagePixels.length; i++)
            if(labelImagePixels[i] != unlabelledImagePixels[i])
                indices[numLabels++] = i;

        return Arrays.copyOf(indices, numLabels);
    }

    @NotNull
    public static byte[] selectBytes(@NotNull int[] indices, @NotNull byte[] bytes) {
        byte[] result = new byte[indices.length];

        for(int i = 0; i < indices.length; i++)
            result[i] = bytes[indices[i]];

        return result;
    }

    @NotNull
    public static short[] selectShorts(@NotNull int[] indices, @NotNull short[] shorts) {
        short[] result = new short[indices.length];

        for(int i = 0; i < indices.length; i++)
            result[i] = shorts[indices[i]];

        return result;
    }

    @NotNull
    public static int[] selectInts(@NotNull int[] indices, @NotNull int[] ints) {
        int[] result = new int[indices.length];

        for(int i = 0; i < indices.length; i++)
            result[i] = ints[indices[i]];

        return result;
    }

    @NotNull
    public static byte[] mapSelectedBytes(@NotNull int[] selected, @NotNull int[] values, @NotNull Map<Integer, Byte> classMap) {
        byte[] result = new byte[selected.length];

        for(int i = 0; i < selected.length; i++)
            result[i] = classMap.get(values[selected[i]]);

        return result;
    }

    @NotNull
    public static short[] mapSelectedShorts(@NotNull int[] selected, @NotNull int[] values, @NotNull Map<Integer, Short> classMap) {
        short[] result = new short[selected.length];

        for(int i = 0; i < selected.length; i++)
            result[i] = classMap.get(values[selected[i]]);

        return result;
    }

    @NotNull
    public static byte[] mapBytes(byte[] bytes, @NotNull Map<Integer, Integer> classMap) {
        byte[] results = new byte[bytes.length];

        for(int i = 0; i < bytes.length; i++)
            results[i] = classMap.get(bytes[i] & 0xff).byteValue();

        return results;
    }

    @NotNull
    public static short[] mapShorts(short[] shorts, @NotNull Map<Integer, Integer> classMap) {
        short[] results = new short[shorts.length];

        for(int i = 0; i < shorts.length; i++)
            results[i] = classMap.get(shorts[i] & 0xffff).byteValue();

        return results;
    }
}