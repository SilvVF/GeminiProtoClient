package ios.silv.gemini

import android.content.Context
import io.ktor.http.escapeIfNeeded
import io.ktor.utils.io.core.copyTo
import ios.silv.core_android.log.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.writeString
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue

private const val CACHE_SIZE_BYTES = 1024 * 1024 * 150L

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
                    bytes += file.length()

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
        logcat { "running cleanup space = $bytes / $CACHE_SIZE_BYTES" }
        val seen = mutableSetOf<String>()
        while (bytes >= CACHE_SIZE_BYTES) {

            val path = queue.poll() ?: return
            if (!seen.add(path)) break

            val file = File(cacheDir, path)

            if (file.delete()) {
                bytes -= file.length()
            } else {
                val mod = System.currentTimeMillis()
                modified[path] = mod
                file.setLastModified(mod)
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
            val space = file.length()

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

    suspend fun cacheResponse(url: String, header: String, source: Source) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key).apply {
                createNewFile()
            }

            source.use { src ->

                copySource(src, header, file.outputStream().asSink().buffered())
            }

            val modifiedAt = System.currentTimeMillis()
            file.setLastModified(modifiedAt)

            modified[file.name] = modifiedAt
            queue.add(file.name)

            bytes += file.length()

            cleanupIfNeeded()
        }
    }

    private fun copySource(source: Source, header: String, sink: Sink) {
        source.use { src ->
            sink.use { dest ->
                dest.writeString(header.removeSuffix("\r\n") + "\r\n")
                src.transferTo(dest)
            }
        }
    }
}