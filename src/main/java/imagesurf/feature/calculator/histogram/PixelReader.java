package imagesurf.feature.calculator.histogram;

public interface PixelReader {
    int get(int index);
    int numPixels();
    int numBits();
}
