package ios.silv.gemini

import android.os.Build
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.streams.inputStream
import io.ktor.utils.io.writeString
import ios.silv.core_android.log.LogPriority
import ios.silv.core_android.log.logcat
import ios.silv.gemini.GeminiStatus.Failure
import ios.silv.gemini.GeminiStatus.Input
import ios.silv.gemini.GeminiStatus.Redirect
import ios.silv.gemini.GeminiStatus.Success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.source
import java.io.InputStream
import java.nio.charset.Charset
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
const val CR = '\r'.code.toByte()
const val LF = '\n'.code.toByte()
private const val SPACE = 0x20

private val GeminiDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(2)

class GeminiClient(
    private val cache: GeminiCache
) {

    suspend fun makeGeminiQuery(query: GeminiQuery): Result<GeminiContent> {
        return runCatching {

            withContext(GeminiDispatcher) {

                val cached = cache.getResponse(url = query.url)

                logcat { "found from cache $query ${cached?.path}" }

                val buffer = okio.Buffer()
                val source: BufferedSource = cached?.inputStream()?.source()?.buffer()
                    ?: getResponseFromSocket(query).inputStream().source().buffer()

                source.use { s ->
                    s.read(buffer, Long.MAX_VALUE)
                }

                val cloned = buffer.clone().inputStream().source().buffer()

                val header = cloned.readUtf8Line()!!
                when (val status = transformStatus(header)) {
                    is Failure -> {
                        logcat { "received failure response" }
                        error(status.meta)
                    }
                    is Input -> TODO()
                    is Redirect -> TODO() // makeGeminiQuery(GeminiQuery(status.meta)).getOrThrow()
                    is Success -> {
                        try {
                            if (cached == null) {
                                cache.cacheResponse(query.url,  buffer.clone().inputStream().source())
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR) { "failed to write to cache $query ${e.stackTraceToString()}" }
                        }

                        parseResponse(
                            GeminiResponse(
                                query = query,
                                meta = status.meta,
                                content = cloned.inputStream()
                            )
                        )
                    }
                }.also {
                    source.close()
                }
            }
        }
    }

}

private fun transformStatus(response: String): GeminiStatus {
    // <STATUS><SPACE><META><CR><LF>
    val status = response.slice(0..1)

    val space = response.indexOf(' ')
    val meta = response.slice(space + 1..response.lastIndex)

    logcat("GeminiClient") { "received header $response $status $meta" }

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

private data class TextParams(
    val charset: Charset,
    val lang: String,
)

private fun getParams(meta: String): TextParams {

    var charset = Charset.forName("UTF-8")
    var lang = ""

    if (meta == "text/gemini") {
        return TextParams(
            charset,
            lang
        )
    }

    val paramList = meta.removePrefix("text/gemini;")
    val params = paramList.split(';')

    for (param in params) {
        val (name, value) = param.trim().split('=')
        when (name) {
            "lang" -> lang = value
            "charset" -> charset = Charset.forName(value)
            else -> Unit
        }
    }
    return TextParams(
        charset,
        lang
    )
}

private fun parseResponse(response: GeminiResponse): GeminiContent {
    val meta = response.meta
    when {
        meta.startsWith("text/gemini") -> {
            val (charset, lang) = getParams(meta)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                GeminiContent.Text(
                    String(response.content.readAllBytes(), charset),
                    lang,
                    response.query.url
                )
            } else {
                TODO("VERSION.SDK_INT < TIRAMISU")
            }
        }

        else -> error("unimplemented")
    }
}

private suspend fun getResponseFromSocket(query: GeminiQuery): Source = withContext(GeminiDispatcher) {

    if (query.url.encodeUtf8().size > GEMINI_URI_MAX_SIZE) {
        error("${query.url} was larger than max size $GEMINI_URI_MAX_SIZE")
    }

    val host = query.extractHostFromGeminiUrl()

    logcat { "Making request to ${host.getOrNull()} $GEMINI_PORT" }

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

            sendChannel.writeString("${query.url}\r\n")

            receiveChannel.readRemaining()
        }
}

private fun GeminiQuery.extractHostFromGeminiUrl(): Result<String> = runCatching {
    if (!url.startsWith(prefix = GEMINI_URI_PREFIX)) {
        error("Invalid URL $url Does not start with $GEMINI_URI_PREFIX")
    }

    val end = url.indexOf('/', startIndex = GEMINI_URI_PREFIX.length)
        .takeIf { i -> i != -1 }
        ?: url.length

    url.slice(GEMINI_URI_PREFIX.length..<end)
}

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

data class GeminiResponse(
    val query: GeminiQuery,
    val meta: String,
    val content: InputStream
)

sealed class GeminiContent {
    data class Text(val content: String, val lang: String, val parent: String = "") : GeminiContent()
}
