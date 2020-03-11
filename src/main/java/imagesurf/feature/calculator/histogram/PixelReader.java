package imagesurf.feature.calculator.histogram;

import java.util.Arrays;

public interface PixelReader {
    int get(int index);
    int numPixels();
    int numBits();
    int maxValue();

    default int numValues() {
        return 1 << numBits();
    }

    default int[] uniqueValues() {
       final int[] counts = new int[numPixels()];
       final int[] unique = new int[numPixels()];

        final int numPixels = numPixels();
        for(int i = 0; i < numPixels; i++)
            counts[get(i)]++;

        int uniqueI = 0;
        for(int i = 0; i < numPixels; i++) {
            if(counts[i] > 0)
                unique[uniqueI++] = i;
        }

        return Arrays.copyOf(unique, uniqueI);
    }
}
