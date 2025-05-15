package ios.silv.libgemini.gemini

import kotlinx.io.Source
import kotlinx.io.files.Path

internal const val CACHE_SIZE_BYTES = 1024 * 1024 * 150L

interface IGeminiCache {
    suspend fun deleteResponse(url: String)
    suspend fun getResponse(url: String): Path?
    suspend fun cacheResponse(url: String, header: String, source: Source)
}
