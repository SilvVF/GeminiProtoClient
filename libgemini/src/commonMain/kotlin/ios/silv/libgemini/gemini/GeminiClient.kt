package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.set
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.tls
import io.ktor.util.Platform
import io.ktor.util.PlatformUtils
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.flushIfNeeded
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.logcat.LogPriority.ERROR
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.readLineStrict
import okio.buffer
import okio.use
import kotlin.coroutines.CoroutineContext

internal const val URLMaxLength = 1024
internal const val MetaMaxLength = 1024

internal const val CACHE_SIZE_BYTES = 1024 * 1024 * 150L

data class Response(
    val status: Int,
    val meta: String,
    val body: Source,
) : AutoCloseable {

    override fun close() {
        body.close()
    }
}


internal data class Header(
    val status: Int,
    val meta: String,
)

private fun consumeHeaderInternal(line: String): Header {
    val fields = line.split(" ")
    if (fields.size < 2) {
        error("Header not formatted correctly")
    }

    val status = fields[0].toIntOrNull() ?: error("Unexpected status value ${fields[0]}")
    val meta = if (line.length <= 3) "" else line.substring(fields[0].length + 1)

    if (meta.length > MetaMaxLength) {
        error("Meta string is too long")
    }

    return Header(status, meta)
}

@Throws(IOException::class)
internal fun consumeHeaderOkio(source: okio.BufferedSource): Result<Header> = runCatching {
    val line = source.readUtf8LineStrict(4096)
    consumeHeaderInternal(line)
}

@Throws(IOException::class)
internal fun consumeHeader(source: Source): Result<Header> = runCatching {
    val line = source.readLineStrict(4096)
    consumeHeaderInternal(line)
}

data class ClientConfig(
    val dispatcher: CoroutineContext =
        Dispatchers.IO +
                SupervisorJob() +
                CoroutineName("GeminiClientScope"),
    val selector: SelectorManager = SelectorManager(dispatcher),
    val maxRedirects: Int = 5
)

internal expect fun createSocket(
    selector: SelectorManager,
    url: Url,
    store: DataStore<Preferences>
): SocketFactory

interface SecureSocket: AutoCloseable {
    suspend fun read(buffer: Buffer): Int
    suspend fun write(data: String)
    suspend fun flush()
    override fun close()
}

interface SocketFactory {
    suspend fun connect(
        url: Url,
    ): SecureSocket
}

class GeminiClient(
    private val cache: IGeminiCache,
    private val config: ClientConfig = ClientConfig(),
    private val fileSystem: okio.FileSystem,
    storage: Storage<Preferences>
) {
    private val store: DataStore<Preferences> = createDataStore(storage)
    private val selector = config.selector

    suspend fun makeGeminiQuery(
        query: String,
        forceNetwork: Boolean = false,
        cacheEnabled: Boolean = true
    ): Result<Response> {
        return withContext(config.dispatcher) {
            fetch(
                url = Url(query),
                forceNetwork,
                cacheEnabled,
                redirected = 0
            )
        }
    }

    private suspend fun readResponseFromCache(
        path: okio.Path,
        redirected: Int,
        cacheEnabled: Boolean
    ): Result<Response> {
        return suspendRunCatching {
            logcat { path.toString() }
            fileSystem.source(path).buffer().use { source ->

                val header = consumeHeaderOkio(source).getOrThrow()

                if (header.status == GeminiCode.REDIRECT_PERMANENT) {
                    return@suspendRunCatching fetch(
                        Url(header.meta),
                        false,
                        cacheEnabled,
                        redirected = (redirected + 1)
                    )
                        .getOrThrow()
                }

                if (source.exhausted()) {
                    error("empty buffer")
                }

                val buffer = Buffer()
                val chunk = ByteArray(8192)

                while (true) {
                    val read = source.read(chunk, 0, chunk.size)
                    if (read == -1) break

                    buffer.write(chunk, 0, read)
                }
                Response(
                    status = header.status,
                    meta = header.meta,
                    body = buffer
                )
            }
        }
    }

    private suspend fun openNewConnection(
        url: Url,
    ): SecureSocket {
        val socket = createSocket(selector, url, store)
        return socket.connect(url)
    }

    private suspend fun fetch(
        url: Url,
        forceNetwork: Boolean,
        cacheEnabled: Boolean,
        redirected: Int
    ): Result<Response> {
        return suspendRunCatching {
            val urlBuilder = URLBuilder(url)
            val urlLength = url.toString().length

            if (urlLength > URLMaxLength) {
                error("url is too long $urlLength > $URLMaxLength")
            }

            if (url.protocol.name != "gemini") {
                error("unsupported protocol ${url.protocol.name}")
            }

            if (url.port == 0) {
                urlBuilder.set {
                    port = 1965
                }
            }

            val url = urlBuilder.build()

            if (!forceNetwork) {
                logcat { "looking in cache for $url" }
                cache.getResponse(url.toString())?.let { cached ->
                    logcat { "Found entry in cache for $url" }
                    readResponseFromCache(cached, redirected, cacheEnabled)
                        .onSuccess { response ->
                            logcat { "successfully read from cache from $url" }
                            return@suspendRunCatching response
                        }
                        .onFailure {
                            cache.deleteResponse("$url")
                            logcat(ERROR) { "failed to parse cache entry for $url ${it.message}" }
                        }
                } ?: logcat { "unable to find response in cache" }
            }

            logcat { "Trying to connect to ${url.host}:${url.port} - $url" }

            openNewConnection(url).use { conn ->

                conn.write("${url}\r\n")

                val buffer = Buffer()

                conn.read(buffer)
                val header = consumeHeader(buffer).getOrThrow()

                when (header.status) {
                    GeminiCode.REDIRECT_PERMANENT,
                    GeminiCode.REDIRECT_TEMPORARY -> {
                        handleRedirect(
                            header,
                            redirected,
                            url,
                            forceNetwork,
                            cacheEnabled
                        )
                    }

                    else -> {
                        if (header.status == GeminiCode.SUCCESS) {
                            logcat { "writing response to cache url: $url header: $header" }
                            cache.cacheResponse(
                                url = url.toString(),
                                header = "${header.status} ${header.meta}\r\n",
                                source = buffer.copy()
                            )
                        }

                        Response(
                            status = header.status,
                            meta = header.meta,
                            body = buffer
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleRedirect(
        header: Header,
        redirected: Int,
        url: Url,
        forceNetwork: Boolean,
        cacheEnabled: Boolean,
    ): Response {
        if (redirected > config.maxRedirects) {
            error("client was redirected $redirected times > max ${config.maxRedirects}")
        }

        if (header.status == GeminiCode.REDIRECT_PERMANENT) {
            cache.cacheResponse(
                url = url.toString(),
                header = "${header.status} ${header.meta}\r\n",
                source = Buffer()
            )
        }

        return fetch(
            Url(header.meta),
            forceNetwork,
            cacheEnabled,
            (redirected + 1)
        )
            .getOrThrow()
    }
}




