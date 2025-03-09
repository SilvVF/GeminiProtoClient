package ios.silv.gemini

import android.annotation.SuppressLint
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.protocolWithAuthority
import io.ktor.http.set
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.addKeyStore
import io.ktor.network.tls.tls
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.core.copy
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.core_android.log.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import java.security.SecureRandom
import java.security.cert.X509Certificate
import kotlin.coroutines.CoroutineContext
import kotlin.math.log


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
    val meta: String
)


class GeminiClient(
    private val cache: GeminiCache
) {

    private val geminiDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(2)

    suspend fun makeGeminiQuery(query: String): Result<Response> {
        return withContext(geminiDispatcher) { fetch(query) }
    }

    private suspend fun fetch(rawUrl: String): Result<Response> {
        return runCatching {
            fetchWithHostAndCert(Url(rawUrl), byteArrayOf(), byteArrayOf()).getOrThrow()
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun fetchWithHostAndCert(
        url: Url, certPEM: ByteArray, keyPEM: ByteArray
    ): Result<Response> {
        return runCatching {

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

            val keystore = buildTlsCertificateIfNeeded(certPEM, keyPEM).getOrNull()

            aSocket(SelectorManager(Dispatchers.IO)).tcp().connect(
                url.host,
                urlBuilder.build().port,
            )
                .tls(Dispatchers.IO) {
                    random = SecureRandom()
                    if (keystore != null) {
                        addKeyStore(keystore, null, "certificate")
                    } else {
                        trustManager = SslSettings.getTrustManager()
                    }
                }
                .use { conn ->
                    conn.openWriteChannel().use {
                        writeString("${url}\r\n")
                    }

                    val rc = conn.openReadChannel()
                    val header = getHeader(rc).getOrThrow()

                    // need to suck the shit up and cpy so
                    // sock closing doesn't cancel channel when
                    // the parser is parsing
                    val body = rc.readRemaining().copy()

                    Response(
                        status = header.status,
                        meta = header.meta,
                        body = body,
                        cert = null
                    )
                }
        }
    }
}




