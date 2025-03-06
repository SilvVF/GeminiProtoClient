package ios.silv.gemclient

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readText
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeString
import ios.silv.gemclient.GeminiStatus.Failure
import ios.silv.gemclient.GeminiStatus.Input
import ios.silv.gemclient.GeminiStatus.Redirect
import ios.silv.gemclient.GeminiStatus.Success
import ios.silv.gemclient.log.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

@JvmInline
value class GeminiQuery(
    val url: String
)

private const val GEMINI_PORT = 1965
private const val GEMINI_URI_PREFIX = "gemini://"
private const val GEMINI_URI_MAX_SIZE = 1024
private const val GEMTEXT_LINK_PREFIX = "=>"
private const val CR_LF = "\r\n"
private const val SPACE = 0x20

private val GeminiDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(2)

sealed class GeminiStatus(
    open val code: Int,
    open val meta: String
) {
    data class Input(override val code: Int, override val meta: String) : GeminiStatus(code, meta)
    data class Success(override val code: Int, override val meta: String) : GeminiStatus(code, meta)
    data class Redirect(override val code: Int, override val meta: String) :
        GeminiStatus(code, meta)

    sealed class Failure(override val code: Int, override val meta: String) :
        GeminiStatus(code, meta) {
        data class TempFailure(override val code: Int, override val meta: String) :
            Failure(code, meta)

        data class PermFailure(override val code: Int, override val meta: String) :
            Failure(code, meta)

        data class ClientCertReq(override val code: Int, override val meta: String) :
            Failure(code, meta)
    }
}

private fun transformStatus(response: String): GeminiStatus {
    // <STATUS><SPACE><META><CR><LF>
    val status = response.slice(0..1)
    val meta = response.slice(response.indexOf(Char(SPACE)) + 1..<response.lastIndexOf(CR_LF))

    return when (val code = status.toInt()) {
        in 10..19 -> Input(code, meta)
        in 20..29 -> Success(code, meta)
        in 30..39 -> Redirect(code, meta)
        in 40..49 -> Failure.TempFailure(code, meta)
        in 50..59 -> Failure.PermFailure(code, meta)
        in 60..69 -> Failure.ClientCertReq(code, meta)
        else -> error("Invalid status code received")
    }
}

suspend fun makeGeminiQuery(query: GeminiQuery): Result<String> {

    val host = query.extractHostFromGeminiUrl()

    if (!host.isSuccess) {
        return host
    }

    return runCatching {
        if (query.url.encodeUtf8().size > GEMINI_URI_MAX_SIZE) {
            error("${query.url} was larger than max size $GEMINI_URI_MAX_SIZE")
        }

        logcat("GeminiClient") { "Making request to ${host.getOrNull()} $GEMINI_PORT" }

        withContext(GeminiDispatcher) {
            aSocket(SelectorManager(GeminiDispatcher))
                .tcp()
                .connect(hostname = host.getOrThrow(), port = GEMINI_PORT)
                .tls(Dispatchers.IO) {
                    random = SecureRandom()
                    trustManager = SslSettings.getTrustManager()
                }
                .use { sock ->
                    val sendChannel = sock.openWriteChannel(autoFlush = true)
                    val receiveChannel = sock.openReadChannel()

                    sendChannel.writeString("${query.url}$CR_LF")

                    when (val status = transformStatus(receiveChannel.readUTF8Line(1029)!!)) {
                        is Failure -> error(status.meta)
                        is Input -> TODO()
                        is Redirect -> TODO()
                        is Success -> receiveChannel.readRemaining().readText()
                    }
                }
        }
    }
}

fun GeminiQuery.extractHostFromGeminiUrl(): Result<String> = runCatching {
    if (!url.startsWith(prefix = GEMINI_URI_PREFIX)) {
        error("Invalid URL $url Does not start with $GEMINI_URI_PREFIX")
    }

    val end = url.indexOf('/', startIndex = GEMINI_URI_PREFIX.length)
        .takeIf { i -> i != -1 }
        ?: url.length

    url.slice(GEMINI_URI_PREFIX.length..<end)
}

