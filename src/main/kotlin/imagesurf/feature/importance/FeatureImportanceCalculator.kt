package imagesurf.feature.importance

import imagesurf.classifier.Classifier
import imagesurf.feature.FeatureReader
import imagesurf.feature.calculator.FeatureCalculator
import java.util.stream.IntStream

interface FeatureImportanceCalculator {

    fun calculateFeatureImportance(classifier: Classifier, reader: FeatureReader, instanceIndices: IntArray = IntStream.range(0, reader.numInstances).toArray()): DoubleArray

    fun selectOptimalFeatures(maxFeatures: Int, reader: FeatureReader, classifier: Classifier, availableFeatures: Array<FeatureCalculator>, logger: ((String) -> Any?)? = null): Array<FeatureCalculator> {
        val featureImportance = calculateFeatureImportance(classifier, reader)

        val rankedFeatures = (0 until reader.numFeatures)
                .asSequence()
                .filter { i -> reader.classIndex != i }
                .sortedWith(Comparator { i1, i2 -> featureImportance[i2].compareTo(featureImportance[i1]) })
                .map { availableFeatures[it] to featureImportance[it] }
                .toList()

        logger?.let { log ->
            log("Feature Importance:")
            rankedFeatures.forEach {(calculator, importance) ->
                logger(calculator.descriptionWithTags + ": " + importance)
            }
        }

        val lowestSelectedFeatureImportance = rankedFeatures[maxFeatures - 1].second
        return rankedFeatures
                .filter { (calculator, importance) -> importance > lowestSelectedFeatureImportance }
                .let {
                    rankedFeatures
                            .filter { (calculator, importance) -> importance.equals(lowestSelectedFeatureImportance) }
                            .shuffled()
                            .take(maxFeatures - it.size) + it
                }
                .map { (calculator, _) -> calculator }
                .toTypedArray()
    }
}