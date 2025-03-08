package ios.silv.gemclient

import android.os.Parcelable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeString
import ios.silv.gemclient.GeminiStatus.Failure
import ios.silv.gemclient.GeminiStatus.Input
import ios.silv.gemclient.GeminiStatus.Redirect
import ios.silv.gemclient.GeminiStatus.Success
import ios.silv.gemclient.log.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readLineStrict
import kotlinx.parcelize.Parcelize
import okio.ByteString.Companion.encodeUtf8
import java.nio.charset.Charset
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

@Parcelize
@JvmInline
value class GeminiQuery(
    val url: String
): Parcelable

private const val GEMINI_PORT = 1965
private const val GEMINI_URI_PREFIX = "gemini://"
private const val GEMINI_URI_MAX_SIZE = 1024
private const val GEMTEXT_LINK_PREFIX = "=>"
const val CR_LF = '\r'.code.toByte() + '\n'.code.toByte()
private const val SPACE = 0x20

private val GeminiDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(2)

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
    logcat("GeminiClient") { "parsing response meta: ${response.meta} content size: ${response.content.size}" }
    val meta = response.meta
    when {
        meta.startsWith("text/gemini") -> {
            val (charset, lang) = getParams(meta)
            return GeminiContent.Text(String(response.content, charset), lang, response.query.url)
        }

        else -> error("unimplemented")
    }
}

private suspend fun getResponseFromSocket(query: GeminiQuery): Source = withContext(GeminiDispatcher) {

    if (query.url.encodeUtf8().size > GEMINI_URI_MAX_SIZE) {
        error("${query.url} was larger than max size $GEMINI_URI_MAX_SIZE")
    }

    val host = query.extractHostFromGeminiUrl()

    logcat("GeminiClient") { "Making request to ${host.getOrNull()} $GEMINI_PORT" }

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

            sendChannel.writeString("${query}$CR_LF")

            receiveChannel.readRemaining()
        }
}

class GeminiClient(
    private val cache: GeminiCache
) {

    suspend fun makeGeminiQuery(query: GeminiQuery): Result<GeminiContent> {
        return runCatching {

            withContext(GeminiDispatcher) {

                val cached = cache.getResponse(url = query.url)

                val source: Source = cached?.inputStream()?.asSource()?.buffered()
                    ?: getResponseFromSocket(query).also {
                        cache.cacheResponse(query.url, it)
                    }


                when (val status = transformStatus(source.readLineStrict(1029))) {
                    is Failure -> error(status.meta)
                    is Input -> TODO()
                    is Redirect -> makeGeminiQuery(GeminiQuery(status.meta)).getOrThrow()
                    is Success -> parseResponse(
                        GeminiResponse(
                            query = query,
                            meta = status.meta,
                            content = source.readByteArray()
                        )
                    )
                }.also { source.close() }
            }
        }
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
    val content: ByteArray
)

sealed class GeminiContent {
    data class Text(val content: String, val lang: String, val parent: String = "") : GeminiContent()
}
