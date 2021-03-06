package imagesurf.feature.calculator.histogram;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Histogram {

    private final Bin[] bins;

    private final Bin[] denseBins;
    private final SkipList included;

    private int numPixels = 0;

    Histogram(PixelReader reader) {
        final int[] values = reader.uniqueValues();

        denseBins = new Bin[reader.numValues()];
        bins = new Bin[values.length];
        included = new SkipList(values.length);

        int j = 0;
        for(int i : values) {
            final Bin b = new Bin(i);
            denseBins[i] = b;
            bins[j++] = b;
        }
    }

    public Histogram copy() {
        return new Histogram(this);
    }

    public Bin bin(int index) {
        return denseBins[index];
    }

    private Histogram(Histogram histogram) {
        denseBins = new Bin[histogram.denseBins.length];

        bins = new Bin[histogram.bins.length];
        included = new SkipList();


        for(int i = 0; i< bins.length; i++) {
            Bin b = new Bin(histogram.bins[i]);
            bins[i] = b;
            denseBins[b.value] = b;

            if(b.value > 0)
                included.add(b.value);
        }
    }

    int getNumPixels() {
        return numPixels;
    }
    int getNumUniquePixelValues() { return included.size(); }

    void increment(int value) {
        final Bin bin = denseBins[value];

        if(bin.count == 0) {
            included.add(value);
        }

        denseBins[value].count++;

        numPixels++;
    }

    void decrement(int value) {
        final Bin bin = denseBins[value];

        if(bin.count <= 0)
            throw new RuntimeException("Cannot decrement below 0");

        bin.count--;

        if(bin.count == 0)
            included.remove(value);

        numPixels--;
    }

    Iterator<Bin> iterator() {
        final PrimitiveIterator.OfInt valuesIterator = included.ascending();

        return new Iterator<Bin>() {
            @Override
            public boolean hasNext() {
                return valuesIterator.hasNext();
            }

            @Override
            public Bin next() {
                return denseBins[valuesIterator.nextInt()];
            }
        };
    }

    public void reset() {
        for(Bin b : bins)
            b.count = 0;

        numPixels = 0;
        included.clear();
    }

    Iterator<Bin> iteratorDescending() {
        final PrimitiveIterator.OfInt valuesIterator = included.descending();

        return new Iterator<Bin>() {
            @Override
            public boolean hasNext() {
                return valuesIterator.hasNext();
            }

            @Override
            public Bin next() {
                return denseBins[valuesIterator.nextInt()];
            }
        };    }

    public Bin min() {
        return denseBins[included.firstValue()];
    }

    public Bin max() {
        return denseBins[included.lastValue()];
    }


    public static class Bin {
        public final int value;
        private int count;

        public int getCount() {
            return count;
        }

        private Bin(int value) {
            this.value = value;
        }

        private Bin(@NotNull  Bin bin) {
            value = bin.value;
            count = bin.count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Bin bin = (Bin) o;
            return value == bin.value &&
                    count == bin.count;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }
}