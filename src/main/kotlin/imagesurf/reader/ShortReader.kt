package imagesurf.reader

import imagesurf.feature.FeatureReader
import net.mintern.primitive.Primitive

class ShortReader(private val values: Array<ShortArray>, private val classIndex: Int) : FeatureReader {

    constructor(values: List<Any?>, classIndex: Int) : this(toShorts(values), classIndex)

    private val numClasses = values[classIndex].toSet().size

    override fun getNumClasses(): Int {
        return numClasses
    }

    override fun getClassValue(instanceIndex: Int): Int {
        return values[classIndex][instanceIndex].toInt() and BIT_MASK
    }

    override fun getValue(instanceIndex: Int, attributeIndex: Int): Double {
        return (values[attributeIndex][instanceIndex].toInt() and BIT_MASK).toDouble()
    }

    override fun getSortedIndices(attributeIndex: Int, instanceIndices: IntArray): IntArray {
        val attributeArray = values[attributeIndex]
        return instanceIndices.copyOf(instanceIndices.size)
                .also {
                    Primitive.sort(it) {
                        i1, i2 -> (attributeArray[i1].toInt() and BIT_MASK).compareTo((attributeArray[i2].toInt() and BIT_MASK))
                    }
                }
    }

    override fun getNumInstances(): Int = values[classIndex].size

    override fun getNumFeatures(): Int = values.size

    override fun getClassIndex(): Int = classIndex

    companion object {
        private const val BIT_MASK: Int = 0xff

        private fun toShorts(shortArrays: Any): Array<ShortArray> = when (shortArrays) {
            is List<*> -> shortArrays.map { it as ShortArray }.toTypedArray()
            is Array<*> -> shortArrays.map { it as ShortArray }.toTypedArray()
            else -> throw RuntimeException("Failed to read $shortArrays using ShortReader")
        }
    }

    override fun withFeatures(indices: List<Int>): FeatureReader =
            indices.filter { index: Int -> index != getClassIndex() && index >= 0 }
                    .map { values[it] }
                    .plusElement( values[classIndex] )
                    .let { ShortReader(it, it.lastIndex) }
}