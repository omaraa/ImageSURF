package imagesurf.feature.importance

import imagesurf.classifier.Classifier
import imagesurf.feature.FeatureReader
import imagesurf.feature.calculator.FeatureCalculator
import java.util.stream.IntStream

interface FeatureImportanceCalculator {

    fun calculateFeatureImportance(classifier: Classifier, reader: FeatureReader, instanceIndices: IntArray = IntStream.range(0, reader.numInstances).toArray()): DoubleArray

    fun selectOptimalFeatures(maxFeatures: Int, reader: FeatureReader, classifier: Classifier, availableFeatures: Array<FeatureCalculator>, logger: ((String) -> Any?)? = null): Array<FeatureCalculator> {
        val featureImportance = calculateFeatureImportance(classifier, reader)

        val rankedFeatures =
                (0 until reader.numFeatures)
                .filter { i -> reader.classIndex != i }
                .sortedWith(Comparator { i1, i2 -> featureImportance[i2].compareTo(featureImportance[i1]) })

        logger?.let { log ->
            log("Feature Importance:")
            rankedFeatures.forEach {
                logger(availableFeatures[it].descriptionWithTags + ": " + featureImportance[it])
            }
        }

        return rankedFeatures.take(maxFeatures).map { availableFeatures[it] }.toTypedArray()
    }
}