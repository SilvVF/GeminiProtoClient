package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.Source
import kotlinx.io.files.Path
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun TLSConfigBuilder.applyPlatformTofuConfig(
    url: Url,
    store: DataStore<Preferences>
) {
    val host: String = url.host
    val port: Int = url.port

}

@OptIn(ExperimentalForeignApi::class)
fun IOSGeminiClient() = GeminiClient(
    cache = object : IGeminiCache {
        override suspend fun deleteResponse(url: String) {

        }
        override suspend fun getResponse(url: String): Path? {
            return null
        }
        override suspend fun cacheResponse(url: String, header: String, source: Source) {
        }
    },
    storage = OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = PreferencesSerializer,
        producePath = {
            val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            (requireNotNull(documentDirectory).path + "/$dataStoreFileName").toPath()
        }
    )
)