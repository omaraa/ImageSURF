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
import imagesurf.util.Utility

import java.io.Serializable
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.IntStream

class RandomForest private constructor(
        val minInstances: Int,
        val numAttributes: Int,
        randomSeed: Int,
        val maxDepth: Int,
        val numTrees: Int,
        val numClasses: Int,
        val bagSizePercent: Double,
        numThreads: Int
) : Serializable {

    private val progressListeners: HashSet<ProgressListener> = HashSet()
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
                        numAttributes < 1 -> Utility.log2((data!!.numFeatures - 1).toDouble()).toInt() + 1
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

    @Throws(InterruptedException::class)
    @JvmOverloads
    fun calculateFeatureImportance(reader: FeatureReader, instanceIndices: IntArray = IntStream.range(0, reader.numInstances).toArray()): DoubleArray =
            (0 until reader.numFeatures).map { attributeIndex ->
                if (attributeIndex == reader.classIndex) java.lang.Double.NaN
                else ScrambledFeatureReader(reader, attributeIndex, random.nextLong())
                        .let{ classForInstances(reader, instanceIndices) }
                        .mapIndexed {index, predictedClass -> predictedClass == reader.getClassValue(instanceIndices[index])}
                        .filterNot { it }
                        .size
                        .toDouble()
                        .div(instanceIndices.size)
            }.toDoubleArray()

    interface ProgressListener {
        fun onProgress(current: Int, max: Int, message: String)
    }

    fun addProgressListener(listener: ProgressListener) =
            progressListeners.add(listener)

    fun addProgressListeners(listeners: Collection<ProgressListener>) =
            progressListeners.addAll(listeners)

    fun removeProgressListener(listener: ProgressListener) =
            progressListeners.remove(listener)

    fun removeProgressListeners(listeners: Collection<ProgressListener>) =
            progressListeners.removeAll(listeners)

    private fun onProgress(current: Int, max: Int, message: String) {
        for (p in progressListeners)
            p.onProgress(current, max, message)
    }

    private fun buildClassifier(data: FeatureReader, instanceIndices: IntArray) {

        val bagSize = Math.floor(instanceIndices.size * (bagSizePercent / 100)).toInt()

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

    private fun getClassInstances(reader: FeatureReader): Array<IntArray> {
        val numClasses = this@RandomForest.numClasses

        val classes = reader.classes
        val classInstances = Array(numClasses) { IntArray(classes.size) }
        val classCounts = IntArray(numClasses)


        for (i in classes.indices) {
            val classValue = reader.getClassValue(i)
            classInstances[classValue][classCounts[classValue]++] = i
        }

        for (i in 0 until numClasses)
            classInstances[i] = Arrays.copyOf(classInstances[i], classCounts[i])

        return classInstances
    }

    private fun getTrainingSet(bagSize: Int, randomSeed: Long, instanceIndices: IntArray): IntArray =
        Random(randomSeed).let{
            generateSequence { it.nextInt(instanceIndices.size) }
        }.take(bagSize).toList().toIntArray()


    fun distributionForInstance(data: FeatureReader, instanceIndex: Int): DoubleArray {
        val sums = DoubleArray(this@RandomForest.numClasses)

        for (i in 0 until this@RandomForest.numTrees) {
            val newProbs = trees[i]!!.distributionForInstance(data, instanceIndex)
            if (null != newProbs)
                for (j in newProbs.indices)
                    sums[j] += newProbs[j]
        }
        return if (Utility.eq(Utility.sum(sums), 0.0)) {
            sums
        } else {
            Utility.normalize(sums)
            sums
        }
    }

    @Throws(InterruptedException::class)
    fun distributionForInstances(data: FeatureReader): Array<DoubleArray> {
        val numInstances = data.numInstances
        val distributions = arrayOfNulls<DoubleArray>(numInstances)

        val current = AtomicInteger(0)
        val e = Executors.newFixedThreadPool(numThreads)

        for (threadIndex in 0 until numThreads)
            e.submit {
                var index = current.getAndIncrement()
                while (index < numInstances) {
                    val result: DoubleArray
                    val sums = DoubleArray(this@RandomForest.numClasses)

                    for (i in 0 until this@RandomForest.numTrees) {
                        val newProbs = trees[i]!!.distributionForInstance(data, index)
                        if (null != newProbs)
                            for (j in newProbs.indices)
                                sums[j] += newProbs[j]
                    }
                    if (Utility.eq(Utility.sum(sums), 0.0)) {
                        result = sums
                    } else {
                        Utility.normalize(sums)
                        result = sums
                    }
                    distributions[index] = result

                    index = current.getAndIncrement()

                }
            }

        e.shutdown()
        e.awaitTermination(Integer.MAX_VALUE.toLong(), TimeUnit.DAYS)

        return distributions.filterNotNull().toTypedArray()
    }

    @Throws(InterruptedException::class)
    @JvmOverloads
    fun classForInstances(data: FeatureReader, instanceIndices: IntArray = IntStream.range(0, data.numInstances).toArray()): IntArray {
        val numInstances = instanceIndices.size
        val classes = IntArray(numInstances)
        val progressPoint = numInstances / 100

        val current = AtomicInteger(0)
        val e = Executors.newFixedThreadPool(numThreads)

        for (threadIndex in 0 until numThreads)
            e.submit {
                var index = current.getAndIncrement()
                while (index < numInstances) {
                    if (index % progressPoint == 0) {
                        onProgress(index / progressPoint, 100, "Segmented " + index / progressPoint + "%")
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

                    index = current.getAndIncrement()
                }
            }

        e.shutdown()
        e.awaitTermination(Integer.MAX_VALUE.toLong(), TimeUnit.DAYS)

        return classes
    }

    companion object {
        internal const val serialVersionUID = 43L
    }
}

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
