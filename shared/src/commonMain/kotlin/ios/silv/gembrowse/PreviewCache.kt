package ios.silv.gembrowse


import androidx.compose.ui.graphics.ImageBitmap
import ios.silv.libgemini.gemini.suspendRunCatching
import ios.silv.logcat.LogPriority.ERROR
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

expect fun ImageBitmap.encodeThumbnail(targetWidth: Int, quality: Int): ByteArray

interface PreviewCache {
    val invalidated: Flow<Unit>
    suspend fun write(url: String, bitmap: ImageBitmap): Result<Path>
    suspend fun read(url: String): Path?
    suspend fun clean(urls: List<String>): Boolean
}

class CommonPreviewCache(
    private val fs: FileSystem,
    cacheDir: Path
) : PreviewCache {

    private val dir = cacheDir / "preview_cache"

    private val _invalidated = MutableSharedFlow<Unit>()
    override val invalidated = _invalidated.asSharedFlow()
        .conflate()
        .onStart { emit(Unit) }

    private fun Path.nameWithoutExt(): String {
        val extIdx = name.indexOfLast { c -> c == '.' }
        return if (extIdx != -1) {
            name.slice(0..<extIdx)
        } else {
            name
        }
    }

    override suspend fun clean(urls: List<String>): Boolean = withContext(Dispatchers.IO) {
        suspendRunCatching {
            val files = fs.list(dir).filter { path ->
                val meta = fs.metadataOrNull(path)
                meta?.isRegularFile == true
            }

            val hashed = buildSet {
                for (url in urls) {
                    add(fileKey(url))
                }
            }

            for (f in files) {
                try {
                    val name = f.nameWithoutExt()
                    if (name !in hashed) {
                        fs.delete(f)
                    }
                } catch (e: Exception) {
                    logcat(ERROR) { "error trying to check file $f ${e.asLog()}" }
                }
            }
        }
            .also { _invalidated.tryEmit(Unit) }
            .isSuccess
    }

    private fun fileKey(url: String) = url.encodeUtf8().md5().hex()

    override suspend fun write(url: String, bitmap: ImageBitmap): Result<Path> {
        return suspendRunCatching {

            fs.createDirectories(dir)

            val fp = dir / "${fileKey(url)}.jpg"

            val thumbnail = bitmap.encodeThumbnail(
                targetWidth = 480,
                quality = 80,
            )

            fs.write(fp) {
                write(thumbnail)
            }
            fp
        }
            .onSuccess {
                logcat { "Successfully wrote to cache \n\turl=${url}\n\tpath=${it}" }
                _invalidated.tryEmit(Unit)
            }
    }

    override suspend fun read(url: String): Path? {
        val f1 = dir / "${fileKey(url)}.webp"
        val f2 = dir / "${fileKey(url)}.jpg"
        return f1.takeIf { fs.exists(f1) } ?: f2.takeIf { fs.exists(f2) }
    }
}