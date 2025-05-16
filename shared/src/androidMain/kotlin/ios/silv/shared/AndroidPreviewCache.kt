package ios.silv.shared

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ios.silv.core.logcat.LogPriority.ERROR
import ios.silv.core.logcat.logcat
import ios.silv.core.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidPreviewCache @Inject constructor(
    context: Context
): PreviewCache  {

    private val cacheDir = File(context.cacheDir, "tab_preview")

    private val _invalidated = MutableSharedFlow<Unit>()
    override val invalidated = _invalidated.asSharedFlow()
        .conflate()
        .onStart { emit(Unit) }

    override suspend fun clean(tabIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
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
                    logcat(ERROR) { "error trying to check file $f" }
                }
            }
        }
            .also { _invalidated.tryEmit(Unit) }
            .isSuccess
    }

    private fun fileKey(tabId: Long) = "tab_$tabId.png"

    override suspend fun write(tabId: Long, bitmap: ImageBitmap): Result<Path> =
        withContext(Dispatchers.IO) {
            suspendRunCatching {
                cacheDir.mkdirs()
                File(cacheDir, fileKey(tabId)).also { f ->
                    f.outputStream().use { os ->
                        bitmap.asAndroidBitmap()
                            .compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
                    .toOkioPath()
            }
                .onSuccess {
                    logcat { "Successfully wrote to cache tab_id=${tabId}" }
                    _invalidated.tryEmit(Unit)
                }
        }

    override suspend fun read(tabId: Long): Path? {
        return File(cacheDir, fileKey(tabId)).takeIf { it.exists() }?.toOkioPath()
    }
}