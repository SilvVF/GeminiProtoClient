package ios.silv.gemini

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.set
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.addKeyStore
import io.ktor.network.tls.tls
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.cancel
import io.ktor.utils.io.core.copy
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.core_android.log.LogPriority
import ios.silv.core_android.log.LogPriority.*
import ios.silv.core_android.log.logcat
import ios.silv.core_android.suspendRunCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlin.coroutines.CoroutineContext


internal const val URLMaxLength = 1024
internal const val MetaMaxLength = 1024


// Gemini status codes as defined in the Gemini spec Appendix 1.
object GeminiCode {
    const val StatusInput = 10
    const val StatusSensitiveInput = 11

    const val StatusSuccess = 20

    const val StatusRedirect = 30
    const val StatusRedirectTemporary = 30
    const val StatusRedirectPermanent = 31

    const val StatusTemporaryFailure = 40
    const val StatusUnavailable = 41
    const val StatusCGIError = 42
    const val StatusProxyError = 43
    const val StatusSlowDown = 44

    const val StatusPermanentFailure = 50
    const val StatusNotFound = 51
    const val StatusGone = 52
    const val StatusProxyRequestRefused = 53
    const val StatusBadRequest = 59

    const val StatusClientCertificateRequired = 60
    const val StatusCertificateNotAuthorised = 61
    const val StatusCertificateNotValid = 62

    fun statusText(code: Int): String? = statusText[code]

    private val statusText = mapOf(
        StatusInput to "Input",
        StatusSensitiveInput to "Sensitive Input",

        StatusSuccess to "Success",

        // StatusRedirect to        "Redirect - Temporary"
        StatusRedirectTemporary to "Redirect - Temporary",
        StatusRedirectPermanent to "Redirect - Permanent",

        StatusTemporaryFailure to "Temporary Failure",
        StatusUnavailable to "Server Unavailable",
        StatusCGIError to "CGI Error",
        StatusProxyError to "Proxy Error",
        StatusSlowDown to "Slow Down",

        StatusPermanentFailure to "Permanent Failure",
        StatusNotFound to "Not Found",
        StatusGone to "Gone",
        StatusProxyRequestRefused to "Proxy Request Refused",
        StatusBadRequest to "Bad Request",

        StatusClientCertificateRequired to "Client Certificate Required",
        StatusCertificateNotAuthorised to "Certificate Not Authorised",
        StatusCertificateNotValid to "Certificate Not Valid",
    )
}

data class Response(
    val status: Int,
    val meta: String,
    val body: Source,
    val cert: X509Certificate? = null
)

data class Header(
    val status: Int,
    val meta: String,
)


class GeminiClient(
    private val cache: GeminiCache
) {
    data class Conn(
        val sock: Socket,
        val writeChannel: ByteWriteChannel,
        val readChannel: ByteReadChannel,
    )

    private val geminiDispatcher: CoroutineContext = Dispatchers.IO
    private val selector = ActorSelectorManager(geminiDispatcher + SupervisorJob())

    private val conns = mutableMapOf<String, Conn>()
    private val mutex = Mutex()

    suspend fun makeGeminiQuery(query: String): Result<Response> {
        return withContext(geminiDispatcher) { fetch(query) }
    }

    private suspend fun fetch(rawUrl: String): Result<Response> {
        return suspendRunCatching {
            fetchWithHostAndCert(Url(rawUrl), byteArrayOf(), byteArrayOf()).getOrThrow()
        }
    }

    private suspend fun readResponseFromCache(file: File): Result<Response> {
        return suspendRunCatching {
            val rc = file.inputStream().toByteReadChannel(geminiDispatcher)
            val header = getHeader(rc).getOrThrow()

            // need to suck the shit up and cpy so
            // sock closing doesn't cancel channel when
            // the parser is parsing
            val body = rc.readRemaining().copy()
            rc.cancel()

            Response(
                status = header.status,
                meta = header.meta,
                body = body,
                cert = null
            )
        }
    }

    private suspend fun openNewConnection(url: Url, port: Int, keystore: KeyStore?): Conn {
        val socket = aSocket(selector).tcp().connect(
            url.host,
            port,
        )
            .tls(Dispatchers.IO) {
                random = SecureRandom()
                if (keystore != null) {
                    addKeyStore(keystore, null, "certificate")
                } else {
                    trustManager = SslSettings.getTrustManager()
                }
            }

        return Conn(
            socket,
            socket.openWriteChannel(true),
            socket.openReadChannel()
        ).also {
            conns["${url.host}:$port"] = it
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun fetchWithHostAndCert(
        url: Url, certPEM: ByteArray, keyPEM: ByteArray
    ): Result<Response> {
        return suspendRunCatching {
            val cached = cache.getResponse(url.toString())
            if (cached != null) {
                logcat { "Found entry in cache for $url" }
                readResponseFromCache(cached).onSuccess { response ->
                    return@suspendRunCatching response
                }.onFailure {
                    cache.deleteResponse("$url")
                    logcat(ERROR) { "failed to parse cache entry for $url ${it.message}" }
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
            val port = urlBuilder.build().port

            val keystore = buildTlsCertificateIfNeeded(certPEM, keyPEM).getOrNull()

            logcat { "Trying to connect to ${url.host} $port - $url" }

            val conn = mutex.withLock {
                val key = "${url.host}:$port"
                val open = conns[key]

                if (open != null && !open.sock.isClosed) {
                    open
                } else {
                    open?.sock?.close()
                    conns.remove(key)
                    openNewConnection(url, port, keystore)
                }
            }

            conn.writeChannel.writeString("${url}\r\n")

            val rc = conn.readChannel
            val header = getHeader(rc).getOrThrow()

            // need to suck the shit up and cpy so
            // sock closing doesn't cancel channel when
            // the parser is parsing
            val source = rc.readRemaining()
            val body = source.copy()

            if (header.status == GeminiCode.StatusSuccess) {
                cache.cacheResponse(
                    url = url.toString(),
                    header = "${header.status} ${header.meta}\r\n",
                    source = source.copy()
                )
            }

            Response(
                status = header.status,
                meta = header.meta,
                body = body,
                cert = null
            )
        }
    }
}




