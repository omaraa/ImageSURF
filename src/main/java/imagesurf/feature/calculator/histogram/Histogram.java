package imagesurf.feature.calculator.histogram;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

public class Histogram {
    private final Bin[] bins;
    private final SortedSet<Bin> sparseBins = new ConcurrentSkipListSet<>(Comparator.comparingInt(x -> x.value));

    Histogram(int nBins) {
        bins = new Bin[nBins];
        for(int i = 0; i< nBins; i++)
            bins[i] = new Bin(i);
    }

    private int numPixels = 0;

    int getNumPixels() {
        return numPixels;
    }
    int getNumUniquePixelValues() { return sparseBins.size(); }

    void increment(int value) {
        bins[value].increment();
        numPixels++;
    }

    void decrement(int value) {
        bins[value].decrement();
        numPixels--;
    }

    Iterator<Bin> iterator() {
        return sparseBins.iterator();
    }

    public class Bin {
        public final int value;
        private int count;

        private final int hashCode;

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
            this.hashCode = Objects.hash(value);
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
            return hashCode;
        }
    }
}