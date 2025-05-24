package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
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
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.core.logcat.LogPriority.*
import ios.silv.core.logcat.logcat
import ios.silv.core.suspendRunCatching
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.CoroutineContext

internal const val URLMaxLength = 1024
internal const val MetaMaxLength = 1024

data class Response(
    val status: Int,
    val meta: String,
    val body: Source,
) : Closeable {

    override fun close() {
        body.close()
    }
}

data class ClientConfig(
    val dispatcher: CoroutineContext =
        Dispatchers.IO +
                SupervisorJob() +
                CoroutineName("GeminiClientScope"),
    val selector: SelectorManager = SelectorManager(dispatcher),
    val maxRedirects: Int = 5
)

internal expect fun TLSConfigBuilder.applyPlatformTofuConfig(url: Url, store: DataStore<Preferences>)

class GeminiClient(
    private val cache: IGeminiCache,
    private val store: DataStore<Preferences>,
    private val config: ClientConfig = ClientConfig()
) {

    private val fileSystem: FileSystem = SystemFileSystem
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
        path: Path,
        redirected: Int,
        cacheEnabled: Boolean
    ): Result<Response> {
        return suspendRunCatching {
            fileSystem.source(path).buffered().use { source ->
                val header = consumeHeader(source).getOrThrow()

                if (header.status == GeminiCode.REDIRECT_PERMANENT) {
                    return@suspendRunCatching fetch(
                        Url(header.meta),
                        false,
                        cacheEnabled,
                        redirected = (redirected + 1)
                    )
                        .getOrThrow()
                }

                if (source.remaining <= 0L) {
                    error("empty buffer")
                }

                val buffer = Buffer().apply {
                    source.transferTo(this)
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
    ): Socket {
        return aSocket(selector).tcp().connect(
            url.host,
            url.port,
        )
            .tls(Dispatchers.IO) {
                applyPlatformTofuConfig(url, store)
            }
    }

    private suspend fun fetch(
        url: Url,
        forceNetwork: Boolean,
        cacheEnabled: Boolean,
        redirected: Int
    ): Result<Response> {
        return suspendRunCatching {

            if (!forceNetwork) {
                cache.getResponse(url.toString())?.let { cached ->
                    logcat { "Found entry in cache for $url" }
                    readResponseFromCache(cached, redirected, cacheEnabled)
                        .onSuccess { response ->
                            logcat { "successfully read from cache fro $url" }
                            return@suspendRunCatching response
                        }
                        .onFailure {
                            cache.deleteResponse("$url")
                            logcat(ERROR) { "failed to parse cache entry for $url ${it.message}" }
                        }
                }
            }

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

            logcat { "Trying to connect to ${url.host}:${url.port} - $url" }


            openNewConnection(url).use { conn ->
                val writeChannel = conn.openWriteChannel(true)
                val readChannel = conn.openReadChannel()

                writeChannel.writeString("${url}\r\n")

                val buffer = Buffer()

                readChannel.readRemaining().use {
                    it.readAvailable(buffer)
                }
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




