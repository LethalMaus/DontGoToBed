package dev.jamescullimore.dontgotobed

import kotlin.random.Random
import kotlin.test.*

class RouteFrontierTest {
    @Test fun decreasingPriorityPreservesOriginalInsertionOrderForTies() {
        val q = RouteFrontier<String>()
        q.offer("first", 8f); q.offer("second", 3f); q.offer("third", 3f)
        q.offer("first", 3f)
        q.offer("second", 20f) // A worse route must not replace the existing route.
        assertEquals("first", q.removeFirst())
        assertEquals("second", q.removeFirst())
        assertEquals("third", q.removeFirst())
        assertNull(q.removeFirst())
        q.offer("third", 1f); q.offer("first", 1f)
        assertEquals("third", q.removeFirst())
        assertEquals("first", q.removeFirst())
    }

    @Test fun mixedInsertionsDecreasesAndRemovalsMatchStableListOrdering() {
        val q = RouteFrontier<Int>()
        val reference = linkedMapOf<Int, Float>()
        val random = Random(713)
        fun remove() {
            val expected = reference.entries.minByOrNull { it.value }?.key
            assertEquals(expected, q.removeFirst())
            reference.remove(expected)
        }
        repeat(5000) {
            if (random.nextInt(4) == 0) remove()
            else {
                val value = random.nextInt(200)
                val priority = random.nextInt(50).toFloat()
                reference[value] = minOf(priority, reference[value] ?: Float.POSITIVE_INFINITY)
                q.offer(value, priority)
            }
        }
        while (reference.isNotEmpty()) remove()
        assertNull(q.removeFirst())
    }
}
