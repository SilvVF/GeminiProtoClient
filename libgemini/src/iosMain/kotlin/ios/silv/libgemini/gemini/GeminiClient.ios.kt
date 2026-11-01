package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import io.ktor.http.Url
import io.ktor.network.tls.TLSConfigBuilder
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSUserDomainMask
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.toByteString
import platform.Foundation.*
import platform.Security.*
import kotlin.time.Clock


internal actual fun TLSConfigBuilder.applyPlatformTofuConfig(
    url: Url,
    store: DataStore<Preferences>
) = Unit

@OptIn(ExperimentalForeignApi::class)
fun IOSGeminiClient() = GeminiClient(
    fileSystem = FileSystem.SYSTEM,
    cache = GeminiCache(
        fileSystem = FileSystem.SYSTEM,
        cacheDir = NSFileManager.defaultManager.URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )!!.path!!.toPath()
    ),
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