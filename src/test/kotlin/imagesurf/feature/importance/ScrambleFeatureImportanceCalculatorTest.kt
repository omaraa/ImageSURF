package imagesurf.feature.importance

import imagesurf.classifier.Classifier
import imagesurf.feature.FeatureReader
import imagesurf.feature.ImageFeatures
import org.junit.Test

import org.assertj.core.api.Assertions.*

class ScrambleFeatureImportanceCalculatorTest {

    @Test
    fun calculateFeatureImportance() {

        val importanceCalculator = ScrambleFeatureImportanceCalculator(42)

        val classifier = object : Classifier {
            override fun distributionForInstance(data: FeatureReader, instanceIndex: Int): DoubleArray = DoubleArray(0)
            override fun distributionForInstances(data: FeatureReader): Array<DoubleArray> = arrayOf(DoubleArray(0))

            override fun classForInstances(data: FeatureReader, instanceIndices: IntArray): IntArray =
                    instanceIndices.map {
                        data.getValue(it, 0).toInt() % 2
                    }.toIntArray()
        }

        val data = ImageFeatures.ByteReader(
                arrayOf(
                        byteArrayOf(0,1,2,3,4,5,6,7), // Feature 1 is index and maps to class
                        byteArrayOf(0,0,0,0,0,0,0,0), // Feature 2 does not contain information
                        byteArrayOf(0,1,0,1,0,1,0,1) // Class is feature one % 2
                ),
                2
        )

        val importance = importanceCalculator.calculateFeatureImportance(classifier, data)

        //Classifier only looks at feature 1 so accuracy should drop if feature 1 is randomised
        assertThat(importance[0]).isGreaterThan(importance[1])
    }
}