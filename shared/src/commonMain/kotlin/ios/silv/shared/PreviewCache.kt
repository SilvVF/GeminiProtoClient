package ios.silv.shared

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import okio.Path

interface PreviewCache {
    val invalidated: Flow<Unit>
    suspend fun write(tabId: Long, bitmap: ImageBitmap): Result<Path>
    suspend fun read(tabId: Long): Path?
    suspend fun clean(tabIds: List<Long>): Boolean
}
