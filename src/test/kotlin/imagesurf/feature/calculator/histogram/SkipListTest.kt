package imagesurf.feature.calculator.histogram

import org.junit.Test
import org.assertj.core.api.Assertions.*

class SkipListTest {
    @Test
    fun ascending() {
        val ascending = (0 until 1000)
        val list = SkipList()
        ascending.shuffled().forEach{ list.add(it)}

        list.ascending()
                .asSequence()
                .toList()
                .zip(ascending) { expected, actual ->
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    fun descending() {
        val descending = (1000 downTo 0)

        val list = SkipList()

        descending.shuffled().forEach{ list.add(it)}

        list.descending()
                .asSequence()
                .toList()
                .zip(descending) { expected, actual ->
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