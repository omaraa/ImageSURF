package imagesurf.util

import ij.CompositeImage
import ij.ImagePlus
import ij.io.FileSaver
import imagesurf.ApplyImageSurf
import imagesurf.ImageSurfSettings
import imagesurf.TrainImageSurfMultiClass
import imagesurf.classifier.ImageSurfClassifier
import imagesurf.classifier.RandomForest
import imagesurf.feature.FeatureReader
import imagesurf.feature.PixelType
import imagesurf.feature.SurfImage
import imagesurf.feature.calculator.FeatureCalculator
import org.scijava.app.StatusService
import org.scijava.log.LogService
import org.scijava.prefs.PrefService
import java.io.File
import java.io.FileFilter
import java.nio.file.Files
import java.util.*
import java.util.stream.IntStream

object Training {
    data class Paths(
            val labelPath: File,
            val rawImagePath: File,
            val unlabelledImagePath: File,
            val imagePattern: String
    ) {
        private val imageLabelFileFilter = FileFilter { labelPath ->
            if (!labelPath.isFile || labelPath.isHidden || !labelPath.canRead()) return@FileFilter false
            val imageName = labelPath.name

            //Check for matching image. If it doesn't exist or isn't suitable, exclude this label image
            val imagePath = File(rawImagePath, imageName)

            return@FileFilter !(!imagePath.exists() || imagePath.isHidden || !imagePath.isFile || !imageName.contains(imagePattern))
        }


        val labelFiles: List<File> = labelPath.listFiles(imageLabelFileFilter)!!.asList()

        val rawImageFiles = labelFiles.map { File(rawImagePath, it.name) }
        val unlabelledFiles = labelFiles.map { File(unlabelledImagePath, it.name) }
        val imageSurfDataPath: File = File(rawImagePath, TrainImageSurfMultiClass.IMAGESURF_DATA_FOLDER_NAME)
        val featuresPath = File(imageSurfDataPath, "features")
        val featureFiles = rawImageFiles.map { i: File -> File(featuresPath, i.name + ".features") }

        fun validate() {
            if (!labelPath.exists())
                throw RuntimeException("Could not access training label folder " + labelPath.absolutePath)

            if (labelFiles.isEmpty())
                throw RuntimeException("Could not find any training labels in folder " + labelPath.absolutePath)
        }
    }

    class Verification(
            val expectedCounts: IntArray,
            val actualCounts: IntArray,
            val correct: Int
    ) {
        val totalPixels: Int = expectedCounts.sum()
        val numClasses = expectedCounts.size

        fun describe(): String {
            //Output some info about training to the log and output text
            val info = StringBuilder("Classes in training set - ")
            for (i in 0 until numClasses) info.append(i.toString() + ": " + actualCounts[i] + "\t")
            info.append("\nClasses in verification set - ")
            for (i in 0 until numClasses) info.append(i.toString() + ": " + expectedCounts[i] + "\t")
            info.append("""\nSegmenter classifies $correct/$totalPixels """ + (correct.toDouble()
                    / totalPixels) * 100 + "%) of the training pixels correctly.")

            return info.toString()
        }
    }

    @Throws(java.lang.Exception::class)
    fun segmentTrainingImages(imageSurfClassifier: ImageSurfClassifier, paths: Paths, log: LogService, prefService: PrefService, statusService: StatusService): List<File> {
        val tileSize = prefService.getInt(ImageSurfSettings.IMAGESURF_TILE_SIZE, ImageSurfSettings.DEFAULT_TILE_SIZE)
        val outputFolder = Files.createTempDirectory("imagesurf-" + System.nanoTime())

        val rawImageFiles = paths.rawImageFiles
        val featureFiles: List<File?> = paths.featureFiles
        val numImages = rawImageFiles.size

        return rawImageFiles.map { File(outputFolder.toFile(), it.name) }
                .also {
                    it.forEachIndexed { imageIndex, outputFile ->
                        val imagePath = rawImageFiles[imageIndex]
                        val image = ImagePlus(imagePath.absolutePath)
                        val surfImage: SurfImage
                        surfImage = if (featureFiles[imageIndex] == null || !featureFiles[imageIndex]!!.exists()) {
                            SurfImage(image)
                        } else {
                            statusService.showStatus("Reading features for image " + (imageIndex + 1) + "/" + numImages)
                            log.info("Reading features for image " + (imageIndex + 1) + "/" + numImages)
                            SurfImage.deserialize(featureFiles[imageIndex]!!.toPath())
                        }
                        val segmentation = ApplyImageSurf.run(imageSurfClassifier, surfImage, statusService, tileSize)
                        val segmentationImage = ImagePlus("segmentation", segmentation)
                        val outputPath = outputFile.absolutePath
                        if (segmentation.size() > 1) FileSaver(segmentationImage).saveAsTiffStack(outputPath) else FileSaver(segmentationImage).saveAsTiff(outputPath)
                    }
                }
    }

    fun verifySegmentation(reader: FeatureReader, numClasses: Int, randomForest: RandomForest): Verification {
        val expectedClasses = randomForest.classForInstances(reader, IntStream.range(0, reader.numInstances).toArray())
        val expectedClassCount = IntArray(numClasses)
        val actualClassCount = IntArray(numClasses)

        var correct = 0
        for (i in expectedClasses.indices) {
            if (expectedClasses[i] == reader.getClassValue(i)) correct++
            expectedClassCount[expectedClasses[i]]++
            actualClassCount[reader.getClassValue(i)]++
        }

        return Verification(
                expectedCounts = expectedClassCount,
                actualCounts = actualClassCount,
                correct = correct
        )
    }

    fun getSelectedFeaturesReader(optimalFeatures: Array<FeatureCalculator>, allFeatures: Array<FeatureCalculator>,
                                  reader: FeatureReader): FeatureReader =
            optimalFeatures.map { allFeatures.indexOf(it) }
                    .filter { it != -1 }
                    .let { reader.withFeatures(it) }

    fun getTrainingExamples(paths: Paths, random: Random, trainingProgressListener: TrainingProgressListener?,
                            examplePortion: Int, saveCalculatedFeatures: Boolean,
                            pixelType: PixelType, selectedFeatures: Array<FeatureCalculator>) =
            getTrainingExamples(
                    paths.labelFiles,
                    paths.unlabelledFiles,
                    paths.rawImageFiles,
                    paths.featureFiles,
                    random,
                    trainingProgressListener,
                    examplePortion,
                    saveCalculatedFeatures,
                    pixelType,
                    selectedFeatures
            )

    fun getTrainingExamples(labelFiles: List<File>, unlabelledFiles: List<File>, rawImageFiles: List<File>, featureFiles: List<File>?, random: Random, trainingProgressListener: TrainingProgressListener?,
                            examplePortion: Int, saveCalculatedFeatures: Boolean,
                            pixelType: PixelType, selectedFeatures: Array<FeatureCalculator>): Array<Any> {

        val progressListener = trainingProgressListener ?: TrainingProgressListener.dummy

        val numImages = labelFiles.size
        val examplePixelIndices = selectExamplePixelIndices(
                getLabelledPixelIndices(labelFiles, unlabelledFiles, progressListener),
                random,
                examplePortion
        )

        val classColors = emptyList<Int>().toMutableList()

        val expectedNumChannels: Int = rawImageFiles.filterIndexed { index, _ -> examplePixelIndices[index].isNotEmpty() }
                .first().let { getImagePlus(it).nChannels }

        val examples: List<List<FeatureImage<Any>>> = rawImageFiles.indices
                .map { imageIndex ->
                    val rawImage = getRawTrainingImage(rawImageFiles[imageIndex], expectedNumChannels)

                    val (surfImage: SurfImage, savedFeatures: Collection<FeatureCalculator>) =
                            getImageFeatures(featureFiles, imageIndex, progressListener, numImages, rawImage, pixelType)

                    val calculatedFeatures =
                            calculateFeatures(progressListener, imageIndex, numImages, surfImage, selectedFeatures)

                    if (featureFiles != null && calculatedFeatures && saveCalculatedFeatures && !savedFeatures.containsAll(surfImage.easilyComputedFeatures)) {
                        writeFeatures(progressListener, imageIndex, numImages, featureFiles, surfImage)
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
                        val featurePixels = (surfImage.getFeaturePixels(0, 0, featureCalculator) as Array<Any>)[0]

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

    private fun writeFeatures(progressListener: TrainingProgressListener, imageIndex: Int, numImages: Int, featureFiles: List<File>, surfImage: SurfImage) {
        progressListener.showStatus("Writing features for image ${imageIndex + 1}/$numImages")
        progressListener.logInfo("Writing features to ${featureFiles[imageIndex].toPath()}")
        try {
            surfImage.serialize(featureFiles[imageIndex].toPath())
        } catch (e: Exception) {
            throw RuntimeException("Failed to save features to file ${featureFiles[imageIndex].absolutePath}", e)
        }

        progressListener.logInfo("Wrote features to ${featureFiles[imageIndex].toPath()}")
    }

    private fun calculateFeatures(progressListener: TrainingProgressListener, imageIndex: Int, numImages: Int, surfImage: SurfImage, selectedFeatures: Array<FeatureCalculator>): Boolean {
        var calculatedFeatures = false

        try {
            val ifProgressListener: ProgressListener =
                    object : ProgressListener {
                        override fun onProgress(current: Int, max: Int, message: String) {
                            progressListener.showStatus(current, max, "Calculating features for image ${imageIndex + 1}/$numImages")
                        }
                    }
            surfImage.addProgressListener(ifProgressListener)

            if (surfImage.calculateFeatures(0, 0, selectedFeatures))
                calculatedFeatures = true

            surfImage.removeProgressListener(ifProgressListener)

        } catch (e: Exception) {
            throw RuntimeException("Failed to calculate features", e)
        }
        return calculatedFeatures
    }

    private fun getImageFeatures(featureFiles: List<File>?, imageIndex: Int, progressListener: TrainingProgressListener, numImages: Int, rawImage: ImagePlus, pixelType: PixelType): Pair<SurfImage, Collection<FeatureCalculator>> {
        val surfImage: SurfImage
        val savedFeatures: Collection<FeatureCalculator>
        if (featureFiles == null || !featureFiles[imageIndex].exists()) {
            progressListener.logInfo("Reading image ${imageIndex + 1}/$numImages")
            surfImage = SurfImage(rawImage)
            savedFeatures = ArrayList(0)
        } else {
            progressListener.showStatus("Reading features for image ${imageIndex + 1}/$numImages")
            progressListener.logInfo("Reading features for image ${imageIndex + 1}/$numImages")
            try {
                surfImage = SurfImage.deserialize(featureFiles[imageIndex].toPath())

                if (surfImage.pixelType != pixelType)
                    throw RuntimeException("imagesurf.util.Training images must all be either 8 or 16 bit greyscale format. " +
                            "${featureFiles[imageIndex].name} is ${surfImage.pixelType}, expected $pixelType")
            } catch (e: Exception) {
                throw RuntimeException("Failed to read image features", e)
            }

            savedFeatures = surfImage.features
        }

        return Pair(surfImage, savedFeatures)
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

    private fun getLabelledPixelIndices(labelledFiles: List<File>, unlabelledFiles: List<File>, progressListener: TrainingProgressListener?): List<IntArray> {
        var progressListener = progressListener
        if (progressListener == null)
            progressListener = TrainingProgressListener.dummy


        return labelledFiles.indices.map { imageIndex ->
            progressListener.showStatus(imageIndex + 1, labelledFiles.size,
                    "Scanning image labels ${imageIndex + 1}/${labelledFiles.size}")

            val unlabelledImagePixels = getLabelImagePixels(unlabelledFiles[imageIndex])
            val labelImagePixels = getLabelImagePixels(labelledFiles[imageIndex])

            if (unlabelledImagePixels.size != labelImagePixels.size) {
                progressListener.logError("Un-annotated and annotated images '${unlabelledFiles[imageIndex].name}'" +
                        " differ in size")
            }

            val indices = IntArray(unlabelledImagePixels.size)
            var numLabels = 0
            for (i in 0 until unlabelledImagePixels.size) {
                if (labelImagePixels[i] != unlabelledImagePixels[i])
                    indices[numLabels++] = i
            }

            indices.copyOf(numLabels)
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

        val totalLabelledPixels = labelledPixels.map { it.size }.fold(0) { acc, i -> acc + i }

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
}) { acc: List<Training.FeatureImage<out Any>>, cur: List<Training.FeatureImage<Any>> ->
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