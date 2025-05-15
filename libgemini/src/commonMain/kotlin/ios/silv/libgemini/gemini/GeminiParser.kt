package ios.silv.libgemini.gemini

import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.forName
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

object GeminiParser {

    fun parse(query: String, res: Response): Flow<ContentNode> {
        return try {
           handleSuccess(query, res)
        } catch (e: Exception) {
            if (e is CancellationException) throw e

            flowOf(ContentNode.Error(e.message.orEmpty()))
        }
    }

    private fun handleSuccess(query: String, res: Response): Flow<ContentNode> {
        res.body.peek().use { src ->
            val (mediaType, params) = decodeMeta(res.meta).getOrThrow()
            val encoding = params.getOrElse("charset") { "utf-8" }

            val text = src.readText(Charsets.forName(encoding))

            return when {
                mediaType.startsWith("text") -> parseText(query, text)
                else -> TODO("Unimplemented Content Type")
            }
        }
    }
}

// #...[<whitespace>]<heading>
private val headingRegex = Regex("#+\\s+.*")

fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
    val base = Url(baseUrl)
    val candidate = relativeOrAbsolute.trim()

    return if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
        candidate
    } else {
        URLBuilder().apply {
            takeFrom(base)
            encodedPath = base.encodedPath.trimEnd('/') + "/" + candidate.trimStart('/')
        }.build().toString()
    }
}

fun isRelativeUrl(url: String): Boolean {
    return try {
        val parsed = Url(url.trim())
        parsed.protocol.name.isEmpty() || parsed.protocol.name == "<unknown>"
    } catch (e: Exception) {
        true
    }
}

private const val LINK_PREFIX = "=>"
private const val LIST_PREFIX = "* "
private const val QUOTE_PREFIX = ">"
private const val PREFORMAT_PREFIX = "```"

private fun parseText(query: String, body: String): Flow<ContentNode.Line> = flow {
    for (block in body.split("\n", "\r\n").filter { it.isNotBlank() }) {
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

            block.startsWith(LINK_PREFIX)-> {
                val split = block.removePrefix(LINK_PREFIX).trimStart().split(Regex("\\s+"), limit = 2)
                ContentNode.Line.Link(
                    text = block,
                    url = if (isRelativeUrl(split[0])) {
                        resolveUrl(query, split[0].trim())
                    } else {
                        split[0].trim()
                    },
                    name = split.getOrNull(1) ?: split[0]
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