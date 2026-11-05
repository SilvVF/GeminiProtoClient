package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import com.ditchoom.buffer.BufferFactory
import com.ditchoom.buffer.Default
import com.ditchoom.buffer.PlatformBuffer
import com.ditchoom.buffer.ReadBuffer
import com.ditchoom.buffer.flow.ReadResult
import com.ditchoom.buffer.kotlinxio.copyToKotlinxIoBuffer
import com.ditchoom.buffer.kotlinxio.copyToPlatformBuffer
import com.ditchoom.buffer.kotlinxio.readInto
import com.ditchoom.buffer.utf8Size
import io.ktor.http.Url
import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.core.remaining
import ios.silv.logcat.logcat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.Buffer
import kotlinx.io.writeString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.time.Duration

internal actual fun createSocket(
    selector: SelectorManager,
    url: Url,
    store: DataStore<Preferences>
): SocketFactory {
    return IosSocketFactory(store)
}
class IosSocketFactory(
    private val store: DataStore<Preferences>,
) : SocketFactory {

    val socket = NWClientSocketWrapper(
        TransportConfig.tlsInsecure()
    )

    override suspend fun connect(url: Url): SecureSocket {

        socket.open(url.port, url.host)

        logcat { "${url.port} ${url.host}" }

        return object : SecureSocket {
            override suspend fun read(buffer: Buffer): Int {
                var bytes = 0
                loop@ while (true) {
                    when (val r = socket.read(Duration.INFINITE)) {
                        is ReadResult.Data -> {
                            logcat { "reading bytes: ${r.buffer.remaining()}" }
                            while (r.buffer.hasRemaining()) {
                                buffer.writeByte(r.buffer.readByte())
                                bytes++
                            }
                        }
                        else -> {
                            logcat { "done reading" }
                            break@loop
                        }
                    }
                }
                logcat { "read $bytes" }
                return bytes
            }

            override suspend fun write(data: String) {
                val buffer = Buffer()
                buffer.writeString(data)
                socket.write(buffer.copyToPlatformBuffer(), socket.writePolicy.toDeadline())
            }

            override suspend fun flush() = Unit

            override fun close() = socket.close()
        }
    }
}

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