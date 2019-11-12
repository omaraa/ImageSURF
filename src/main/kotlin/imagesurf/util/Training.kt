package imagesurf.util

import ij.CompositeImage
import ij.ImagePlus
import imagesurf.feature.FeatureReader
import imagesurf.feature.ImageFeatures
import imagesurf.feature.PixelType
import imagesurf.feature.calculator.FeatureCalculator
import imagesurf.reader.ByteReader
import imagesurf.reader.ShortReader
import java.io.File
import java.util.*

object Training {
    fun getSelectedFeaturesReader(optimalFeatures: Array<FeatureCalculator>, allFeatures: Array<FeatureCalculator>,
                                  trainingExamples: Array<Any>, pixelType: PixelType): FeatureReader {

        val numFeatures = optimalFeatures.size

        val optimisedTrainingExamples: Array<Any?> = arrayOfNulls(numFeatures + 1)

        for (i in 0 until numFeatures)
            optimisedTrainingExamples[i] = trainingExamples[allFeatures.indexOf(optimalFeatures[i])]

        //Add class annotations
        optimisedTrainingExamples[numFeatures] = trainingExamples[trainingExamples.size - 1]

        return when (pixelType) {
            PixelType.GRAY_8_BIT -> ByteReader(optimisedTrainingExamples, numFeatures)
            PixelType.GRAY_16_BIT -> ShortReader(optimisedTrainingExamples, numFeatures)
        }
    }

    fun getTrainingExamples(labelFiles: Array<File>, unlabelledFiles: Array<File>, rawImageFiles: Array<File>,
                            featureFiles: Array<File>?, random: Random, trainingProgressListener: TrainingProgressListener?,
                            examplePortion: Int, saveCalculatedFeatures: Boolean,
                            pixelType: PixelType, selectedFeatures: Array<FeatureCalculator>): Array<Any> {

        val progressListener = trainingProgressListener ?: TrainingProgressListener.dummy

        if (labelFiles.isEmpty())
            throw RuntimeException("No valid label files")

        val numImages = labelFiles.size
        val examplePixelIndices = getLabelledPixelIndices(labelFiles, unlabelledFiles, progressListener).let {
            selectExamplePixelIndices(it, random, examplePortion)
        }

        val classColors = emptyList<Int>().toMutableList()

        val expectedNumChannels: Int = rawImageFiles.filterIndexed { index, _ -> examplePixelIndices[index].isNotEmpty() }
                .first().let { getImagePlus(it).nChannels }

        val examples: List<List<FeatureImage<Any>>> = rawImageFiles.indices
                .map { imageIndex ->
                    val rawImage = getRawTrainingImage(rawImageFiles[imageIndex], expectedNumChannels)

                    val (imageFeatures: ImageFeatures, savedFeatures: Collection<FeatureCalculator>) =
                            getImageFeatures(featureFiles, imageIndex, progressListener, numImages, rawImage, pixelType)

                    val calculatedFeatures =
                            calculateFeatures(progressListener, imageIndex, numImages, imageFeatures, selectedFeatures)

                    if (featureFiles != null && calculatedFeatures && saveCalculatedFeatures && !savedFeatures.containsAll(imageFeatures.easilyComputedFeatures)) {
                        writeFeatures(progressListener, imageIndex, numImages, featureFiles, imageFeatures)
                    }

                    progressListener.showStatus("Extracting examples from image " + (imageIndex + 1) + "/" + numImages)

                    val labelImagePixels = getLabelImagePixels(labelFiles[imageIndex])

                    val selectedPixels = examplePixelIndices[imageIndex]

                    //Add label colours to classColors list if not yet there
                    classColors.addAll(selectedPixels
                            .map { pixelIndex -> labelImagePixels[pixelIndex] }
                            .groupBy { pixelValue -> pixelValue }
                            .keys.filter { !classColors.contains(it) })

                    // TODO: Assumes each feature calculate only produces one feature image, but future ones may produce more.
                    (selectedFeatures.map { featureCalculator ->
                        val featurePixels = (imageFeatures.getFeaturePixels(0, 0, featureCalculator) as Array<Any>)[0]

                        when (pixelType) {
                            PixelType.GRAY_8_BIT -> ByteFeatureImage(selectedPixels.map { (featurePixels as ByteArray)[it] }.toByteArray())
                            PixelType.GRAY_16_BIT -> ShortFeatureImage(selectedPixels.map { (featurePixels as ShortArray)[it] }.toShortArray())
                        }
                    } + when (pixelType) {
                        PixelType.GRAY_8_BIT -> {
                            val classMap = classColors.mapIndexed { index, rgb -> rgb to index.toByte() }.toMap()
                            ByteFeatureImage(selectedPixels.map { classMap[labelImagePixels[it]]!! }.toByteArray())
                        }
                        PixelType.GRAY_16_BIT -> {
                            val classMap = classColors.mapIndexed { index, rgb -> rgb to index.toShort() }.toMap()
                            ShortFeatureImage(selectedPixels.map { classMap[labelImagePixels[it]]!! }.toShortArray())
                        }
                    }) as List<FeatureImage<Any>>
                }

        if (classColors.size > 127)
            throw RuntimeException("Detected ${classColors.size} classes. Maximum allowed is 128. Is there a" +
                    " discrepancy between pixel values in the annotated and un-annotated images?" +
                    " This may be caused by differing colour profiles.")
        return examples
                .mapClassValues(pixelType, classColors)
                .collapseFeatures(pixelType, selectedFeatures.size)
                .map { it.pixels }
                .toTypedArray()
    }

    private fun writeFeatures(progressListener: TrainingProgressListener, imageIndex: Int, numImages: Int, featureFiles: Array<File>, imageFeatures: ImageFeatures) {
        progressListener.showStatus("Writing features for image ${imageIndex + 1}/$numImages")
        progressListener.logInfo("Writing features to ${featureFiles[imageIndex].toPath()}")
        try {
            imageFeatures.serialize(featureFiles[imageIndex].toPath())
        } catch (e: Exception) {
            throw RuntimeException("Failed to save features to file ${featureFiles[imageIndex].absolutePath}", e)
        }

        progressListener.logInfo("Wrote features to ${featureFiles[imageIndex].toPath()}")
    }

    private fun calculateFeatures(progressListener: TrainingProgressListener, imageIndex: Int, numImages: Int, imageFeatures: ImageFeatures, selectedFeatures: Array<FeatureCalculator>): Boolean {
        var calculatedFeatures = false

        try {
            val ifProgressListener: ProgressListener =
                    object : ProgressListener {
                        override fun onProgress(current: Int, max: Int, message: String) {
                            progressListener.showStatus(current, max, "Calculating features for image ${imageIndex + 1}/$numImages")
                        }
                    }
            imageFeatures.addProgressListener(ifProgressListener)

            if (imageFeatures.calculateFeatures(0, 0, selectedFeatures))
                calculatedFeatures = true

            imageFeatures.removeProgressListener(ifProgressListener)

        } catch (e: Exception) {
            throw RuntimeException("Failed to calculate features", e)
        }
        return calculatedFeatures
    }

    private fun getImageFeatures(featureFiles: Array<File>?, imageIndex: Int, progressListener: TrainingProgressListener, numImages: Int, rawImage: ImagePlus, pixelType: PixelType): Pair<ImageFeatures, Collection<FeatureCalculator>> {
        val imageFeatures: ImageFeatures
        val savedFeatures: Collection<FeatureCalculator>
        if (featureFiles==null || !featureFiles[imageIndex].exists()) {
            progressListener.logInfo("Reading image ${imageIndex + 1}/$numImages")
            imageFeatures = ImageFeatures(rawImage)
            savedFeatures = ArrayList(0)
        } else {
            progressListener.showStatus("Reading features for image ${imageIndex + 1}/$numImages")
            progressListener.logInfo("Reading features for image ${imageIndex + 1}/$numImages")
            try {
                imageFeatures = ImageFeatures.deserialize(featureFiles[imageIndex].toPath())

                if (imageFeatures.pixelType != pixelType)
                    throw RuntimeException("imagesurf.util.Training images must all be either 8 or 16 bit greyscale format. " +
                            "${featureFiles[imageIndex].name} is ${imageFeatures.pixelType}, expected $pixelType")
            } catch (e: Exception) {
                throw RuntimeException("Failed to read image features", e)
            }

            savedFeatures = imageFeatures.features
        }

        return Pair(imageFeatures, savedFeatures)
    }

    private fun getRawTrainingImage(imageFile: File, numChannels: Int): ImagePlus = getImagePlus(imageFile).apply {
        if (this.nFrames * this.nSlices > 1)
            throw RuntimeException("Training image ${this.title} not valid. Images must be single plane.")

        if (this.nChannels != numChannels)
            throw RuntimeException("Training image ${this.title} not valid. Image has ${this.nChannels}" +
                    " channels. Expected $numChannels.")
    }


    private fun getImagePlus(rawImageFile: File): ImagePlus {

        val imagePlus = ImagePlus(rawImageFile.absolutePath)

        if (imagePlus.type == ImagePlus.COLOR_RGB) {
            return if (Utility.isGrayScale(imagePlus)) {
                ImagePlus(imagePlus.title, imagePlus.channelProcessor)
            } else {
                CompositeImage(imagePlus, CompositeImage.GRAYSCALE)
            }
        }
        return imagePlus
    }

    private fun getLabelledPixelIndices(labelFiles: Array<File>, unlabelledFiles: Array<File>, progressListener: TrainingProgressListener?): List<IntArray> {
        var progressListener = progressListener
        if (progressListener == null)
            progressListener = TrainingProgressListener.dummy

        return labelFiles.indices.map { imageIndex ->
            progressListener.showStatus(imageIndex + 1, labelFiles.size,
                    "Scanning image labels ${imageIndex + 1}/${labelFiles.size}")

            val unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex])
            val labelImagePixels = getLabelImagePixels(labelFiles[imageIndex])

            if (unlabelledImagePixels.size != labelImagePixels.size) {
                progressListener.logError("Un-annotated and annotated images '${unlabelledFiles[imageIndex].name}'" +
                        " differ in size")
            }

            unlabelledImagePixels.indices
                    .filter { labelImagePixels[it] != unlabelledImagePixels[it] }.toIntArray()
        }
    }

    private fun getLabelImagePixels(labelFile: File): IntArray {
        val labelImage = ImagePlus(labelFile.absolutePath)
        if (!(labelImage.nChannels == 1 || labelImage.nChannels == 3) || labelImage.nFrames * labelImage.nSlices > 1)
            throw RuntimeException("Label image ${labelImage.title} not valid. Label images must be single plane RGB format.")

        return labelImage
                .processor.convertToRGB().pixels as IntArray
    }

    private fun selectExamplePixelIndices(labelledPixels: List<IntArray>, random: Random, examplePortion: Int): List<IntArray> {
        if (examplePortion >= 100)
            return labelledPixels

        val totalLabelledPixels = labelledPixels.map { it.size}.fold(0) { acc, i -> acc + i }

        if (totalLabelledPixels == 0)
            throw RuntimeException("No labels found in label files")

        val selectedPixelIndices = IntArray(totalLabelledPixels * examplePortion / 100) { random.nextInt(totalLabelledPixels) }.sortedArray()

        var cur = 0
        val numberedPixels = labelledPixels.mapIndexed { imageIndex, labelledPixelIndices ->
            imageIndex
            labelledPixelIndices.map { pixelIndex -> cur++ to Pair(imageIndex, pixelIndex) }
        }.flatten().toMap()

        return selectedPixelIndices
                .map { numberedPixels[it] }
                .groupBy { it!!.first }
                .let {
                    it.keys.sorted().map { imageIndex ->
                        it[imageIndex]!!.map { pair -> pair!!.second }.toIntArray()
                    }
                }
    }

    interface TrainingProgressListener {
        fun logInfo(Message: String)
        fun logError(message: String)
        fun showStatus(progress: Int, total: Int, message: String)
        fun showStatus(message: String)

        companion object {
            val dummy: TrainingProgressListener
                get() = object : TrainingProgressListener {
                    override fun logInfo(Message: String) = Unit
                    override fun logError(message: String) = Unit
                    override fun showStatus(progress: Int, total: Int, message: String) = Unit
                    override fun showStatus(message: String) = Unit
                }
        }
    }

    interface FeatureImage<T> {
        val pixels: Any
        fun map(transform: (T) -> T): FeatureImage<T>

        operator fun plus(featureImage: FeatureImage<T>): FeatureImage<T>
    }

    class ByteFeatureImage(private val bytePixels: ByteArray) : FeatureImage<Byte> {
        override fun plus(featureImage: FeatureImage<Byte>) =
                ByteFeatureImage(bytePixels + featureImage.pixels as ByteArray)

        override fun map(transform: (Byte) -> Byte): FeatureImage<Byte> =
                ByteFeatureImage(ByteArray(bytePixels.size) { i -> transform(bytePixels[i]) })

        override val pixels: Any get() = bytePixels
    }

    class ShortFeatureImage(private val shortPixels: ShortArray) : FeatureImage<Short> {
        override fun plus(featureImage: FeatureImage<Short>) =
                ShortFeatureImage(shortPixels + featureImage.pixels as ShortArray)

        override fun map(transform: (Short) -> Short): FeatureImage<Short> =
                ShortFeatureImage(ShortArray(shortPixels.size) { i -> transform(shortPixels[i]) })

        override val pixels: Any get() = shortPixels
    }
}

private fun List<List<Training.FeatureImage<Any>>>.collapseFeatures(pixelType: PixelType, numFeatures: Int) = fold(when (pixelType) {
    PixelType.GRAY_8_BIT -> List(numFeatures + 1) { _ -> Training.ByteFeatureImage(ByteArray(0)) }
    PixelType.GRAY_16_BIT -> List(numFeatures + 1) { _ -> Training.ShortFeatureImage(ShortArray(0)) }
}) { acc, cur ->
    acc.mapIndexed { index, accumulatedFeatures ->
        (cur[index] + accumulatedFeatures as Training.FeatureImage<Any>)
    }
}

private fun List<List<Training.FeatureImage<Any>>>.mapClassValues(pixelType: PixelType, classColors: List<Int>) = map { imageFeatures ->
    imageFeatures.mapIndexed { index, feature ->
        if (index == imageFeatures.size - 1) {
            val unordered = classColors.mapIndexed { classIndex, classRgb -> classRgb to classIndex }.toMap()
            val classMap = classColors.sorted().mapIndexed { classIndex, classRgb ->
                unordered[classRgb] to classIndex
            }.toMap()

            when (pixelType) {
                PixelType.GRAY_8_BIT -> feature.map { classMap[(it as Byte).toInt()]!!.toByte() }
                PixelType.GRAY_16_BIT -> feature.map { classMap[(it as Short).toInt()]!!.toShort() }
            }
        } else {
            feature
        }
    }
}