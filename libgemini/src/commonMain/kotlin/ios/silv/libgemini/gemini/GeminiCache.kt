package ios.silv.libgemini.gemini

import kotlinx.coroutines.IO
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Source
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.time.Clock

interface IGeminiCache {
    suspend fun deleteResponse(url: String)
    suspend fun getResponse(url: String): Path?
    suspend fun cacheResponse(url: String, header: String, source: Source)
}

interface PriorityQueue<T> {
    fun add(element: T): Boolean
    fun poll(): T?
    fun remove(element: T): Boolean
    fun isEmpty(): Boolean
}

expect fun <T : Any> createPriorityQueue(
    comparator: Comparator<T>
): PriorityQueue<T>


class GeminiCache(
    cacheDir: Path,
    private val fileSystem: FileSystem,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : IGeminiCache {

    private val directory = cacheDir / "gemini"

    private var bytes = 0L
    private val modified = mutableMapOf<String, Long>()
    private val queue = createPriorityQueue<String>(
        compareBy { modified[it] ?: Long.MAX_VALUE }
    )

    private val mutex = Mutex(locked = true)

    init {
        scope.launch {
            try {
                fileSystem.createDirectories(directory)

                for (path in fileSystem.list(directory)) {
                    val metadata = fileSystem.metadata(path)

                    if (!metadata.isDirectory) {
                        bytes += metadata.size ?: 0L
                        modified[path.name] = metadata.lastModifiedAtMillis ?: 0L
                        queue.add(path.name)
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

    private fun updateModified(path: Path) {
        fileSystem.appendingSink(path).buffer().use { sink ->
            sink.flush()
        }
        modified[path.name] = Clock.System.now().toEpochMilliseconds()
    }

    private fun cleanupIfNeeded() {
        while (bytes >= CACHE_SIZE_BYTES) {
            val name = queue.poll() ?: break
            val path = directory / name
            val size = fileSystem.metadataOrNull(path)?.size ?: 0L

            fileSystem.delete(path)

            bytes -= size
            modified.remove(name)
        }
    }

    private fun hashForKey(key: String): String =
        key.encodeUtf8().md5().hex()

    override suspend fun deleteResponse(url: String) {
        mutex.withLock {
            val key = hashForKey(url)
            val path = directory / key

            val size = fileSystem.metadataOrNull(path)?.size ?: return@withLock

            fileSystem.delete(path)
            queue.remove(key)
            modified.remove(key)

            bytes -= size
        }
    }

    override suspend fun getResponse(url: String): Path? {
        return mutex.withLock {
            val path = directory / hashForKey(url)

            logcat { "checking cache for \n\t path: $path \n\t url: $url" }
            if (fileSystem.exists(path)) {
                logcat { "updating modified $path" }
                updateModified(path)
                path
            } else {
                null
            }
        }
    }

    override suspend fun cacheResponse(
        url: String,
        header: String,
        source: Source
    ) {
        mutex.withLock {
            val path = directory / hashForKey(url)
            val oldSize = fileSystem.metadataOrNull(path)?.size ?: 0L
            logcat { "writing to cache \t\n url: $url \t\n path: $path" }
            source.use { src ->
                fileSystem.sink(path).buffer().use { sink ->
                    sink.writeUtf8(header.removeSuffix("\r\n") + "\r\n")

                    val buffer = ByteArray(8192)

                    while (true) {
                        val read = src.readAtMostTo(buffer, 0, buffer.size)
                        if (read == -1) break

                        sink.write(buffer, 0, read)
                    }
                }
            }

            updateModified(path)

            queue.remove(path.name)
            queue.add(path.name)

            val updatedSize = (fileSystem.metadata(path).size ?: 0L)
            bytes += updatedSize - oldSize

            cleanupIfNeeded()
            logcat { "wrote response to cache path: $path \n url: $url" }
        }
    }
}