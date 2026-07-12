package ios.silv.gembrowse

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import ios.silv.libgemini.gemini.suspendRunCatching
import ios.silv.logcat.LogPriority.ERROR
import ios.silv.logcat.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.security.MessageDigest

class AndroidPreviewCache(
    context: Context
): PreviewCache  {

    private val cacheDir = File(context.cacheDir, "tab_preview")

    private val _invalidated = MutableSharedFlow<Unit>()
    override val invalidated = _invalidated.asSharedFlow()
        .conflate()
        .onStart { emit(Unit) }

    private val md5 by lazy {
        MessageDigest.getInstance("md5")
    }

    override suspend fun clean(urls: List<String>): Boolean = withContext(Dispatchers.IO) {
        suspendRunCatching {
            val files = cacheDir.listFiles()
                ?.filterNotNull()
                ?.filter { it.isFile }
                .orEmpty()

            val hashed = buildSet {
                for (url in urls) {
                    add(fileKey(url))
                }
            }

            for (f in files) {
                try {
                    val name = f.nameWithoutExtension
                    if (name !in hashed) {
                        f.delete()
                    }
                } catch (e: Exception) {
                    logcat(ERROR) { "error trying to check file $f" }
                }
            }
        }
            .also { _invalidated.tryEmit(Unit) }
            .isSuccess
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun fileKey(url: String) = md5.digest(url.toByteArray()).toHexString()

    override suspend fun write(url: String, bitmap: ImageBitmap): Result<Path> =
        withContext(Dispatchers.IO) {
            suspendRunCatching {
                cacheDir.mkdirs()
                File(cacheDir, fileKey(url) + ".png").also { f ->
                    f.outputStream().use { os ->
                        bitmap.asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
                    .toOkioPath()
            }
                .onSuccess {
                    logcat { "Successfully wrote to cache url=${url}" }
                    _invalidated.tryEmit(Unit)
                }
        }

    override suspend fun read(url: String): Path? {
        return File(cacheDir, fileKey(url) + ".png").takeIf { it.exists() }?.toOkioPath()
    }
}