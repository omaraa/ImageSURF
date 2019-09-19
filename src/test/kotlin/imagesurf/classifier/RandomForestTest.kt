package imagesurf.classifier

import imagesurf.feature.ImageFeatures
import imagesurf.feature.PixelType
import imagesurf.feature.importance.ScrambleFeatureImportanceCalculator
import imagesurf.feature.calculator.FeatureCalculator
import imagesurf.util.ProgressListener
import imagesurf.util.Training
import org.assertj.core.api.Assertions.*
import org.assertj.core.data.Percentage
import org.junit.Test

import java.io.File
import java.util.*

class RandomForestTest {

    @Test
    fun `classifies training pixels accurately in single channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/nomarski/annotated-2-fixed/Nomarski-7DIV.png").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/nomarski/raw-unannotated/Nomarski-7DIV.png").file))
        val featureFile = null

        val trainingExamples = Training.getTrainingExamples(labelImageFile, rawImageFile, rawImageFile, featureFile,
                random, null, examplePortion, false, pixelType,
                selectedFeaturesSingleChannel).map { it as ByteArray }.toTypedArray()

        `classifies training pixels accurately`(trainingExamples)
    }

    @Test
    fun `classifies training pixels accurately in multi channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val unlabelledImageFile = arrayOf(File(javaClass.getResource("/immuno/unannotated/amyloid-beta.tif").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val featureFile = null

        val selectedFeatures = selectedFeaturesMultiChannel

        val trainingExamples = Training.getTrainingExamples(labelImageFile, unlabelledImageFile, rawImageFile, featureFile,
                random, null, examplePortion, false, pixelType,
                selectedFeatures).map { it as ByteArray }.toTypedArray()

        `classifies training pixels accurately`(trainingExamples)
    }

    private fun `classifies training pixels accurately`(trainingExamples: Array<ByteArray>) {
        val reader = ImageFeatures.ByteReader(trainingExamples, trainingExamples.size - 1)
        val randomForest = randomForest(reader)

        val classifications = randomForest.classForInstances(reader)

        assertThat(classifications.distinct().size).isGreaterThan(1)

        val correct = reader.classes.map(Short::toInt).zip(classifications.toList()).filter { (expected, actual) -> expected == actual }.size

        assertThat(correct).isCloseTo(classifications.size, Percentage.withPercentage(1.0))
    }

    @Test
    fun `feature order does not affect importance calculation in single channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/nomarski/annotated-2-fixed/Nomarski-7DIV.png").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/nomarski/raw-unannotated/Nomarski-7DIV.png").file))

        val featureFile = null

        val trainingExamples = Training.getTrainingExamples(labelImageFile, rawImageFile, rawImageFile, featureFile,
                random, progressListener, examplePortion, false, pixelType,
                selectedFeaturesSingleChannel).map { it as ByteArray }.toTypedArray()


        `feature order does not affect importance calculation`(trainingExamples)
    }

    @Test
    fun `feature order does not affect importance calculation in multi channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val unlabelledImageFile = arrayOf(File(javaClass.getResource("/immuno/unannotated/amyloid-beta.tif").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val featureFile = null

        val selectedFeatures = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 3)

        val trainingExamples = Training.getTrainingExamples(labelImageFile, unlabelledImageFile, rawImageFile, featureFile,
                random, null, examplePortion, false, pixelType,
                selectedFeatures).map { it as ByteArray }.toTypedArray()

        `feature order does not affect importance calculation`(trainingExamples)
    }


    private fun `feature order does not affect importance calculation`(trainingExamples: Array<ByteArray>) {
        val featureImportance = ImageFeatures.ByteReader(trainingExamples, trainingExamples.size - 1)
                .let {
                    featureImportanceCalculator.calculateFeatureImportance(randomForest(it), it)
                }.dropLast(1)

        val reversedImportance = ImageFeatures.ByteReader(trainingExamples.reversedArray(), 0)
                .let {
                    featureImportanceCalculator.calculateFeatureImportance(randomForest(it), it)
                }.drop(1)

        featureImportance.zip(reversedImportance).forEach { (expected, actual) ->
            assertThat(actual).isCloseTo(actual, Percentage.withPercentage(1.0))
        }
    }

    @Test
    fun `top features should be sufficient for classification in single channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/nomarski/annotated-2-fixed/Nomarski-7DIV.png").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/nomarski/raw-unannotated/Nomarski-7DIV.png").file))

        val featureFile = null

        val trainingExamples = Training.getTrainingExamples(labelImageFile, rawImageFile, rawImageFile, featureFile,
                random, progressListener, examplePortion, false, pixelType,
                selectedFeaturesSingleChannel).map { it as ByteArray }.toTypedArray()

        `top features should be sufficient for classification`(trainingExamples, selectedFeaturesSingleChannel)
    }

    @Test
    fun `top features should be sufficient for classification in multi channel image`() {

        val labelImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val unlabelledImageFile = arrayOf(File(javaClass.getResource("/immuno/unannotated/amyloid-beta.tif").file))
        val rawImageFile = arrayOf(File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file))
        val featureFile = null

        val selectedFeatures = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 3)

        val trainingExamples = Training.getTrainingExamples(labelImageFile, unlabelledImageFile, rawImageFile, featureFile,
                random, null, examplePortion, false, pixelType,
                selectedFeatures).map { it as ByteArray }.toTypedArray()

        `top features should be sufficient for classification`(trainingExamples, selectedFeaturesMultiChannel)
    }


    private fun `top features should be sufficient for classification`(trainingExamples: Array<ByteArray>, featureCalculators: Array<FeatureCalculator>) {

        val reader = ImageFeatures.ByteReader(trainingExamples, trainingExamples.size - 1)
        val randomForest = randomForest(reader)

        val featureImportanceCalculator = ScrambleFeatureImportanceCalculator(42);

        val optimalFeatures = featureImportanceCalculator.selectOptimalFeatures(10, reader, randomForest, featureCalculators) { println(it)}
        val optimalTrainingExamples = optimalFeatures.map { featureCalculators.indexOf(it) }.map { trainingExamples[it] } + trainingExamples.last()
        val optimalReader = ImageFeatures.ByteReader(optimalTrainingExamples, optimalTrainingExamples.lastIndex)

        assertThat(optimalReader.numFeatures).isEqualTo(11)

        println("Top Features:")
        optimalFeatures.forEach { println(it.descriptionWithTags)}

        val optimalRandomForest = randomForest(optimalReader)
        val classifications = optimalRandomForest.classForInstances(optimalReader)
        val correct = optimalReader.classes.map(Short::toInt).zip(classifications.toList()).filter { (expected, actual) -> expected == actual }.size

        assertThat(correct).isCloseTo(classifications.size, Percentage.withPercentage(7.0))
    }

    companion object {

        private val random = Random(42)
        private val examplePortion = 30
        private val pixelType = PixelType.GRAY_8_BIT
        val selectedFeaturesSingleChannel = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 1)
        val selectedFeaturesMultiChannel = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 3)
        val featureImportanceCalculator = ScrambleFeatureImportanceCalculator(42)


        val progressListener: Training.TrainingProgressListener = object: Training.TrainingProgressListener {
            override fun logInfo(message: String) = println(message)
            override fun logError(message: String) = System.err.println(message)
            override fun showStatus(progress: Int, total: Int, message: String) = println("$progress/$total: $message")
            override fun showStatus(message: String) = print(message)
        }

        val rfProgressListener: ProgressListener =
                object : ProgressListener {
                    override fun onProgress(current: Int, max: Int, message: String) {
                        println("$current/$max: $message")
                    }
                }

        fun randomForest(reader: ImageFeatures.ByteReader): RandomForest = randomForest(reader, rfProgressListener)

        fun randomForest(reader: ImageFeatures.ByteReader, rfProgressListener: ProgressListener?): RandomForest {
            return RandomForest.Builder()
                    .withNumTrees(500)
                    .withMaxDepth(50)
                    .withNumAttributes(0)
                    .withBagSize(30)
                    .withRandomSeed(random.nextInt())
                    .withData(reader)
                    .let{
                        if(rfProgressListener!= null)
                            it.withProgressListener(rfProgressListener)
                        else it
                    }
                    .build()
        }
    }
}