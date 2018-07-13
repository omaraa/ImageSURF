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

package imagesurf.classifier;

import imagesurf.feature.FeatureReader;
import imagesurf.util.Utility;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class RandomForest implements Serializable {
    static final long serialVersionUID = 43L;

    private RandomForest(int m_minNum, int numAttributes, int m_randomSeed,
                        int m_maxDepth, int numTrees, int numClasses, double bagSizePercent, int numThreads) {
        m_MinNum = m_minNum;
        this.numAttributes = numAttributes;
        this.m_randomSeed = m_randomSeed;
        maxDepth = m_maxDepth;
        this.numTrees = numTrees;
        this.numClasses = numClasses;
        this.bagSizePercent = bagSizePercent;
        this._numThreads = numThreads;

        trees = new RandomTree[numTrees];
        m_random = new Random(m_randomSeed);
    }

    public void setNumThreads(int threads) {
        this._numThreads = threads;
    }

    public static class Builder {
        private static final int NOT_SET = Integer.MIN_VALUE;

        private int minNum = 1;
        private int numAttributes = 0;
        private int randomSeed = 1;
        private int maxDepth = 0;
        private int numTrees = NOT_SET;
        private double bagSizePercent = 100;
        private int numThreads = NOT_SET;

        private FeatureReader data;
        private int[] instanceIndices;

        private final Collection<ProgressListener> progressListeners = new LinkedList<>();

        public Builder withMinNum(int minNum) {
            this.minNum = minNum;
            return this;
        }

        public Builder withNumAttributes(int numAttributes) {
            this.numAttributes = numAttributes;
            return this;
        }

        public Builder withRandomSeed(int randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder withMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder withNumTrees(int numTrees) {
            this.numTrees = numTrees;
            return this;
        }

        public Builder withBagSize(int percent) {
            this.bagSizePercent = percent;
            return this;
        }

        public Builder onNumThreads(int numThreads) {
            this.numThreads = numThreads;
            return this;
        }

        public Builder withData(FeatureReader data) {
            return withData(data, IntStream.range(0, data.getNumInstances()).toArray());
        }

        public Builder withData(FeatureReader data, int[] instanceIndices) {
            this.data = data;
            this.instanceIndices = instanceIndices;
            return this;
        }

        public Builder withProgressListener(ProgressListener progressListener){
            this.progressListeners.add(progressListener);
            return this;
        }

        public RandomForest build() {

            if (numTrees == NOT_SET)
                throw new IllegalArgumentException("Num trees must be set before building");

            if (data == null)
                throw new IllegalArgumentException("Data must be set before building");

            // Make sure K value is in range
            if (numAttributes >= data.getNumFeatures()) {
                numAttributes = data.getNumFeatures();
            }
            if (numAttributes < 1) {
                numAttributes = (int) Utility.log2(numAttributes - 1) + 1;
            }

            RandomForest randomForest = new RandomForest(minNum, numAttributes, randomSeed, maxDepth, numTrees, data.getNumClasses(), bagSizePercent, numThreads);
            for(ProgressListener p : progressListeners)
                randomForest.addProgressListener(p);

            randomForest.buildClassifier(data, instanceIndices);

            for(ProgressListener p : progressListeners)
                randomForest.removeprogressListener(p);

            return randomForest;
        }
    }

    /**
     * Minimum number of instances for leaf.
     */
    private final int m_MinNum;
    /**
     * The number of attributes considered for a split.
     */
    public final int numAttributes;

    /**
     * The random seed to use.
     */
    private final int m_randomSeed;
    /**
     * The maximum depth of the tree (0 = unlimited)
     */
    public final int maxDepth;

    private final RandomTree[] trees;

    private final Random m_random;
    public final int numTrees;
    public final int numClasses;
    private final double bagSizePercent;
    private int _numThreads;

    private int getNumThreads() {
        return (_numThreads <= 0) ? Runtime.getRuntime().availableProcessors() : _numThreads;
    }

    public double[] calculateFeatureImportance(final FeatureReader reader) throws InterruptedException {
        return calculateFeatureImportance(reader, IntStream.range(0, reader.getNumInstances()).toArray());
    }

    public double[] calculateFeatureImportance(final FeatureReader reader, final int[] instanceIndices) throws InterruptedException {
        double[] percentIncorrect = new double[reader.getNumFeatures()];

        for (int attributeIndex = 0; attributeIndex < reader.getNumFeatures(); attributeIndex++) {
            if (attributeIndex == reader.getClassIndex()) {
                percentIncorrect[attributeIndex] = Double.NaN;
                continue;
            }

            ScrambledFeatureReader scrambleReader = new ScrambledFeatureReader(reader, attributeIndex);

            int[] instanceClasses = classForInstances(scrambleReader, instanceIndices);
            int numIncorrect = 0;

            for (int i = 0; i < instanceIndices.length; i++)
                if (instanceClasses[i] != reader.getClassValue(i))
                    numIncorrect++;
//			else
//					System.err.println("Predicted: "+instanceClasses[i]+" actual: "+reader.getClassValue(i));

            percentIncorrect[attributeIndex] = ((double) numIncorrect) / instanceIndices.length;

        }

        return percentIncorrect;
    }

    private class ScrambledFeatureReader implements FeatureReader {
        final int scrambledIndex;
        final int[] scrambledIndices;
        final FeatureReader reader;

        private ScrambledFeatureReader(FeatureReader reader, int attributeIndex) {
            this.reader = reader;

            this.scrambledIndex = attributeIndex;

            this.scrambledIndices = IntStream.range(0, reader.getNumFeatures()).toArray();
            Utility.shuffleArray(scrambledIndices, m_random);
        }

        @Override
        public int getClassValue(int instanceIndex) {
            return reader.getClassValue(instanceIndex);
        }

        @Override
        public double getValue(int instanceIndex, int attributeIndex) {
            if (attributeIndex == scrambledIndex)
                return reader.getValue(scrambledIndices[instanceIndex], attributeIndex);

            return reader.getValue(instanceIndex, attributeIndex);
        }

        @Override
        public int getNumInstances() {
            return reader.getNumInstances();
        }

        @Override
        public int getNumFeatures() {
            return reader.getNumFeatures();
        }

        @Override
        public int getClassIndex() {
            return reader.getClassIndex();
        }

        @Override
        public int getNumClasses() {
            return reader.getNumClasses();
        }
    }

    public interface ProgressListener {
        void onProgress(int current, int max, String message);
    }

    private final Collection<ProgressListener> progressListeners = new HashSet<>();

    public boolean addProgressListener(ProgressListener progressListener) {
        return progressListeners.add(progressListener);
    }

    public boolean removeprogressListener(ProgressListener progressListener) {
        return progressListeners.remove(progressListener);
    }

    private void onProgress(int current, int max, String message) {
        for (ProgressListener p : progressListeners)
            p.onProgress(current, max, message);
    }

    private void buildClassifier(FeatureReader data, int[] instanceIndices) {

        final int bagSize = (int) Math.floor(instanceIndices.length * (bagSizePercent / 100));

        ExecutorService executorPool = Executors.newFixedThreadPool(getNumThreads());

        List<Future> futures = new ArrayList<>();
        final AtomicInteger treesBuilt = new AtomicInteger(0);

        for (int i = 0; i < numTrees; i++) {
            final RandomTree currentClassifier = trees[i] = new RandomTree();
            final int iteration = i;

            final long trainingSetRandomSeed = m_random.nextLong();
            final long treeRandomSeed = m_random.nextLong();

            futures.add(executorPool.submit(new Runnable() {
                @Override
                public void run() {
                    long startTime = System.currentTimeMillis();
                    final int[] trainingSet = getTrainingSet(bagSize, trainingSetRandomSeed, instanceIndices);

                    try {
                        currentClassifier.buildTree(data, trainingSet, treeRandomSeed);
                        onProgress(treesBuilt.getAndAdd(1), numTrees, "Built tree " + iteration + " in " + (System.currentTimeMillis() - startTime) + "ms");
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to build tree " + iteration + " in " + (System.currentTimeMillis() - startTime) + "ms.", e);
                    }
                }
            }));
        }

        try {
            for (Future future : futures) {
                future.get();
                if (future.isCancelled())
                    throw new RuntimeException("Concurrent processing failed.");
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            executorPool.shutdown();
        }

    }

    private int[][] getClassInstances(FeatureReader reader) {
        int numClasses = RandomForest.this.numClasses;

        short[] classes = reader.getClasses();
        int[][] classInstances = new int[numClasses][classes.length];
        int[] classCounts = new int[numClasses];


        for (int i = 0; i < classes.length; i++) {
            int classValue = reader.getClassValue(i);
            classInstances[classValue][classCounts[classValue]++] = i;
        }

        for (int i = 0; i < numClasses; i++)
            classInstances[i] = Arrays.copyOf(classInstances[i], classCounts[i]);

        return classInstances;
    }

    private int[] getTrainingSet(final int bagSize, long randomSeed, int[] instanceIndices) {
        Random random = new Random(randomSeed);

        final int numInstances = instanceIndices.length;

        int[] trainingSet = new int[bagSize];

        for (int i = 0; i < bagSize; i++)
            trainingSet[i] = instanceIndices[random.nextInt(numInstances)];

        return trainingSet;
    }

    public double[] distributionForInstance(final FeatureReader data, final int instanceIndex) {
        final double[] sums = new double[RandomForest.this.numClasses];

        for (int i = 0; i < RandomForest.this.numTrees; i++) {
            final double[] newProbs = trees[i].distributionForInstance(data, instanceIndex);
            if (null != newProbs)
                for (int j = 0; j < newProbs.length; j++)
                    sums[j] += newProbs[j];
        }
        if (Utility.eq(Utility.sum(sums), 0)) {
            return sums;
        } else {
            Utility.normalize(sums);
            return sums;
        }
    }

    public double[][] distributionForInstances(final FeatureReader data) throws InterruptedException {
        final int numInstances = data.getNumInstances();
        final double[][] distributions = new double[numInstances][];

        final AtomicInteger current = new AtomicInteger(0);
        final ExecutorService e = Executors.newFixedThreadPool(getNumThreads());

        for (int threadIndex = 0; threadIndex < getNumThreads(); threadIndex++)
            e.submit(() -> {
                while (true) {
                    int index = current.getAndIncrement();
                    if (index >= numInstances)
                        return;

                    double[] result;
                    final double[] sums = new double[RandomForest.this.numClasses];

                    for (int i = 0; i < RandomForest.this.numTrees; i++) {
                        final double[] newProbs = trees[i].distributionForInstance(data, index);
                        if (null != newProbs)
                            for (int j = 0; j < newProbs.length; j++)
                                sums[j] += newProbs[j];
                    }
                    if (Utility.eq(Utility.sum(sums), 0)) {
                        result = sums;
                    } else {
                        Utility.normalize(sums);
                        result = sums;
                    }
                    distributions[index] = result;
                }
            });

        e.shutdown();
        e.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);

        return distributions;
    }

    public int[] classForInstances(final FeatureReader data) throws InterruptedException {
        return classForInstances(data, IntStream.range(0, data.getNumInstances()).toArray());
    }

    public int[] classForInstances(final FeatureReader data, int[] instanceIndices) throws InterruptedException {
        final int numInstances = instanceIndices.length;
        final int[] classes = new int[numInstances];

        final int progressPoint = numInstances / 100;

        final AtomicInteger current = new AtomicInteger(0);
        final ExecutorService e = Executors.newFixedThreadPool(getNumThreads());

        for (int threadIndex = 0; threadIndex < getNumThreads(); threadIndex++)
            e.submit(() -> {
                while (true) {
                    int index = current.getAndIncrement();
                    if (index >= numInstances)
                        return;

                    if (index % progressPoint == 0) {
                        onProgress(index / progressPoint, 100, "Segmented " + (index / progressPoint) + "%");
                    }

                    final double[] sums = new double[RandomForest.this.numClasses];

                    for (int i = 0; i < RandomForest.this.numTrees; i++) {
                        final double[] newProbs = trees[i].distributionForInstance(data, instanceIndices[index]);
                        if (null != newProbs)
                            for (int j = 0; j < newProbs.length; j++)
                                sums[j] += newProbs[j];
                    }

                    int maxClass = 0;

                    for (int c = 1; c < sums.length; c++)
                        if (sums[c] > sums[maxClass])
                            maxClass = c;

                    classes[index] = maxClass;
                }
            });

        e.shutdown();
        e.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);

        return classes;
    }

    protected class RandomTree implements Serializable {
        static final long serialVersionUID = 42L;

        private final int SPLIT_LEFT = 0;
        private final int SPLIT_RIGHT = 1;
        private final int N_SPLIT_DIRECTIONS = 2;
        private final int[] SPLIT_DIRECTIONS = new int[]{SPLIT_LEFT, SPLIT_RIGHT};

        /**
         * The subtrees appended to this tree.
         */
        protected RandomTree[] m_Successors;

        /**
         * The attribute to split on.
         */
        protected int m_Attribute = -1;

        /**
         * The split point.
         */
        protected double m_SplitPoint = Double.NaN;

        /**
         * Class probabilities from the training data in the nominal case. Holds the
         * mean in the numeric case.
         */
        protected double[] m_ClassDistribution = null;

        protected double[] m_NormalizedClassDistribution = null;


        /**
         * Computes class distribution of an instance using the decision tree.
         *
         * @return the computed class distribution
         */
        public double[] distributionForInstance(FeatureReader data, int instanceIndex) {

            if (m_Attribute > -1) {
                final int splitDirection = data.getValue(instanceIndex, m_Attribute) < m_SplitPoint
                        ? SPLIT_LEFT : SPLIT_RIGHT;

                final double[] distribution = m_Successors[splitDirection].distributionForInstance(data, instanceIndex);

                if (distribution != null)
                    return distribution;
            }

            // Node is a leaf or successor is empty
            return m_NormalizedClassDistribution;
        }

        protected void buildTree(FeatureReader data, int[] instanceIndices, long randomSeed) {
            final Random rand = new Random(randomSeed);

            // Create the attribute indices window
            final int[] attIndicesWindow = new int[RandomForest.this.numAttributes - 1];
            int j = 0;
            for (int i = 0; i < attIndicesWindow.length; i++) {
                if (j == data.getClassIndex()) {
                    j++; // do not include the class
                }
                attIndicesWindow[i] = j++;
            }

            // Compute initial class counts
            final double[] classProbs = new double[RandomForest.this.numClasses];
            for (final int i : instanceIndices) {
                classProbs[data.getClassValue(i)] += data.getWeight(i);
            }

            buildTree(data, instanceIndices, classProbs, attIndicesWindow, rand, 0);
        }

        /**
         * Recursively generates a tree.
         *
         * @param data             the data to work with
         * @param classProbs       the class distribution
         * @param attIndicesWindow the attribute window to choose attributes from
         * @param random           random number generator for choosing random attributes
         * @param depth            the current depth
         * @throws Exception if generation fails
         */
        protected void buildTree(FeatureReader data, int[] instanceIndices, double[] classProbs,
                                 int[] attIndicesWindow, Random random, int depth) {

            // Make leaf if there are no training instances
            if (instanceIndices.length == 0) {
                m_Attribute = -1;
                m_ClassDistribution = null;
                m_NormalizedClassDistribution = null;
                return;
            }

            // Check if node doesn't contain enough instances or is pure
            // or maximum depth reached
            double totalWeight = Utility.sum(classProbs);

            // System.err.println("Total weight " + totalWeight);
            // double sum = omaraa.Utils.sum(classProbs);
            if (totalWeight < 2 * m_MinNum ||

                    // Nominal case
                    (Utility.eq(classProbs[Utility.maxIndex(classProbs)], Utility.sum(classProbs)))

                    ||

                    // check tree depth
                    ((RandomForest.this.maxDepth > 0) && (depth >= RandomForest.this.maxDepth))) {

                // Make leaf
                m_Attribute = -1;
                m_ClassDistribution = classProbs.clone();
                m_NormalizedClassDistribution = classProbs.clone();
                Utility.normalize(m_NormalizedClassDistribution);

                return;
            }

            // Compute class distributions and value of splitting
            // criterion for each attribute
            double val = -Double.MAX_VALUE;
            double split = -Double.MAX_VALUE;
            double[][] bestDists = null;
            double[] bestProps = null;
            int bestIndex = 0;

            // Handles to get arrays out of distribution method
            double[][][] dists = new double[1][0][0];

            // Investigate K random attributes
            int attIndex = 0;
            int windowSize = attIndicesWindow.length;
            int k = numAttributes;
            boolean gainFound = false;
            while ((windowSize > 0) && (k-- > 0 || !gainFound)) {

                int chosenIndex = random.nextInt(windowSize);
                attIndex = attIndicesWindow[chosenIndex];

                // shift chosen attIndex out of window
                attIndicesWindow[chosenIndex] = attIndicesWindow[windowSize - 1];
                attIndicesWindow[windowSize - 1] = attIndex;
                windowSize--;

                double currSplit = distribution(dists, attIndex, data, instanceIndices);

                double currVal = gain(dists[0], priorVal(dists[0]));

                if (Utility.gr(currVal, 0)) {
                    gainFound = true;
                }

                if ((currVal > val)) {
                    val = currVal;
                    bestIndex = attIndex;
                    split = currSplit;
                    bestDists = dists[0];
                }
            }

            // Find best attribute
            m_Attribute = bestIndex;

            // Any useful split found?
            if (Utility.gr(val, 0)) {

                // Build subtrees
                m_SplitPoint = split;
                int[][] subsets = splitData(data, instanceIndices);
                m_Successors = new RandomTree[bestDists.length];

                for (int i = 0; i < bestDists.length; i++) {
                    m_Successors[i] = new RandomTree();
                    m_Successors[i].buildTree(data, subsets[i], bestDists[i], attIndicesWindow,
                            random, depth + 1);
                }

                // If all successors are non-empty, we don't need to store the class
                // distribution
                boolean emptySuccessor = false;
                for (int i = 0; i < subsets.length; i++) {
                    if (m_Successors[i].m_ClassDistribution == null) {
                        emptySuccessor = true;
                        break;
                    }
                }
                if (emptySuccessor) {
                    m_ClassDistribution = classProbs.clone();
                }
            } else {

                // Make leaf
                m_Attribute = -1;
                m_ClassDistribution = classProbs.clone();
            }

            if (m_ClassDistribution != null) {
                m_NormalizedClassDistribution = classProbs.clone();
                Utility.normalize(m_NormalizedClassDistribution);
            }
        }

        /**
         * Splits instances into subsets based on the given split.
         *
         * @param data the data to work with
         * @return the subsets of instances
         * @throws Exception if something goes wrong
         */
        protected int[][] splitData(final FeatureReader data, final int[] instanceIndices) {
            int[] subsetCounts = new int[N_SPLIT_DIRECTIONS];
            int[][] subsets = new int[N_SPLIT_DIRECTIONS][instanceIndices.length];

            for (final int i : instanceIndices) {
                final int splitDirection = (data.getValue(i, m_Attribute) < m_SplitPoint) ? SPLIT_LEFT : SPLIT_RIGHT;
                subsets[splitDirection][subsetCounts[splitDirection]++] = i;
            }

            for (final int splitDirection : SPLIT_DIRECTIONS)
                subsets[splitDirection] = Arrays.copyOf(subsets[splitDirection], subsetCounts[splitDirection]);

//			Assert.that(subsets[SPLIT_LEFT].length+subsets[SPLIT_RIGHT].length == instanceIndices.length, "Total split indices "+(subsets[SPLIT_LEFT].length+subsets[SPLIT_RIGHT].length)+" not equal to total indices "+instanceIndices.length);

            return subsets;
        }

        /**
         * Computes class distribution for an attribute.
         *
         * @param dists
         * @param att   the attribute index
         * @param data  the data to work with
         * @throws Exception if something goes wrong
         */
        protected double distribution(double[][][] dists,
                                      int att, FeatureReader data, int[] instanceIndices) {

            double splitPoint = Double.NaN;
            double[][] dist = null;

            {

                // For numeric attributes
                double[][] currDist = new double[2][RandomForest.this.numClasses];
                dist = new double[2][RandomForest.this.numClasses];

                int[] sortedIndices = data.getSortedIndices(att, instanceIndices);

                // Move all instances into second subset
                for (int j : sortedIndices) {
                    currDist[1][(int) data.getClassValue(j)] += data.getWeight(j);
                }

                // Value before splitting
                double priorVal = priorVal(currDist);

                // Save initial distribution
                for (int j = 0; j < currDist.length; j++) {
                    System.arraycopy(currDist[j], 0, dist[j], 0, dist[j].length);
                }

                // Try all possible split points
                double currSplit = data.getValue(sortedIndices[0], att);
                double currVal, bestVal = -Double.MAX_VALUE;
                for (int i : sortedIndices) {
                    double attVal = data.getValue(i, att);

                    // Can we place a sensible split point here?
                    if (attVal > currSplit) {

                        // Compute gain for split point
                        currVal = gain(currDist, priorVal);

                        // Is the current split point the best point so far?
                        if (currVal > bestVal) {

                            // Store value of current point
                            bestVal = currVal;

                            // Save split point
                            splitPoint = (attVal + currSplit) / 2.0;

                            // Check for numeric precision problems
                            if (splitPoint <= currSplit) {
                                splitPoint = attVal;
                            }

                            // Save distribution
                            for (int j = 0; j < currDist.length; j++) {
                                System.arraycopy(currDist[j], 0, dist[j], 0, dist[j].length);
                            }
                        }

                        // Update value
                        currSplit = attVal;
                    }

                    // Shift over the weight
                    int classVal = data.getClassValue(i);
                    currDist[0][classVal] += data.getWeight(i);
                    currDist[1][classVal] -= data.getWeight(i);
                }
            }

            // Return distribution and split point
            dists[0] = dist;
            return splitPoint;
        }

        /**
         * Computes value of splitting criterion before split.
         *
         * @param dist the distributions
         * @return the splitting criterion
         */
        protected double priorVal(double[][] dist) {

            return Utility.entropyOverColumns(dist);
        }

        /**
         * Computes value of splitting criterion after split.
         *
         * @param dist     the distributions
         * @param priorVal the splitting criterion
         * @return the gain after the split
         */
        protected double gain(double[][] dist, double priorVal) {

            return priorVal - Utility.entropyConditionedOnRows(dist);
        }
    }


}
