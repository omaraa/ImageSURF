package imagesurf.feature.calculator.histogram

import org.junit.Test
import org.assertj.core.api.Assertions.*
import kotlin.random.Random

class SkipListTest {

    @Test
    fun size() {
        val values = (0 until 1000).toList()
        val list = SkipList()
        values.shuffled().forEach{ list.add(it)}

        assertThat(list.size()).isEqualTo(values.size)

        val random = Random(42)

        val (included, removed) = values.partition { random.nextBoolean()}

        removed.forEach { list.remove(it) }

        assertThat(list.size()).isEqualTo(included.size)
    }

    @Test
    fun ascending() {
        val ascending = (0 until 1000).toList()
        val list = SkipList()
        ascending.shuffled().forEach{ list.add(it)}

        val retrieved = list.ascending()
                .asSequence()
                .toList()

        assertThat(retrieved.size).isEqualTo(ascending.size)

        retrieved.zip(ascending) { expected, actual ->
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun descending() {
        val descending = (1000 downTo 0).toList()
        val list = SkipList()
        descending.shuffled().forEach{ list.add(it)}

        val retrieved = list.descending()
                .asSequence()
                .toList()

        assertThat(retrieved.size).isEqualTo(descending.size)

        retrieved.zip(descending) { expected, actual ->
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun firstValue() {
        val ascending = (0 until 1000)
        val list = SkipList()
        ascending.shuffled().forEach{ list.add(it)}

        assertThat(list.firstValue()).isEqualTo(ascending.first)
    }

    @Test
    fun lastValue() {
        val ascending = (0 until 1000)
        val list = SkipList()
        ascending.shuffled().forEach{ list.add(it)}

        assertThat(list.lastValue()).isEqualTo(ascending.last)
    }
}