package ios.silv.gemini

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.set
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.addKeyStore
import io.ktor.network.tls.tls
import io.ktor.utils.io.core.readAvailable
import io.ktor.utils.io.core.remaining
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.core.logcat.LogPriority
import ios.silv.core.logcat.LogPriority.*
import ios.silv.core.logcat.logcat
import ios.silv.core.suspendRunCatching
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.Closeable
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal const val URLMaxLength = 1024
internal const val MetaMaxLength = 1024
internal val SOCKET_CLOSE_TIMEOUT = 10.seconds

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
) : Closeable {

    override fun close() {
        body.close()
    }
}

data class Header(
    val status: Int,
    val meta: String,
)

data class ClientConfig(
    val dispatcher: CoroutineContext =
        Dispatchers.IO +
                SupervisorJob() +
                CoroutineName("GeminiClientScope"),
    val selector: SelectorManager = SelectorManager(dispatcher),
    val maxRedirects: Int = 5
)

class GeminiClient(
    private val cache: GeminiCache,
    private val config: ClientConfig = ClientConfig()
) {
    private val selector = config.selector

    suspend fun makeGeminiQuery(query: String, forceNetwork: Boolean = false): Result<Response> {
        return withContext(config.dispatcher) { fetch(query, forceNetwork) }
    }

    private suspend fun fetch(rawUrl: String, forceNetwork: Boolean = false): Result<Response> {
        return suspendRunCatching {
            fetchWithHostAndCert(
                Url(rawUrl),
                byteArrayOf(),
                byteArrayOf(),
                forceNetwork,
                0
            ).getOrThrow()
        }
    }

    private suspend fun readResponseFromCache(file: File, redirected: Int = 0): Result<Response> {
        return suspendRunCatching {
            file.inputStream().asSource().buffered().use { source ->
                val header = consumeHeader(source).getOrThrow()

                if (header.status == GeminiCode.StatusRedirectPermanent) {
                    return@suspendRunCatching fetchWithHostAndCert(
                        Url(header.meta),
                        byteArrayOf(),
                        byteArrayOf(),
                        false,
                        (redirected + 1)
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
                    body = buffer,
                    cert = null
                )
            }
        }
    }

    private suspend fun openNewConnection(url: Url, port: Int, keystore: KeyStore?): Socket {
        return aSocket(selector).tcp().connect(
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
    }

    private suspend fun fetchWithHostAndCert(
        url: Url,
        certPEM: ByteArray,
        keyPEM: ByteArray,
        forceNetwork: Boolean,
        redirected: Int = 0
    ): Result<Response> {
        return suspendRunCatching {

            if (!forceNetwork) {
                cache.getResponse(url.toString())?.let { cached ->
                    logcat { "Found entry in cache for $url" }
                    readResponseFromCache(cached, redirected)
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
            val port = urlBuilder.build().port

            val keystore = buildTlsCertificateIfNeeded(certPEM, keyPEM).getOrNull()

            logcat { "Trying to connect to ${url.host} $port - $url" }

            openNewConnection(url, port, keystore).use { conn ->
                val writeChannel = conn.openWriteChannel(true)
                val readChannel = conn.openReadChannel()

                writeChannel.writeString("${url}\r\n")

                val buffer = Buffer()

                readChannel.readRemaining().use {
                    it.readAvailable(buffer)
                }
                val header = consumeHeader(buffer).getOrThrow()

                when (header.status) {
                    GeminiCode.StatusRedirectPermanent,
                    GeminiCode.StatusRedirectTemporary -> {
                        handleRedirect(
                            header, redirected, url, certPEM, keyPEM, forceNetwork
                        )
                    }
                    else -> {
                        if (header.status == GeminiCode.StatusSuccess) {
                            cache.cacheResponse(
                                url = url.toString(),
                                header = "${header.status} ${header.meta}\r\n",
                                source = buffer.copy()
                            )
                        }

                        Response(
                            status = header.status,
                            meta = header.meta,
                            body = buffer,
                            cert = null
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
        certPEM: ByteArray,
        keyPEM: ByteArray,
        forceNetwork: Boolean,
    ): Response {
        if (redirected > config.maxRedirects) {
            error("client was redirected $redirected times > max ${config.maxRedirects}")
        }

        if (header.status == GeminiCode.StatusRedirectPermanent) {
            cache.cacheResponse(
                url = url.toString(),
                header = "${header.status} ${header.meta}\r\n",
                source = Buffer()
            )
        }

        return fetchWithHostAndCert(
            Url(header.meta), certPEM, keyPEM,
            forceNetwork,
            (redirected + 1)
        )
            .getOrThrow()
    }
}




