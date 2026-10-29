package dev.jamescullimore.dontgotobed

/** A* frontier with logarithmic lookup/update and the old list's insertion-order ties. */
internal class RouteFrontier<T : Any> {
    private class Entry<T>(val value: T, var priority: Float, val order: Long, var index: Int)
    private val heap = ArrayList<Entry<T>>()
    private val entries = HashMap<T, Entry<T>>()
    private var nextOrder = 0L

    fun offer(value: T, priority: Float) {
        val existing = entries[value]
        if (existing != null) {
            if (priority >= existing.priority) return
            existing.priority = priority
            siftUp(existing.index)
        } else {
            val entry = Entry(value, priority, nextOrder++, heap.size)
            entries[value] = entry
            heap.add(entry)
            siftUp(entry.index)
        }
    }

    fun removeFirst(): T? {
        if (heap.isEmpty()) return null
        val first = heap[0]
        entries.remove(first.value)
        val last = heap.removeAt(heap.lastIndex)
        if (heap.isNotEmpty()) {
            heap[0] = last
            last.index = 0
            var index = 0
            while (true) {
                val left = index * 2 + 1
                if (left >= heap.size) break
                val right = left + 1
                val child = if (right < heap.size && before(heap[right], heap[left])) right else left
                if (!before(heap[child], heap[index])) break
                swap(index, child)
                index = child
            }
        }
        return first.value
    }

    private fun before(a: Entry<T>, b: Entry<T>) =
        a.priority < b.priority || (a.priority == b.priority && a.order < b.order)

    private fun siftUp(start: Int) {
        var index = start
        while (index > 0) {
            val parent = (index - 1) / 2
            if (!before(heap[index], heap[parent])) break
            swap(index, parent)
            index = parent
        }
    }

    private fun swap(a: Int, b: Int) {
        val old = heap[a]
        heap[a] = heap[b]; heap[a].index = a
        heap[b] = old; old.index = b
    }
}
