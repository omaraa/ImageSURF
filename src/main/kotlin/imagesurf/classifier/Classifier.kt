package imagesurf.classifier

import imagesurf.feature.FeatureReader
import java.util.stream.IntStream

interface Classifier {
    fun distributionForInstance(data: FeatureReader, instanceIndex: Int): DoubleArray

    fun distributionForInstances(data: FeatureReader): Array<DoubleArray>

    fun classForInstances(data: FeatureReader, instanceIndices: IntArray = IntStream.range(0, data.numInstances).toArray()): IntArray
}