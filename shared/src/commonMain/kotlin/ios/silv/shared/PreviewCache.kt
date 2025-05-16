package ios.silv.shared

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import okio.Path

interface PreviewCache {
    val invalidated: Flow<Unit>
    suspend fun write(url: String, bitmap: ImageBitmap): Result<Path>
    suspend fun read(url: String): Path?
    suspend fun clean(urls: List<String>): Boolean
}
