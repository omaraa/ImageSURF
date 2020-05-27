package imagesurf.util

import ij.ImagePlus
import ij.ImageStack
import imagesurf.classifier.ImageSurfClassifier
import imagesurf.feature.PixelType
import imagesurf.feature.SurfImage
import imagesurf.feature.calculator.FeatureCalculator
import org.scijava.app.StatusService
import java.util.concurrent.ExecutionException

object UtilityKt {

    @Throws(ExecutionException::class, InterruptedException::class)
    fun calculateImageFeatures(featureCalculators: Array<FeatureCalculator>, surf: SurfImage, statusService: StatusService, pixelType: PixelType): ImageStack {

        val outputStack = ImageStack(surf.width, surf.height)

        var currentSlice = 1
        for (z in 0 until surf.numSlices)
            for (t in 0 until surf.numFrames) {
                val finalCurrentSlice = currentSlice
                val imageFeaturesProgressListener = object : ProgressListener {
                    override fun onProgress(current: Int, max: Int, message: String) {
                        statusService.showStatus(current, max, "Calculating features for plane " + finalCurrentSlice + "/" +
                                surf.numSlices * surf.numFrames)
                    }
                }

                surf.addProgressListener(imageFeaturesProgressListener)
                if (surf.calculateFeatures(z, t, featureCalculators))
                    surf.removeProgressListener(imageFeaturesProgressListener)

                for (f in featureCalculators) {
                    when (pixelType) {

                        PixelType.GRAY_8_BIT -> outputStack.addSlice(f.descriptionWithTags, (surf.getFeaturePixels(z, t, f) as Array<ByteArray>)[0])
                        PixelType.GRAY_16_BIT -> outputStack.addSlice(f.descriptionWithTags, (surf.getFeaturePixels(z, t, f) as Array<ShortArray>)[0])
                    }
                }

                currentSlice++
            }

        return outputStack
    }

    fun getPixelType(imagePlus: ImagePlus): PixelType = when (imagePlus.type) {
        ImagePlus.COLOR_256, ImagePlus.COLOR_RGB, ImagePlus.GRAY8 -> PixelType.GRAY_8_BIT
        ImagePlus.GRAY16 -> PixelType.GRAY_16_BIT
        ImagePlus.GRAY32 -> throw IllegalArgumentException("32-bit grayscale images are not yet supported.")
        else -> throw IllegalArgumentException("Image type not supported.")
    }

    fun describeClassifier(classifier: ImageSurfClassifier?): String {
        if (null == classifier)
            return "Classifier does not exist (NULL)."


        val randomForest = classifier.randomForest ?: return "Classifier does not exist (not trained)."

        val sb = StringBuilder()

        val numAttributes = randomForest.numAttributes

        var bit = "???"

        when (classifier.pixelType) {

            PixelType.GRAY_8_BIT -> bit = "8-bit"
            PixelType.GRAY_16_BIT -> bit = "16-bit"
        }

        sb.append("Random forest was built for ")
                .append(bit)
                .append(" images with ")
                .append(randomForest.numTrees)
                .append(" trees to a maximum depth of ")
                .append(randomForest.maxDepth)
                .append(" nodes, considering ")
                .append(numAttributes)
                .append(" features at each decision node.")

        sb.append("\n\nFeatures used:\n")

        for (f in classifier.features)
            sb.append(f.descriptionWithTags).append("\n")

        return sb.toString()
    }

    fun calculateNumMergedChannels(numChannels: Int): Int {
        return (1 shl numChannels) - 1
    }

    fun getClassColors(numClasses: Int): ByteArray {
        val classColors = ByteArray(numClasses)
        classColors[0] = 0
        classColors[classColors.size - 1] = 0xff.toByte()

        if (numClasses > 2) {
            val interval = 0xff / (numClasses - 1)
            for (i in 1 until classColors.size - 1)
                classColors[i] = (interval * i).toByte()
        }

        return classColors
    }

    class MessageProgress(private val statusService: StatusService) : ProgressListener {
        var message = ""

        override fun onProgress(current: Int, max: Int, message: String) =
                statusService.showStatus(current, max, message)
    }
}
