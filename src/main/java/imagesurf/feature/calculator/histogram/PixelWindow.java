package imagesurf.feature.calculator.histogram;

import imagesurf.feature.calculator.histogram.Mask.*;
import org.jetbrains.annotations.NotNull;


public class PixelWindow {
    private final Histogram histogram;

    private final int maskOffset;
    private final MaskRow[] maskRows;

    private final PixelReader reader;
    final int width;
    final int height;
    final int y;

    private int x = 0;

    public int getNumPixels() {
        return histogram.getNumPixels();
    }

    public int getNumUniqueValues() {
        return histogram.getNumUniquePixelValues();
    }

    public java.util.Iterator<Histogram.Bin> getHistogramIterator()
    {
        return histogram.iterator();
    }

    private PixelWindow(
            final Mask mask,
            final int maskOffset,
            final Histogram sparseHistogram,
            final PixelReader reader,
            final int width,
            final int height,
            final int y) {

        this.maskRows = mask.rows;
        this.maskOffset = maskOffset;

        this.histogram = sparseHistogram;
        this.reader = reader;
        this.width = width;
        this.height = height;
        this.y = y;
    }

    void moveWindow() {

        for (int i = 0; i < maskRows.length; i++) {
            final int currentY = y + i + maskOffset;
            if (currentY >= 0 && currentY < height) {
                final int oldX = x + maskOffset + maskRows[i].offset;
                final int newX = oldX + maskRows[i].width;

                if (oldX >= 0 && oldX < width) {
                    final int oldValue = reader.get(to1d(oldX, currentY, width));
                    histogram.decrement(oldValue);
                }

                if (newX >= 0 && newX < width) {
                    final int newValue = reader.get(to1d(newX, currentY, width));
                    histogram.increment(newValue);
                }
            }
        }

        //Incrementing afterwards to reduce overhead of subtracting 1 from  oldX and newX values
        x++;
    }

    private final static int to1d(int x, int y, int width) {
        return y * width + x;
    }

    /***
     * Get the histogram for pixel neighborhood centered on pixel x=0 in row y
     * @param reader
     * @param width
     * @param height
     * @param mask
     * @param maskOffset
     * @param y
     * @return
     */
    static PixelWindow get(
            final PixelReader reader,
            final int width,
            final int height,
            final Mask mask,
            final int maskOffset,
            final int y) {
        final Histogram sparseHistogram = new Histogram(reader);

        return get(reader, width, height, mask, maskOffset, y, sparseHistogram);
    }

    @NotNull
    static PixelWindow get(PixelReader reader, int width, int height, Mask mask, int maskOffset, int y, Histogram histogram) {
        final MaskRow[] maskRows = mask.rows;

        histogram.reset();

        for (int i = 0; i < maskRows.length; i++) {
            final int currentY = y + i + maskOffset;
            if (currentY < 0 || currentY >= height)
                continue;

            for (int j = 0; j < maskRows[i].width; j++) {
                final int x = j + maskOffset + maskRows[i].offset;
                if (x < width && x >= 0) {
                    final int value = reader.get(to1d(x, currentY, width));
                    histogram.increment(value);
                }
            }
        }

        return new PixelWindow(mask, maskOffset, histogram, reader, width, height, y);
    }
}

