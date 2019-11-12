package imagesurf.feature;

import imagesurf.reader.ByteReader;
import imagesurf.reader.ShortReader;

public class FeatureReaderFactory {
    public final PixelType pixelType;

    public FeatureReaderFactory(PixelType pixelType) {
        this.pixelType = pixelType;
    }

    public FeatureReader getReader(Object[] trainingExamples) {
        switch (pixelType) {
            case GRAY_8_BIT:
                final byte[][] bytes = new byte[trainingExamples.length][];
                for (int i = 0; i < bytes.length; i++)
                    bytes[i] = (byte[]) trainingExamples[i];

                return new ByteReader(bytes, trainingExamples.length - 1);
            case GRAY_16_BIT:
                final short[][] shorts = new short[trainingExamples.length][];
                for (int i = 0; i < shorts.length; i++)
                    shorts[i] = (short[]) trainingExamples[i];

                return new ShortReader(shorts, trainingExamples.length - 1);
            default:
                throw new RuntimeException("ReaderFactory does not support pixeltype "+pixelType);
        }
    }
}
