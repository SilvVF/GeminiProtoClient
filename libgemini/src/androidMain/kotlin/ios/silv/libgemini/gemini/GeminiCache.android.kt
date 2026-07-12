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

class GeminiCache(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : IGeminiCache {
    private val cacheDir by lazy { File(context.cacheDir.path, "gemini") }

    private val md5 by lazy { MessageDigest.getInstance("MD5") }

    private var bytes = 0L
    private val modified = mutableMapOf<String, Long>()
    private val queue =
        PriorityQueue<String>(compareByDescending { path -> modified[path] ?: Long.MAX_VALUE })

    private val mutex = Mutex(locked = true)

    fun File.updateModified() {
        val mod = System.currentTimeMillis()
        modified[path] = mod
        this.setLastModified(mod)
    }

    init {
        scope.launch {
            try {
                cacheDir.mkdirs()

                for (file in cacheDir.listFiles().orEmpty()) {
                    if (file.isFile) {
                        bytes += file.length()

                        modified[file.name] = file.lastModified()
                        queue.add(file.name)
                    }
                }
                cleanupIfNeeded()
            } catch (e: Exception) {
                logcat { e.asLog() }
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun cleanupIfNeeded() {
        logcat { "running cleanup space = $bytes / $CACHE_SIZE_BYTES" }
        while (bytes >= CACHE_SIZE_BYTES) {

            val path = queue.poll() ?: break

            val file = File(cacheDir, path)

            if (file.delete()) {
                bytes -= file.length()
                modified.remove(file.name)
            }
        }
    }

    private fun hashForKey(key: String): String {
        val hashBytes = md5.digest(key.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    override suspend fun deleteResponse(url: String) {
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

    override suspend fun getResponse(url: String): Path? {
        return mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key)

            if (file.exists()) {
                file.updateModified()
                Path(file.path)
            } else {
                null
            }
        }
    }

    override suspend fun cacheResponse(url: String, header: String, source: Source) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key).apply {
                createNewFile()
            }

            source.use { src ->
                file.outputStream().use { fos ->
                    fos.asSink().buffered().use { sink ->
                        sink.writeString(header.removeSuffix("\r\n") + "\r\n")
                        src.transferTo(sink)
                    }
                }
            }

            file.updateModified()
            queue.add(file.name)

            bytes += file.length()

            cleanupIfNeeded()
        }
    }
}