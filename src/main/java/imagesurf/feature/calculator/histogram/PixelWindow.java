package imagesurf.feature.calculator.histogram;

import imagesurf.feature.calculator.IntHashSet;
import imagesurf.feature.calculator.histogram.Mask.*;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;


public class PixelWindow {
    private final Histogram histogram;

    private final int maskOffset;
    private final MaskRow[] maskRows;

    private final PixelReader reader;
    final int width;
    final int height;
    final int y;

    private final IntHashSet added;
    private final IntHashSet removed;

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

    public Histogram.Bin getHistogramMin() {
        return histogram.min();
    }

    public Histogram.Bin getHistogramMax() {
        return histogram.max();
    }

    public java.util.Iterator<Histogram.Bin> getHistogramIteratorDescending()
    {
        return histogram.iteratorDescending();
    }

    public Iterator<Histogram.Bin> getLastAdded() {

        final imagesurf.feature.calculator.IntHashSet.IntIterator it = added.intIterator();
        return new Iterator<Histogram.Bin>() {

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Histogram.Bin next() {
                return histogram.bin(it.next());
            }
        };
    }

    public Iterator<Histogram.Bin> getLastRemoved() {
        final imagesurf.feature.calculator.IntHashSet.IntIterator it = removed.intIterator();

        return new Iterator<Histogram.Bin>() {

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Histogram.Bin next() {
                return histogram.bin(it.next());
            }
        };
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

        this.added = new IntHashSet(maskRows.length);
        histogram.iterator().forEachRemaining((b) -> added.add(b.value));
        this.removed = new IntHashSet(maskRows.length);
    }

    void moveWindow() {
        added.clear();
        removed.clear();

        for (int i = 0; i < maskRows.length; i++) {
            final int currentY = y + i + maskOffset;
            if (currentY >= 0 && currentY < height) {
                final int oldX = x + maskOffset + maskRows[i].offset;
                final int newX = oldX + maskRows[i].width;

                if (oldX >= 0 && oldX < width) {
                    final int oldValue = reader.get(to1d(oldX, currentY, width));
                    histogram.decrement(oldValue);
                    removed.add(oldValue);
                }

                if (newX >= 0 && newX < width) {
                    final int newValue = reader.get(to1d(newX, currentY, width));
                    histogram.increment(newValue);
                    added.add(newValue);
                }
            }
        }

        //Incrementing afterwards to reduce overhead of subtracting 1 from  oldX and newX values
        x++;
    }

    private static int to1d(int x, int y, int width) {
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

