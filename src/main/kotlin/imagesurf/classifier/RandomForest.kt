/*
 *     This file is part of ImageSURF.
 *
 *     ImageSURF is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ImageSURF is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ImageSURF.  If not, see <http://www.gnu.org/licenses/>.
 */

package imagesurf.classifier

import imagesurf.feature.FeatureReader
import imagesurf.util.*
import util.UtilityJava

import java.io.Serializable
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream
import kotlin.math.floor
import kotlin.math.min

class RandomForest private constructor(
        val minInstances: Int,
        val numAttributes: Int,
        randomSeed: Int,
        val maxDepth: Int,
        val numTrees: Int,
        val numClasses: Int,
        val bagSizePercent: Double,
        numThreads: Int
) : Serializable, Classifier, ProgressNotifier by BasicProgressNotifier() {

    private val random: Random = Random(randomSeed.toLong())
    private val trees: Array<RandomTree?> = arrayOfNulls(numTrees)

    var numThreads: Int = numThreads
        get() = if (field <= 0) Runtime.getRuntime().availableProcessors() else field

    class Builder {
        private var minNum = 1
        private var numAttributes = 0
        private var randomSeed = 1
        private var maxDepth = 0
        private var numTrees = NOT_SET
        private var bagSizePercent = 100.0
        private var numThreads = NOT_SET

        private var data: FeatureReader? = null
        private var instanceIndices: IntArray? = null

        private val progressListeners = LinkedList<ProgressListener>()

        fun withMinNum(minNum: Int): Builder {
            this.minNum = minNum
            return this
        }

        fun withNumAttributes(numAttributes: Int): Builder {
            this.numAttributes = numAttributes
            return this
        }

        fun withRandomSeed(randomSeed: Int): Builder {
            this.randomSeed = randomSeed
            return this
        }

        fun withMaxDepth(maxDepth: Int): Builder {
            this.maxDepth = maxDepth
            return this
        }

        fun withNumTrees(numTrees: Int): Builder {
            this.numTrees = numTrees
            return this
        }

        fun withBagSize(percent: Int): Builder {
            this.bagSizePercent = percent.toDouble()
            return this
        }

        fun onNumThreads(numThreads: Int): Builder {
            this.numThreads = numThreads
            return this
        }

        @JvmOverloads
        fun withData(data: FeatureReader, instanceIndices: IntArray = IntStream.range(0, data.numInstances).toArray()): Builder {
            this.data = data
            this.instanceIndices = instanceIndices
            return this
        }

        fun withProgressListener(progressListener: ProgressListener): Builder {
            this.progressListeners.add(progressListener)
            return this
        }

        fun build(): RandomForest {

            if (numTrees == NOT_SET)
                throw IllegalArgumentException("Num trees must be set before building")

            if (data == null)
                throw IllegalArgumentException("Data must be set before building")

            return RandomForest(
                    minInstances = minNum,
                    numAttributes = when {
                        numAttributes >= data!!.numFeatures -> data!!.numFeatures
                        numAttributes < 1 -> UtilityJava.log2((data!!.numFeatures - 1).toDouble()).toInt() + 1
                        else -> numAttributes
                    },
                    randomSeed = randomSeed,
                    maxDepth = maxDepth,
                    numTrees = numTrees,
                    numClasses = data!!.numClasses,
                    bagSizePercent = bagSizePercent,
                    numThreads = numThreads
            )
                .also {
                    it.addProgressListeners(progressListeners)
                    it.buildClassifier(data!!, instanceIndices!!)
                    it.removeProgressListeners(progressListeners)
                }

        }

        companion object {
            private const val NOT_SET = Integer.MIN_VALUE
        }
    }

    private fun buildClassifier(data: FeatureReader, instanceIndices: IntArray) {

        val bagSize = floor(instanceIndices.size * (bagSizePercent / 100)).toInt()

        val executorPool = Executors.newFixedThreadPool(numThreads)

        val futures = ArrayList<Future<*>>()
        val treesBuilt = AtomicInteger(0)

        for (i in 0 until numTrees) {
            trees[i] = RandomTree(this)
            val currentClassifier = trees[i]

            val trainingSetRandomSeed = random.nextLong()
            val treeRandomSeed = random.nextLong()

            futures.add(executorPool.submit {
                val startTime = System.currentTimeMillis()
                val trainingSet = getTrainingSet(bagSize, trainingSetRandomSeed, instanceIndices)

                try {
                    currentClassifier!!.buildTree(data, trainingSet, treeRandomSeed)
                    onProgress(treesBuilt.getAndAdd(1), numTrees, "Built tree " + i + " in " + (System.currentTimeMillis() - startTime) + "ms")
                } catch (e: Exception) {
                    throw RuntimeException("Failed to build tree " + i + " in " + (System.currentTimeMillis() - startTime) + "ms.", e)
                }
            })
        }

        try {
            for (future in futures) {
                future.get()
                if (future.isCancelled)
                    throw RuntimeException("Concurrent processing failed.")
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            e.printStackTrace()
            throw RuntimeException(e)
        } finally {
            executorPool.shutdown()
        }

    }

    private fun getTrainingSet(bagSize: Int, randomSeed: Long, instanceIndices: IntArray): IntArray =
        Random(randomSeed).let{
            generateSequence { it.nextInt(instanceIndices.size) }
        }.take(bagSize).toList().toIntArray()


    override fun distributionForInstance(data: FeatureReader, instanceIndex: Int): DoubleArray {
        val sums = DoubleArray(this@RandomForest.numClasses)

        for (i in 0 until this@RandomForest.numTrees) {
            val newProbs = trees[i]!!.distributionForInstance(data, instanceIndex)
            if (null != newProbs)
                for (j in newProbs.indices)
                    sums[j] += newProbs[j]
        }
        return if (UtilityJava.eq(UtilityJava.sum(sums), 0.0)) {
            sums
        } else {
            UtilityJava.normalize(sums)
            sums
        }
    }

    @Throws(InterruptedException::class)
    override fun distributionForInstances(data: FeatureReader): Array<DoubleArray> {
        val numInstances = data.numInstances
        val distributions = arrayOfNulls<DoubleArray>(numInstances)

        val progressPoint = numInstances / 100
        val progress = AtomicInteger(0)

        //TODO remove dependency on ImageSurfEnvironment and pass executors around
        val e = ImageSurfEnvironment.getSegmentationExecutor()

        val batchSize = numInstances / numThreads

        e.submit { (0 until numThreads).toList()
            .stream()
            .parallel()
            .forEach { threadIndex ->
                IntRange(batchSize * threadIndex, min(batchSize * threadIndex + batchSize, numInstances - 1)).forEach { index ->
                    if (index % progressPoint == 0 || (index + 1) % batchSize == 0) {
                        val currentProgress = progress.getAndIncrement()
                        onProgress(currentProgress, 100, "Segmented $currentProgress%")
                    }
                    val result: DoubleArray
                    val sums = DoubleArray(this@RandomForest.numClasses)

                    for (i in 0 until this@RandomForest.numTrees) {
                        val newProbs = trees[i]!!.distributionForInstance(data, index)
                        if (null != newProbs)
                            for (j in newProbs.indices)
                                sums[j] += newProbs[j]
                    }
                    if (UtilityJava.eq(UtilityJava.sum(sums), 0.0)) {
                        result = sums
                    } else {
                        UtilityJava.normalize(sums)
                        result = sums
                    }
                    distributions[index] = result

                }
            }
        }.get()

        return distributions.filterNotNull().toTypedArray()
    }

    @Throws(InterruptedException::class)
    override fun classForInstances(data: FeatureReader, instanceIndices: IntArray): IntArray {
        val numInstances = instanceIndices.size
        val classes = IntArray(numInstances)
        val progressPoint = numInstances / 100

        //TODO make sure progress is working properly
        val progress = AtomicInteger(0)

        //TODO remove dependency on ImageSurfEnvironment and pass executors around
        val e = ImageSurfEnvironment.getSegmentationExecutor()

        val batchSize = numInstances / numThreads

        e.submit { (0 until numThreads).toList()
            .stream()
            .parallel()
            .forEach { threadIndex ->
                IntRange(batchSize * threadIndex, min(batchSize * threadIndex + batchSize, numInstances - 1)).forEach { index ->
                    if (index % progressPoint == 0 || (index + 1) % batchSize == 0) {
                        val currentProgress = progress.getAndIncrement()
                        onProgress(currentProgress, 100, "Segmented $currentProgress%")
                    }

                    val sums = DoubleArray(this@RandomForest.numClasses)

                    for (i in 0 until this@RandomForest.numTrees) {
                        val newProbs = trees[i]!!.distributionForInstance(data, instanceIndices[index])
                        if (null != newProbs)
                            for (j in newProbs.indices)
                                sums[j] += newProbs[j]
                    }

                    var maxClass = 0

                    for (c in 1 until sums.size)
                        if (sums[c] > sums[maxClass])
                            maxClass = c

                    classes[index] = maxClass
                }
            }
        }.get()

        return classes
    }

    companion object {
        internal const val serialVersionUID = 43L
    }
}

