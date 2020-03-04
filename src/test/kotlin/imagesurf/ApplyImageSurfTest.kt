package imagesurf.classifier

import ij.ImagePlus
import imagesurf.ApplyImageSurf
import imagesurf.feature.PixelType
import imagesurf.feature.calculator.FeatureCalculator
import imagesurf.feature.importance.ScrambleFeatureImportanceCalculator
import imagesurf.reader.ByteReader
import imagesurf.util.ProgressListener
import imagesurf.util.Training
import org.assertj.core.api.Assertions.*
import org.assertj.core.data.Percentage
import org.junit.Test
import org.scijava.Context
import org.scijava.app.StatusService
import org.scijava.app.event.StatusEvent
import org.scijava.plugin.PluginInfo

import java.io.File
import java.util.*

class ApplyImageSurfTest {

    @Test
    fun `classifies training pixels accurately in single channel image`() {

        val labelImageFile = File(javaClass.getResource("/nomarski/annotated-2-fixed/Nomarski-7DIV.png").file)
        val rawImageFile = File(javaClass.getResource("/nomarski/raw-unannotated/Nomarski-7DIV.png").file)
        val expectedOutputFile = File(javaClass.getResource("/nomarski/output-2/Nomarski-7DIV.png").file)

        val expected = (ImagePlus(expectedOutputFile.absolutePath).processor.convertToByte(false).pixels as ByteArray)
                .map {
                    when (it) {
                        (-1).toByte() -> 0.toByte()
                        else -> (-1).toByte()
                    }}
                .toByteArray()

        `classifies image accurately`(
                labelImageFile = labelImageFile,
                rawImageFile = rawImageFile,
                features = selectedFeaturesSingleChannel,
                numChannels = 1,
                expected = expected
        )
    }

    @Test
    fun `classifies training pixels accurately in multi channel image`() {

        val labelImageFile = File(javaClass.getResource("/immuno/annotated/amyloid-beta.tif").file)
        val unlabelledImageFile = File(javaClass.getResource("/immuno/unannotated/amyloid-beta.tif").file)
        val rawImageFile = File(javaClass.getResource("/immuno/raw/amyloid-beta.tif").file)
        val expectedOutputFile = File(javaClass.getResource("/immuno/segmented/amyloid-beta.tif").file)

        val expected = (ImagePlus(expectedOutputFile.absolutePath).processor.convertToByte(false).pixels as ByteArray)

        `classifies image accurately`(
                labelImageFile = labelImageFile,
                unlabelledImageFile = unlabelledImageFile,
                rawImageFile = rawImageFile,
                features = selectedFeaturesMultiChannel,
                numChannels = 2,
                expected = expected
        )
    }

    private fun `classifies image accurately`(
            labelImageFile: File,
            unlabelledImageFile: File? = null,
            rawImageFile: File,
            featureFile: File? = null,
            features: Array<FeatureCalculator>,
            numChannels: Int,
            expected: ByteArray
    ) {
        val trainingExamples = Training.getTrainingExamples(
                arrayOf(labelImageFile),
                unlabelledImageFile?.let{ arrayOf(it)} ?: arrayOf(rawImageFile),
                arrayOf(rawImageFile),
                featureFile?.let { arrayOf(it) } ?: null,
                random,
                null,
                examplePortion,
                false,
                pixelType,
                features
        ).map { it as ByteArray }.toTypedArray()

        val reader = ByteReader(trainingExamples, trainingExamples.size - 1)
        val randomForest = randomForest(reader)

        val output = ApplyImageSurf.run(ImageSurfClassifier(
                randomForest,
                features,
                pixelType,
                numChannels
        ), ImagePlus(rawImageFile.absolutePath),
                DUMMY_STATUS_SERVICE
        )
                .getPixels(1)
                .let { (it as ByteArray) }

        assertThat(output.distinct().size).isGreaterThan(1)

        val correct = expected.zip(output).filter { (expected, actual) -> expected == actual }.size
        assertThat(correct).isCloseTo(output.size, Percentage.withPercentage(8.0))
    }

    companion object {

        private val random = Random(42)
        private val examplePortion = 30
        private val pixelType = PixelType.GRAY_8_BIT
        val selectedFeaturesSingleChannel = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 1)
        val selectedFeaturesMultiChannel = PixelType.GRAY_8_BIT.getAllFeatureCalculators(0, 25, 3)
        val featureImportanceCalculator = ScrambleFeatureImportanceCalculator(42)


        val progressListener: Training.TrainingProgressListener = object : Training.TrainingProgressListener {
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

        fun randomForest(reader: ByteReader): RandomForest = randomForest(reader, rfProgressListener)

        fun randomForest(reader: ByteReader, rfProgressListener: ProgressListener?): RandomForest {
            return RandomForest.Builder()
                    .withNumTrees(500)
                    .withMaxDepth(50)
                    .withNumAttributes(0)
                    .withBagSize(30)
                    .withRandomSeed(random.nextInt())
                    .withData(reader)
                    .let {
                        if (rfProgressListener != null)
                            it.withProgressListener(rfProgressListener)
                        else it
                    }
                    .build()
        }

        val DUMMY_STATUS_SERVICE = object : StatusService {
            override fun getInfo(): PluginInfo<*> {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getContext(): Context = context()

            override fun getPriority(): Double = Double.MAX_VALUE

            override fun setInfo(p0: PluginInfo<*>?) {}

            override fun setPriority(p0: Double) {}

            override fun context(): Context {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun warn(p0: String?) {            }

            override fun clearStatus() {            }

            override fun getStatusMessage(p0: String?, p1: StatusEvent?): String = ""

            override fun showStatus(p0: String?) {}

            override fun showStatus(p0: Int, p1: Int, p2: String?) {}

            override fun showStatus(p0: Int, p1: Int, p2: String?, p3: Boolean) {}

            override fun showProgress(p0: Int, p1: Int) {}
        }
    }
}