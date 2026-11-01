package ios.silv.libgemini.gemini

actual fun <T: Any> createPriorityQueue(
    comparator: Comparator<T>
): PriorityQueue<T> = IosPriorityQueue(comparator)

private class IosPriorityQueue<T: Any>(
    private val comparator: Comparator<T>
) : PriorityQueue<T> {

    private val heap = mutableListOf<T>()

    override fun add(element: T): Boolean {
        val res = heap.add(element)
        siftUp(heap.lastIndex)
        return res
    }

    override fun poll(): T? {
        if (heap.isEmpty()) return null

        val result = heap.first()
        val last = heap.removeAt(heap.lastIndex)

        if (heap.isNotEmpty()) {
            heap[0] = last
            siftDown(0)
        }

        return result
    }

    override fun remove(element: T): Boolean {
        val index = heap.indexOf(element)
        if (index == -1) return false

        val last = heap.removeAt(heap.lastIndex)

        if (index < heap.size) {
            heap[index] = last
            siftUp(index)
            siftDown(index)
        }

        return true
    }

    override fun isEmpty(): Boolean = heap.isEmpty()

    private fun siftUp(start: Int) {
        var index = start

        while (index > 0) {
            val parent = (index - 1) / 2

            if (comparator.compare(heap[index], heap[parent]) >= 0) break

            heap[index] = heap[parent]
            heap[parent] = heap[index].also { heap[index] = heap[parent] }

            index = parent
        }
    }

    private fun siftDown(start: Int) {
        var index = start

        while (true) {
            val left = index * 2 + 1
            val right = left + 1
            var smallest = index

            if (left < heap.size &&
                comparator.compare(heap[left], heap[smallest]) < 0
            ) {
                smallest = left
            }

            if (right < heap.size &&
                comparator.compare(heap[right], heap[smallest]) < 0
            ) {
                smallest = right
            }

            if (smallest == index) break

            val temp = heap[index]
            heap[index] = heap[smallest]
            heap[smallest] = temp

            index = smallest
        }
    }
}