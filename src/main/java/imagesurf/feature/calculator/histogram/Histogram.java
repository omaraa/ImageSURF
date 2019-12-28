package imagesurf.feature.calculator.histogram;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class Histogram {
    private final Bin[] bins;

    private final Bin[] denseBins;
    private final SortedSet<Bin> sparseBins = new ConcurrentSkipListSet<>(Comparator.comparingInt(x -> x.value));

    private int numPixels = 0;

    Histogram(PixelReader reader) {
        final int[] values = reader.uniqueValues();

        denseBins = new Bin[reader.numValues()];
        bins = new Bin[values.length];

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

    private Histogram(Histogram histogram) {
        denseBins = new Bin[histogram.denseBins.length];

        bins = new Bin[histogram.bins.length];

        for(int i = 0; i< bins.length; i++) {
            Bin b = new Bin(histogram.bins[i]);
            bins[i] = b;
            denseBins[b.value] = b;

            if(b.value > 0)
                sparseBins.add(b);
        }
    }

    int getNumPixels() {
        return numPixels;
    }
    int getNumUniquePixelValues() { return sparseBins.size(); }

    void increment(int value) {
        denseBins[value].increment();
        numPixels++;
    }

    void decrement(int value) {
        denseBins[value].decrement();
        numPixels--;
    }

    Iterator<Bin> iterator() {
        return sparseBins.iterator();
    }

    public void reset() {
        for(Bin b : bins)
            b.count = 0;

        numPixels = 0;
        sparseBins.clear();
    }

    public class Bin {
        public final int value;
        private int count;

        public int getCount() {
            return count;
        }

        private void increment() {
            if(count == 0) {
                sparseBins.add(this);
                count = 1;
            } else {
                count++;
            }

        }

        private void decrement() {
            if(count <= 0)
                throw new RuntimeException("Cannot decrement below 0");

            count--;

            if(count == 0)
                sparseBins.remove(this);
        }

        private Bin(int value) {
            this.value = value;
        }

        private Bin(Bin bin) {
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