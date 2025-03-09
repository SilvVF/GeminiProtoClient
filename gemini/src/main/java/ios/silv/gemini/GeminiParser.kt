package ios.silv.gemini

import io.ktor.http.ContentType
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.charsets.forName
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import ios.silv.core_android.log.logcat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.io.readByteArray
import java.net.URI

object GeminiParser {

    suspend fun parse(query: String, res: Response): Flow<ContentNode> {
        return try {
            res.body.use { src ->
                val (mediaType, params) = decodeMeta(res.meta).getOrThrow()
                val encoding = params.getOrDefault("charset", "utf-8")

                val text = src.readText(Charsets.forName(encoding))

                return when {
                    mediaType.startsWith("text") -> parseText(query, text)
                    else -> TODO("Unimplemented Content Type")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            flowOf(ContentNode.Error(e.message.orEmpty()))
        }
    }
}

// #...[<whitespace>]<heading>
private val headingRegex = Regex("(?m)^#+[ \\t]+\\S.*$")

// [<whitespace>]<URL>[<whitespace><USER-FRIENDLY LINK NAME>]
private val linkRegex = """\s*([^\s\[]+)\s*(?:\[(.*)])?""".toRegex()

private fun parseLink(input: String): Pair<String, String>? {
    val match = linkRegex.find(input) ?: return null
    val (url, friendlyName) = match.destructured
    return url to friendlyName
}

private fun resolveUrl(baseUrl: String, url: String): String {
    val baseUri = URI(baseUrl)
    val resolvedUri = baseUri.resolve(url)
    return resolvedUri.toString()
}

private fun isRelativeUrl(url: String): Boolean {
    return try {
        val uri = URI(url)
        uri.scheme == null // If there's no scheme (http, https), it's relative
    } catch (e: Exception) {
        false // Invalid URLs are not considered relative
    }
}

private const val LINK_PREFIX = "=>"
private const val LIST_PREFIX = "* "
private const val QUOTE_PREFIX = ">"
private const val PREFORMAT_PREFIX = "```"

private fun parseText(query: String, body: String): Flow<ContentNode.Line> = flow {
    for (block in body.split("\r\n")) {
        val node = when {
            block.matches(headingRegex) -> ContentNode.Line.Heading(
                level = block.takeWhile { c -> c == '#' }.length,
                text = block,
                heading = run {
                    val whiteSpace = block.indexOf(' ')
                    if (block.lastIndex == whiteSpace) {
                        ""
                    } else {
                        block.slice(whiteSpace + 1..block.lastIndex)
                    }
                }
            )

            block.startsWith(LINK_PREFIX) &&
                    parseLink(block.drop(2)) != null -> {
                val (url, name) = parseLink(block.drop(2))!!
                ContentNode.Line.Link(
                    text = block,
                    url = if (isRelativeUrl(url)) {
                        resolveUrl(query, url)
                    } else {
                        url
                    },
                    name = name
                )
            }

            block.startsWith(LIST_PREFIX) -> ContentNode.Line.List(
                block,
                block.drop(LIST_PREFIX.length)
            )

            block.startsWith(QUOTE_PREFIX) -> ContentNode.Line.Quote(
                block,
                block.drop(QUOTE_PREFIX.length)
            )

            block.startsWith(PREFORMAT_PREFIX) -> ContentNode.Line.Preformat(
                block,
                block.drop(PREFORMAT_PREFIX.length)
            )

            else -> ContentNode.Line.Text(block)
        }
        emit(node)
    }
}


private fun decodeMeta(meta: String): Result<Pair<String, Map<String, String>>> {
    return runCatching {
        if (meta.isEmpty()) {
            "text/gemini" to emptyMap()
        } else {
            val contentType = ContentType.parse(meta)

            contentType.contentType to buildMap {
                for ((name, value, _) in contentType.parameters) {
                    put(name, value)
                }
            }
        }
    }
}