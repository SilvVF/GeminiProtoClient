package ios.silv.libgemini.gemini

import android.content.Context
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.writeString
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue

actual fun <T: Any> createPriorityQueue(comparator: Comparator<T>): ios.silv.libgemini.gemini.PriorityQueue<T> {
    return object : ios.silv.libgemini.gemini.PriorityQueue<T> {
        val queue = PriorityQueue<T>()
        override fun add(element: T) = queue.add(element)
        override fun poll(): T? = queue.poll()
        override fun remove(element: T): Boolean = queue.remove(element)
        override fun isEmpty(): Boolean = queue.isEmpty()
    }
}