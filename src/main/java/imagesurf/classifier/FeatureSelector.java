package imagesurf.classifier;

import imagesurf.feature.FeatureReader;
import imagesurf.feature.calculator.FeatureCalculator;
import net.mintern.primitive.Primitive;

import java.util.Arrays;
import java.util.stream.IntStream;

public class FeatureSelector {
    public static FeatureCalculator[] selectOptimalFeatures(int maxFeatures, FeatureReader reader, RandomForest randomForest, FeatureCalculator[] availableFeatures, Logger log) {
        final double[] featureImportance;
        try {
            featureImportance = randomForest.calculateFeatureImportance(reader);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to calculate feature importance", e);
        }
        int[] rankedFeatures = IntStream.range(0, reader.getNumFeatures())
                .filter(i -> reader.getClassIndex() != i)
                .toArray();

        Primitive.sort(rankedFeatures, (i1, i2) -> Double.compare(featureImportance[i2], featureImportance[i1]));

        log.info("Feature Importance:");
        for (int i : rankedFeatures) {
            log.info(availableFeatures[i].getDescriptionWithTags() + ": " + featureImportance[i]);
        }

        return Arrays.stream(rankedFeatures, 0, maxFeatures)
                .mapToObj(i -> availableFeatures[i])
                .toArray(FeatureCalculator[]::new);
    }

    public interface Logger {
        void info(String message);
    }
}
