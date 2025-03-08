package ios.silv.gemclient

import android.content.Context
import io.ktor.utils.io.core.copy
import io.ktor.utils.io.core.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue

private const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

class GeminiCache(private val context: Context) {
    private val cacheDir get() = File(context.cacheDir.path, "gemini")

    private val md5 get() = MessageDigest.getInstance("MD5")

    private var bytes = 0L
    private val modified = mutableMapOf<String, Long>()
    private val queue =
        PriorityQueue<String>(compareByDescending { path -> modified[path] ?: Long.MAX_VALUE })

    private val mutex = Mutex(locked = true)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            cacheDir.mkdirs()

            for (file in cacheDir.listFiles().orEmpty()) {
                if (file.isFile) {
                    bytes += file.totalSpace

                    modified[file.name] = file.lastModified()
                    queue.add(file.name)
                }
            }
            cleanupIfNeeded()
        }.invokeOnCompletion {
            mutex.unlock()
        }
    }

    private fun cleanupIfNeeded() {
        val seen = mutableSetOf<String>()
        while (bytes >= CACHE_SIZE_BYTES) {

            val path = queue.poll() ?: return
            if (!seen.add(path)) break

            val file = File(cacheDir, path)

            if (file.delete()) {
                bytes -= file.totalSpace
            } else {
                modified[path] = System.currentTimeMillis()
                queue.add(path)
            }
        }
    }

    private fun hashForKey(key: String): String {
        val hashBytes = md5.digest(key.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteResponse(url: String) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key)
            val space = file.totalSpace

            if (file.delete()) {
                queue.remove(key)
                modified.remove(key)

                bytes -= space
            }
        }
    }

    suspend fun getResponse(url: String): File? {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key)

            return file.takeIf { it.exists() }
        }
    }

    suspend fun cacheResponse(url: String, source: Source) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key).apply {
                createNewFile()
            }

            file.outputStream().asSink().buffered().use { sink ->
                sink.transferFrom(source.copy())
            }

            val modifiedAt = System.currentTimeMillis()
            file.setLastModified(modifiedAt)

            modified[file.name] = modifiedAt
            queue.add(file.name)

            bytes += file.totalSpace

            cleanupIfNeeded()
        }
    }
}