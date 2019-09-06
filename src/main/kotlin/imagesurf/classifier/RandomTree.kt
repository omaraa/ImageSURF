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

class RandomTree(val randomForest: RandomForest) : Serializable {

    /**
     * The subtrees appended to this tree.
     */
    protected var children: Array<RandomTree?>? = null

    /**
     * The attribute to split on.
     */
    protected var splitAttribute = -1

    /**
     * The split point.
     */
    protected var splitPoint = java.lang.Double.NaN

    /**
     * Class probabilities from the training data in the nominal case. Holds the
     * mean in the numeric case.
     */
    protected var classDistribution: DoubleArray? = null

    protected var normalisedClassDistribution: DoubleArray? = null


    /**
     * Computes class distribution of an instance using the decision tree.
     *
     * @return the computed class distribution
     */
    fun distributionForInstance(data: FeatureReader, instanceIndex: Int): DoubleArray? {

        if (splitAttribute > -1) {
            val splitDirection = if (data.getValue(instanceIndex, splitAttribute) < splitPoint)
                SPLIT_LEFT
            else
                SPLIT_RIGHT

            val distribution = children!![splitDirection]!!.distributionForInstance(data, instanceIndex)

            if (distribution != null)
                return distribution
        }

        // Node is a leaf or successor is empty
        return normalisedClassDistribution
    }

    fun buildTree(data: FeatureReader, instanceIndices: IntArray, randomSeed: Long) {
        val rand = Random(randomSeed)

        // Create the attribute indices window
        val attIndicesWindow = (0 until data.numFeatures)
                .shuffled()
                .filter { it != data.classIndex }
                .take(randomForest.numAttributes)
                .toIntArray()

        // Compute initial class counts
        val classProbs = DoubleArray(randomForest.numClasses)
        for (i in instanceIndices) {
            classProbs[data.getClassValue(i)] += data.getWeight(i)
        }

        buildTree(data, instanceIndices, classProbs, attIndicesWindow, rand, 0)
    }

    /**
     * Recursively builds a tree.
     *
     * @param data             the data to work with
     * @param classProbs       the class distribution
     * @param attIndicesWindow the attribute window to choose attributes from
     * @param random           random number generator for choosing random attributes
     * @param depth            the current depth
     * @throws Exception if generation fails
     */
    protected fun buildTree(data: FeatureReader, instanceIndices: IntArray, classProbs: DoubleArray,
                            attIndicesWindow: IntArray, random: Random, depth: Int) {

        // Make leaf if there are no training instances
        if (instanceIndices.size == 0) {
            splitAttribute = -1
            classDistribution = null
            normalisedClassDistribution = null
            return
        }

        // Check if node doesn't contain enough instances, is pure, or maximum depth reached
        val totalWeight = Utility.sum(classProbs)

        if (totalWeight < 2 * randomForest.minInstances
                || Utility.eq(classProbs[Utility.maxIndex(classProbs)], Utility.sum(classProbs))
                || randomForest.maxDepth > 0 && depth >= randomForest.maxDepth) {
            // Make leaf
            splitAttribute = -1
            classDistribution = classProbs.clone()
            normalisedClassDistribution = classProbs.clone()
            Utility.normalize(normalisedClassDistribution!!)

            return
        }

        // Compute class distributions and value of splitting
        // criterion for each attribute
        var `val` = -java.lang.Double.MAX_VALUE
        var split = -java.lang.Double.MAX_VALUE
        var bestDists: Array<DoubleArray>? = null
        var bestIndex = 0

        // Handles to get arrays out of distribution method
        val dists = Array(1) { Array(0) { DoubleArray(0) } }

        // Investigate K random attributes
        var attIndex = 0
        var windowSize = attIndicesWindow.size
        var k = randomForest.numAttributes
        var gainFound = false
        while (windowSize > 0 && (k-- > 0 || !gainFound)) {

            val chosenIndex = random.nextInt(windowSize)
            attIndex = attIndicesWindow[chosenIndex]

            // shift chosen attIndex out of window
            attIndicesWindow[chosenIndex] = attIndicesWindow[windowSize - 1]
            attIndicesWindow[windowSize - 1] = attIndex
            windowSize--

            val currSplit = distribution(dists, attIndex, data, instanceIndices)

            val currVal = gain(dists[0], priorVal(dists[0]))

            if (Utility.gr(currVal, 0.0)) {
                gainFound = true
            }

            if (currVal > `val`) {
                `val` = currVal
                bestIndex = attIndex
                split = currSplit
                bestDists = dists[0]
            }
        }

        // Find best attribute
        splitAttribute = bestIndex

        // Any useful split found?
        if (Utility.gr(`val`, 0.0)) {

            // Build subtrees
            splitPoint = split
            val subsets = splitData(data, instanceIndices)
            children = arrayOfNulls(bestDists!!.size)

            for (i in bestDists.indices) {
                children!![i] = RandomTree(randomForest)
                children!![i] = RandomTree(randomForest)
                children!![i]!!.buildTree(data, subsets[i], bestDists[i], attIndicesWindow,
                        random, depth + 1)
            }

            // If all successors are non-empty, we don't need to store the class
            // distribution
            var emptySuccessor = false
            for (i in subsets.indices) {
                if (children!![i]!!.classDistribution == null) {
                    emptySuccessor = true
                    break
                }
            }
            if (emptySuccessor) {
                classDistribution = classProbs.clone()
            }
        } else {

            // Make leaf
            splitAttribute = -1
            classDistribution = classProbs.clone()
        }

        if (classDistribution != null) {
            normalisedClassDistribution = classProbs.clone()
            Utility.normalize(normalisedClassDistribution!!)
        }
    }

    /**
     * Splits instances into subsets based on the given split.
     *
     * @param data the data to work with
     * @return the subsets of instances
     * @throws Exception if something goes wrong
     */
    protected fun splitData(data: FeatureReader, instanceIndices: IntArray): Array<IntArray> {
        val subsetCounts = IntArray(N_SPLIT_DIRECTIONS)
        val subsets = Array(N_SPLIT_DIRECTIONS) { IntArray(instanceIndices.size) }

        for (i in instanceIndices) {
            val splitDirection = if (data.getValue(i, splitAttribute) < splitPoint) SPLIT_LEFT else SPLIT_RIGHT
            subsets[splitDirection][subsetCounts[splitDirection]++] = i
        }

        for (splitDirection in SPLIT_DIRECTIONS)
            subsets[splitDirection] = Arrays.copyOf(subsets[splitDirection], subsetCounts[splitDirection])

        return subsets
    }

    /**
     * Computes class distribution for an attribute.
     *
     * @param dists
     * @param att   the attribute index
     * @param data  the data to work with
     * @throws Exception if something goes wrong
     */
    protected fun distribution(dists: Array<Array<DoubleArray>>,
                               att: Int, data: FeatureReader, instanceIndices: IntArray): Double {

        var splitPoint = java.lang.Double.NaN
        var dist: Array<DoubleArray> = Array(2) { DoubleArray(randomForest.numClasses) }.apply {


            // For numeric attributes
            val currDist = Array(2) { DoubleArray(randomForest.numClasses) }

            val sortedIndices = data.getSortedIndices(att, instanceIndices)

            // Move all instances into second subset
            for (j in sortedIndices) {
                currDist[1][data.getClassValue(j)] += data.getWeight(j)
            }

            // Value before splitting
            val priorVal = priorVal(currDist)

            // Save initial distribution
            for (j in currDist.indices) {
                System.arraycopy(currDist[j], 0, this[j], 0, this[j].size)
            }

            // Try all possible split points
            var currSplit = data.getValue(sortedIndices[0], att)
            var currVal: Double
            var bestVal = -java.lang.Double.MAX_VALUE
            for (i in sortedIndices) {
                val attVal = data.getValue(i, att)

                // Can we place a sensible split point here?
                if (attVal > currSplit) {

                    // Compute gain for split point
                    currVal = gain(currDist, priorVal)

                    // Is the current split point the best point so far?
                    if (currVal > bestVal) {

                        // Store value of current point
                        bestVal = currVal

                        // Save split point
                        splitPoint = (attVal + currSplit) / 2.0

                        // Check for numeric precision problems
                        if (splitPoint <= currSplit) {
                            splitPoint = attVal
                        }

                        // Save distribution
                        for (j in currDist.indices) {
                            System.arraycopy(currDist[j], 0, this!![j], 0, this!![j].size)
                        }
                    }

                    // Update value
                    currSplit = attVal
                }

                // Shift over the weight
                val classVal = data.getClassValue(i)
                currDist[0][classVal] += data.getWeight(i)
                currDist[1][classVal] -= data.getWeight(i)
            }
        }

        // Return distribution and split point
        dists[0] = dist
        return splitPoint
    }

    /**
     * Computes value of splitting criterion before split.
     *
     * @param dist the distributions
     * @return the splitting criterion
     */
    protected fun priorVal(dist: Array<DoubleArray>): Double {

        return Utility.entropyOverColumns(dist)
    }

    /**
     * Computes value of splitting criterion after split.
     *
     * @param dist     the distributions
     * @param priorVal the splitting criterion
     * @return the gain after the split
     */
    protected fun gain(dist: Array<DoubleArray>, priorVal: Double): Double {

        return priorVal - Utility.entropyConditionedOnRows(dist)
    }

    companion object {
        internal const val serialVersionUID = 42L

        private val SPLIT_LEFT = 0
        private val SPLIT_RIGHT = 1
        private val N_SPLIT_DIRECTIONS = 2
        private val SPLIT_DIRECTIONS = intArrayOf(SPLIT_LEFT, SPLIT_RIGHT)
    }
}