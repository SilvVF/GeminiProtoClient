package ios.silv.libgemini.gemini

import kotlinx.io.Source
import kotlinx.io.files.Path

interface IGeminiCache {
    suspend fun deleteResponse(url: String)
    suspend fun getResponse(url: String): Path?
    suspend fun cacheResponse(url: String, header: String, source: Source)
}
