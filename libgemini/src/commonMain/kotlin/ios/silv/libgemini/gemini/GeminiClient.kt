package ios.silv.libgemini.gemini

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.network.selector.SelectorManager
import ios.silv.logcat.LogPriority.ERROR
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineDispatcher
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

internal const val URLMaxLength = 1024
internal const val MetaMaxLength = 1024

internal const val CACHE_SIZE_BYTES = 1024 * 1024 * 150L

class TooManyRedirectsException(val max: Int) : IOException("redirected more than $max times")
class RedirectLoopException(val url: Url) : IOException("redirect loop at $url")
class UnsupportedSchemeException(val url: Url) :
    IOException("unsupported protocol ${url.protocol.name}")

class UrlTooLongException(val length: Int) : IOException("url is too long $length > $URLMaxLength")

class Response(
    val url: Url,
    val finalUrl: Url,
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

private fun consumeHeader(line: String): Header {
    val status = line.take(2).toIntOrNull() ?: error("Unexpected status value in header")
    if (line.length > 2 && line[2] != ' ') {
        error("Header not formatted correctly")
    }

    val meta = line.drop(3)

    if (meta.encodeToByteArray().size > MetaMaxLength) {
        error("Meta string is too long")
    }

    return Header(status, meta)
}

@Throws(IOException::class)
internal fun consumeHeaderOkio(source: okio.BufferedSource): Result<Header> = runCatching {
    val line = source.readUtf8LineStrict(4096)
    consumeHeader(line)
}

@Throws(IOException::class)
internal fun consumeHeaderKotlinIO(source: Source): Result<Header> = runCatching {
    val line = source.readLineStrict(4096)
    consumeHeader(line)
}

data class ClientConfig(
    val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    val selector: SelectorManager = SelectorManager(dispatcher + SupervisorJob()),
    val maxRedirects: Int = 5
)


internal expect fun createSocket(
    selector: SelectorManager,
    url: Url,
    store: DataStore<Preferences>
): SocketFactory

interface SecureSocket : AutoCloseable {
    suspend fun readUntilEOF(buffer: Buffer): Int
    suspend fun write(data: String)
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

    private sealed interface Hop {
        data class Done(val response: Response) : Hop
        data class Redirect(val target: Url) : Hop
    }

    private fun normalize(input: Url): Url = URLBuilder(input).apply {
        fragment = ""
        user = null
        password = null
        if (input.port == 0) port = 1965
    }.build()

    private fun resolve(base: Url, ref: String): Url =
        normalize(URLBuilder(base).takeFrom(ref.trim()).build())

    private suspend fun openNewConnection(
        url: Url,
    ): SecureSocket = createSocket(selector, url, store).connect(url)

    suspend fun makeGeminiQuery(
        query: String,
        forceNetwork: Boolean = false,
        cacheEnabled: Boolean = true
    ): Result<Response> = suspendRunCatching {
        // Url(query) can throw, so it must be inside the runCatching
        val origin = normalize(Url(query))
        withContext(config.dispatcher) {
            follow(origin, forceNetwork, cacheEnabled)
        }
    }

    /** Iterative redirect loop: one hop at a time, one open socket at a time. */
    private suspend fun follow(
        origin: Url,
        forceNetwork: Boolean,
        cacheEnabled: Boolean
    ): Response {
        val seen = mutableSetOf<String>()
        var current = origin

        // first request + up to maxRedirects redirects
        repeat(config.maxRedirects + 1) {
            if (!seen.add(current.toString())) throw RedirectLoopException(current)

            when (val hop = fetchOnce(origin, current, forceNetwork, cacheEnabled)) {
                is Hop.Done -> return hop.response
                is Hop.Redirect -> current = hop.target
            }
        }
        throw TooManyRedirectsException(config.maxRedirects)
    }

    private suspend fun fetchOnce(
        origin: Url,
        url: Url,
        forceNetwork: Boolean,
        cacheEnabled: Boolean
    ): Hop {
        val urlLength = url.toString().length
        if (urlLength > URLMaxLength) throw UrlTooLongException(urlLength)
        if (url.protocol.name != "gemini") throw UnsupportedSchemeException(url)

        if (!forceNetwork && cacheEnabled) {
            logcat { "reading from cache \n\t origin: $origin \n\t url: $url \n\t" }
            readFromCache(origin, url)?.let { return it }
        }

        logcat { "Trying to connect to ${url.host}:${url.port} - $url" }

        val (header, buffer) = openNewConnection(url).use { conn ->
            conn.write("$url\r\n")
            val buffer = Buffer()
            conn.readUntilEOF(buffer)
            consumeHeaderKotlinIO(buffer).getOrThrow() to buffer
        }

        return when (header.status) {
            GeminiCode.REDIRECT_PERMANENT,
            GeminiCode.REDIRECT_TEMPORARY -> {
                val target = resolve(url, header.meta)

                if (target.protocol.name != "gemini") {
                    // Don't swallow cross-protocol redirects: hand the target to the UI
                    // (open externally / ask the user) as a normal 3x response.
                    return Hop.Done(
                        Response(
                            origin,
                            url,
                            header.status,
                            target.toString(),
                            Buffer()
                        )
                    )
                }

                if (cacheEnabled && header.status == GeminiCode.REDIRECT_PERMANENT) {
                    cache.cacheResponse(
                        url = url.toString(),
                        header = "${header.status} ${header.meta}\r\n",
                        source = Buffer()
                    )
                }
                Hop.Redirect(target)
            }

            else -> {
                if (cacheEnabled && header.status == GeminiCode.SUCCESS) {
                    cache.cacheResponse(
                        url = url.toString(),
                        header = "${header.status} ${header.meta}\r\n",
                        source = buffer.copy()
                    )
                }
                Hop.Done(Response(origin, url, header.status, header.meta, buffer))
            }
        }
    }

    /** Returns null on miss or on a corrupt entry (which is deleted). Never recurses into fetch. */
    private suspend fun readFromCache(origin: Url, url: Url): Hop? {
        val path = cache.getResponse(url.toString()) ?: return null

        return suspendRunCatching {
            fileSystem.source(path).buffer().use { source ->
                val header = consumeHeaderOkio(source).getOrThrow()

                if (header.status == GeminiCode.REDIRECT_PERMANENT) {
                    return@use Hop.Redirect(resolve(url, header.meta))
                }

                // an empty body is a valid response (empty gemtext), don't treat it as corrupt
                val body = Buffer().apply {
                    write(source.readByteArray())
                }
                Hop.Done(
                    Response(
                        url = origin,
                        finalUrl = url,
                        header.status,
                        header.meta, body
                    )
                )
            }
        }
            .onFailure {
                logcat(ERROR) { "failed to read cache entry for $url ${it.asLog()}" }
                cache.deleteResponse(url.toString())
            }
            .getOrNull()
    }
}




