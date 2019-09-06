package imagesurf.util

import imagesurf.classifier.ImageSurfClassifier
import imagesurf.classifier.RandomForest
import imagesurf.feature.ImageFeatures
import ij.ImagePlus
import ij.ImageStack
import ij.Prefs
import imagesurf.feature.PixelType
import imagesurf.feature.calculator.FeatureCalculator
import org.scijava.app.StatusService

import javax.imageio.ImageIO
import java.awt.*
import java.io.*
import java.util.Random
import java.util.concurrent.ExecutionException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Utility {
    /** Cache of integer logs
     * FROM package weka.core.ContingencyTables.java;
     */
    private val MAX_INT_FOR_CACHE_PLUS_ONE = 10000.0
    private val INT_N_LOG_N_CACHE = DoubleArray(MAX_INT_FOR_CACHE_PLUS_ONE.toInt())

    /**
     * The natural logarithm of 2.
     */
    var log2 = Math.log(2.0)
    /**
     * The small deviation allowed in double comparisons.
     */
    var SMALL = 1e-6

    init {
        var i = 1
        while (i < MAX_INT_FOR_CACHE_PLUS_ONE) {
            val d = i.toDouble()
            INT_N_LOG_N_CACHE[i] = d * Math.log(d)
            i++
        }
    }

    fun isGrayScale(imagePlus: ImagePlus): Boolean {
        if (imagePlus.nChannels > 1)
            return false

        val pixels = imagePlus.bufferedImage.getRGB(0, 0, imagePlus.width, imagePlus.height, null, 0, imagePlus.width)

        for (pixel in pixels)
            if (pixel and 0xff != pixel and 0xff00 shr 8 || pixel and 0xff != pixel and 0xff0000 shr 16)
                return false

        return true
    }

    @Throws(IOException::class)
    fun serializeObject(`object`: Any, outputFile: File, compress: Boolean) {
        if (!outputFile.parentFile.exists())
            outputFile.parentFile.mkdirs()


        if (outputFile.exists())
            outputFile.delete()

        if (compress) {
            val fos = FileOutputStream(outputFile)
            val zos = GZIPOutputStream(fos)
            val ous = ObjectOutputStream(zos)

            ous.writeObject(`object`)
            zos.finish()
            fos.flush()

            zos.close()
            fos.close()
            ous.close()
        } else {
            val file = FileOutputStream(outputFile)
            val buffer = BufferedOutputStream(file)
            val output = ObjectOutputStream(buffer)

            output.writeObject(`object`)

            output.flush()
            output.close()
            buffer.close()
            file.close()
        }
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    fun deserializeObject(objectFile: File, compressed: Boolean): Any {
        if (compressed) {
            val file = FileInputStream(objectFile)
            val gzipInputStream = GZIPInputStream(file)
            val input = ObjectInputStream(gzipInputStream)

            val result = input.readObject()

            input.close()
            gzipInputStream.close()
            file.close()
            return result
        } else {
            val file = FileInputStream(objectFile)
            val buffer = BufferedInputStream(file)
            val input = ObjectInputStream(buffer)

            val result = input.readObject()

            buffer.close()
            file.close()
            input.close()
            return result
        }
    }

    @Throws(IOException::class)
    fun getImageDimensions(imagePath: File): Dimension {

        try {
            ImageIO.createImageInputStream(imagePath).use { input ->
                val reader = ImageIO.getImageReaders(input).next() // TODO: Handle no reader
                try {
                    reader.input = input
                    return Dimension(reader.getWidth(0), reader.getHeight(0))
                } finally {
                    reader.dispose()
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to read dimensions", e)
        }

    }

    fun sum(labelledPixels: IntArray): Int {
        var sum = 0
        for (i in labelledPixels)
            sum += i

        return sum
    }

    fun shuffleArray(ar: IntArray, random: Random) {
        for (i in ar.size - 1 downTo 1) {
            val index = random.nextInt(i + 1)

            // Simple swap
            val a = ar[index]
            ar[index] = ar[i]
            ar[i] = a
        }
    }

    //todo: remove ImagePlus image parameter and use features members (e.g.,  width, height) instead.
    @Throws(ExecutionException::class, InterruptedException::class)
    fun segmentImage(imageSurfClassifier: ImageSurfClassifier, features: ImageFeatures, image: ImagePlus, statusService: StatusService): ImageStack {
        if (imageSurfClassifier.pixelType != features.pixelType)
            throw RuntimeException("Classifier pixel type (" +
                    imageSurfClassifier.pixelType + ") does not match image pixel type (" + features.pixelType + ")")

        if (imageSurfClassifier.numChannels != features.numChannels)
            throw RuntimeException("Classifier trained for " + imageSurfClassifier.numChannels + " channels. Image has " + features.numChannels + " - cannot segment.")


        val randomForest = imageSurfClassifier.randomForest
        randomForest.numThreads = Prefs.getThreads()


        val outputStack = ImageStack(image.width, image.height)
        val numPixels = image.width * image.height

        val numClasses = randomForest.numClasses
        val classColors = ByteArray(numClasses)
        run {
            classColors[0] = 0
            classColors[classColors.size - 1] = 0xff.toByte()

            if (numClasses > 2) {
                val interval = 0xff / (numClasses - 1)
                for (i in 1 until classColors.size - 1)
                    classColors[i] = (interval * i).toByte()
            }
        }


        val currentSlice = 1
        for (z in 0 until image.nSlices)
            for (t in 0 until image.nFrames) {

                val imageFeaturesProgressListener = ImageFeatures.ProgressListener { current, max, message ->
                    statusService.showStatus(current, max,
                            "Calculating features for plane $currentSlice/${image.nChannels * image.nSlices * image.nFrames}")
                }

                features.addProgressListener(imageFeaturesProgressListener)
                if (features.calculateFeatures(z, t, imageSurfClassifier.features))
                    features.removeProgressListener(imageFeaturesProgressListener)

                val featureReader = features.getReader(z, t, imageSurfClassifier.features)

                val randomForestProgressListener = object : RandomForest.ProgressListener {
                    override fun onProgress(current: Int, max: Int, message: String) {
                        statusService.showStatus(current, max, "Segmenting plane " + currentSlice + "/" +
                                image.nChannels * image.nSlices * image.nFrames)
                    }
                }

                randomForest.addProgressListener(randomForestProgressListener)
                val classes = randomForest.classForInstances(featureReader)
                randomForest.removeProgressListener(randomForestProgressListener)
                val segmentationPixels = ByteArray(numPixels)

                for (i in 0 until numPixels) {
                    segmentationPixels[i] = classColors[classes[i]]
                }

                outputStack.addSlice("", segmentationPixels)
            }
        return outputStack
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    fun calculateImageFeatures(featureCalculators: Array<FeatureCalculator>, features: ImageFeatures, statusService: StatusService, pixelType: PixelType): ImageStack {

        val outputStack = ImageStack(features.width, features.height)

        var currentSlice = 1
        for (z in 0 until features.numSlices)
            for (t in 0 until features.numFrames) {
                val finalCurrentSlice = currentSlice
                val imageFeaturesProgressListener = object : ImageFeatures.ProgressListener {
                    override fun onProgress(current: Int, max: Int, message: String) {
                        statusService.showStatus(current, max, "Calculating features for plane " + finalCurrentSlice + "/" +
                                features.numSlices * features.numFrames)
                    }
                }

                features.addProgressListener(imageFeaturesProgressListener)
                if (features.calculateFeatures(z, t, featureCalculators))
                    features.removeProgressListener(imageFeaturesProgressListener)

                for (f in featureCalculators) {
                    when (pixelType) {

                        PixelType.GRAY_8_BIT -> outputStack.addSlice(f.descriptionWithTags, (features.getFeaturePixels(z, t, f) as Array<ByteArray>)[0])
                        PixelType.GRAY_16_BIT -> outputStack.addSlice(f.descriptionWithTags, (features.getFeaturePixels(z, t, f) as Array<ShortArray>)[0])
                        else -> throw RuntimeException("Unsupported pixel type: $pixelType")
                    }

                }

                currentSlice++
            }

        return outputStack
    }

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Computes conditional entropy of the columns given
     * the rows.
     *
     * @param matrix the contingency table
     * @return the conditional entropy of the columns given the rows
     */
    fun entropyConditionedOnRows(matrix: Array<DoubleArray>): Double {

        var returnValue = 0.0
        var sumForRow: Double
        var total = 0.0

        for (i in matrix.indices) {
            sumForRow = 0.0
            for (j in 0 until matrix[0].size) {
                returnValue = returnValue + lnFunc(matrix[i][j])
                sumForRow += matrix[i][j]
            }
            returnValue = returnValue - lnFunc(sumForRow)
            total += sumForRow
        }
        return if (eq(total, 0.0)) {
            0.0
        } else -returnValue / (total * log2)
    }

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Computes the columns' entropy for the given contingency table.
     *
     * @param matrix the contingency table
     * @return the columns' entropy
     */
    fun entropyOverColumns(matrix: Array<DoubleArray>): Double {

        var returnValue = 0.0
        var sumForColumn: Double
        var total = 0.0

        for (j in 0 until matrix[0].size) {
            sumForColumn = 0.0
            for (i in matrix.indices) {
                sumForColumn += matrix[i][j]
            }
            returnValue = returnValue - lnFunc(sumForColumn)
            total += sumForColumn
        }
        return if (eq(total, 0.0)) {
            0.0
        } else (returnValue + lnFunc(total)) / (total * log2)
    }

    /**
     * FROM package weka.core.ContingencyTables.java;
     * Help method for computing entropy.
     */
    fun lnFunc(num: Double): Double {

        if (num <= 0) {
            return 0.0
        } else {

            // Use cache if we have a sufficiently small integer
            if (num < MAX_INT_FOR_CACHE_PLUS_ONE) {
                val n = num.toInt()
                if (n.toDouble() == num) {
                    return INT_N_LOG_N_CACHE[n]
                }
            }
            return num * Math.log(num)
        }
    }

    /**
     * FROM package weka.core.Utils.java;
     * Tests if a is equal to b.
     *
     * @param a a double
     * @param b a double
     */
    /* @pure@ */ fun eq(a: Double, b: Double): Boolean {

        return a == b || a - b < SMALL && b - a < SMALL
    }

    /**
     * FROM package weka.core.Utils.java;
     * Returns the logarithm of a for base 2.
     *
     * @param a a double
     * @return the logarithm for base 2
     */
    /* @pure@ */ fun log2(a: Double): Double {

        return Math.log(a) / log2
    }

    /**
     * FROM package weka.core.Utils.java;
     * Computes the sum of the elements of an array of doubles.
     *
     * @param doubles the array of double
     * @return the sum of the elements
     */
    /* @pure@ */ fun sum(doubles: DoubleArray): Double {

        var sum = 0.0

        for (d in doubles) {
            sum += d
        }
        return sum
    }

    /**
     * FROM package weka.core.Utils.java;
     * Normalizes the doubles in the array by their sum.
     *
     * @param doubles the array of double
     * @exception IllegalArgumentException if sum is Zero or NaN
     */
    fun normalize(doubles: DoubleArray) {

        var sum = 0.0
        for (d in doubles) {
            sum += d
        }
        normalize(doubles, sum)
    }

    /**
     * FROM package weka.core.Utils.java;
     * Normalizes the doubles in the array using the given value.
     *
     * @param doubles the array of double
     * @param sum the value by which the doubles are to be normalized
     * @exception IllegalArgumentException if sum is zero or NaN
     */
    fun normalize(doubles: DoubleArray, sum: Double) {

        if (java.lang.Double.isNaN(sum)) {
            throw IllegalArgumentException("Can't normalize array. Sum is NaN.")
        }
        if (sum == 0.0) {
            // Maybe this should just be a return.
            throw IllegalArgumentException("Can't normalize array. Sum is zero.")
        }
        for (i in doubles.indices) {
            doubles[i] /= sum
        }
    }

    /**
     * FROM package weka.core.Utils.java;
     * Returns index of maximum element in a given array of doubles. First maximum
     * is returned.
     *
     * @param doubles the array of doubles
     * @return the index of the maximum element
     */
    /* @pure@ */ fun maxIndex(doubles: DoubleArray): Int {

        var maximum = 0.0
        var maxIndex = 0

        for (i in doubles.indices) {
            if (i == 0 || doubles[i] > maximum) {
                maxIndex = i
                maximum = doubles[i]
            }
        }

        return maxIndex
    }

    /**
     * FROM package weka.core.Utils.java;
     * Tests if a is greater than b.
     *
     * @param a a double
     * @param b a double
     */
    /* @pure@ */ fun gr(a: Double, b: Double): Boolean {

        return a - b > SMALL
    }

    fun getPixelType(imagePlus: ImagePlus): PixelType {
        when (imagePlus.type) {
            ImagePlus.COLOR_256, ImagePlus.COLOR_RGB, ImagePlus.GRAY8 -> return PixelType.GRAY_8_BIT

            ImagePlus.GRAY16 -> return PixelType.GRAY_16_BIT

            ImagePlus.GRAY32 -> throw IllegalArgumentException("32-bit grayscale images are not yet supported.")

            else -> throw IllegalArgumentException("Image type not supported.")
        }
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
}
