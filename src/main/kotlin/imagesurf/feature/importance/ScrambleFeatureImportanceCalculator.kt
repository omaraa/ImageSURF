package imagesurf.feature.importance

import imagesurf.classifier.Classifier
import imagesurf.feature.FeatureReader
import imagesurf.util.Utility
import java.util.*
import java.util.stream.IntStream

class ScrambleFeatureImportanceCalculator(
        val random: Random
) : FeatureImportanceCalculator {

    constructor(randomSeed: Long) : this(Random(randomSeed))

    override fun calculateFeatureImportance(classifier: Classifier, reader: FeatureReader, instanceIndices: IntArray): DoubleArray =
            (0 until reader.numFeatures).map { attributeIndex ->
                if (attributeIndex == reader.classIndex) java.lang.Double.NaN
                else ScrambledFeatureReader(reader, attributeIndex, random.nextLong())
                        .let{ classifier.classForInstances(it, instanceIndices) }
                        .mapIndexed {index, predictedClass -> predictedClass == reader.getClassValue(instanceIndices[index])}
                        .filterNot { it }
                        .size
                        .toDouble()
                        .div(instanceIndices.size)
            }.toDoubleArray()

    private class ScrambledFeatureReader constructor(
            val reader: FeatureReader,
            val scrambledIndex: Int,
            val randomSeed: Long) : FeatureReader {

        val scrambledIndices: IntArray = IntStream.range(0, reader.numInstances).toArray()
                .also { Utility.shuffleArray(it, Random(randomSeed)) }

        override fun getClassValue(instanceIndex: Int): Int {
            return reader.getClassValue(instanceIndex)
        }

        override fun getValue(instanceIndex: Int, attributeIndex: Int): Double =
                if (attributeIndex == scrambledIndex)
                    reader.getValue(scrambledIndices[instanceIndex], attributeIndex)
                else
                    reader.getValue(instanceIndex, attributeIndex)

        override fun getNumInstances(): Int = reader.numInstances

        override fun getNumFeatures(): Int = reader.numFeatures

        override fun getClassIndex(): Int = reader.classIndex

        override fun getNumClasses(): Int = reader.numClasses
    }
}