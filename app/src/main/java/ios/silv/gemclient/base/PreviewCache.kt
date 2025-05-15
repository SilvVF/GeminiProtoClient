package ios.silv.gemclient.base

import android.content.Context
import android.graphics.Bitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ios.silv.core_android.log.LogPriority
import ios.silv.core_android.log.logcat
import ios.silv.core_android.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
class PreviewCache @Inject constructor(
    context: Context,
) {

    private val cacheDir = File(context.cacheDir, "tab_preview")

    private val _invalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val invalidated = _invalidated.asSharedFlow()
        .conflate()
        .onStart { emit(Unit) }

    suspend fun cleanCache(tabIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        suspendRunCatching {
            val files = cacheDir.listFiles()
                ?.filterNotNull()
                ?.filter { it.isFile }
                .orEmpty()

            for (f in files) {
                try {
                    val split = f.nameWithoutExtension.split("_")
                    val tabId = split.lastOrNull()?.toLongOrNull() ?: continue
                    if (tabId !in tabIds) {
                        f.delete()
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "error trying to check file $f" }
                }
            }
        }
            .also { _invalidated.tryEmit(Unit) }
            .isSuccess
    }

    private fun fileKey(tabId: Long) = "tab_$tabId.png"

    suspend fun writeToCache(tabId: Long, bitmap: Bitmap): Result<File> =
        withContext(Dispatchers.IO) {
            suspendRunCatching {
                cacheDir.mkdirs()
                File(cacheDir, fileKey(tabId)).also { f ->
                    f.outputStream().use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
            }
                .onSuccess {
                    logcat { "Successfully wrote to cache tab_id=${tabId}" }
                    _invalidated.tryEmit(Unit)
                }
        }

    fun readFromCache(tabId: Long): File? {
        return File(cacheDir, fileKey(tabId)).takeIf { it.exists() }
    }
}