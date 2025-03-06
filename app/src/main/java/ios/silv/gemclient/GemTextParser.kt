package ios.silv.gemclient

import ios.silv.gemclient.ContentNode.*


/*
Text lines
Link lines
Heading lines
List items
Quote lines
Preformat toggle lines
 */

sealed interface ContentNode {

    data class Error(val message: String): ContentNode

    sealed class Line(val raw: String): ContentNode {
        data class Text(val text: String): Line (text)
        data class Link(val text: String, val url: String, val name: String): Line(text)
        data class Heading(val text: String, val level: Int, val heading: String): Line(text)
        data class List(val text: String, val line: String): Line(text)
        data class Quote(val text: String, val line: String): Line(text)
        data class Preformat(val text: String, val block: String): Line(text)
    }
}


private const val LINK_PREFIX = "=>"
private const val HEADING_PREFIX = '#'
private const val LIST_PREFIX = "* "
private const val QUOTE_PREFIX = ">"
private const val PREFORMAT_PREFIX = "```"

fun resolveUrl(url: String, parent: String): String = when {
    parent.isBlank() -> url
    url.contains("://") -> url
    else -> {
        val rel = url.removePrefix("/")

        if (parent.endsWith('/')) {
            "$parent$rel"
        } else {
            "$parent/$rel"
        }
    }
}

fun parseText(text: GeminiContent.Text): List<Line> {
    return text.content.lines().map { line ->
        when {
            line.startsWith(LINK_PREFIX) -> {
                // =>[<whitespace>]<URL>[<whitespace><USER-FRIENDLY LINK NAME>]
                val urlText = line.drop(LINK_PREFIX.length)
                        .dropWhile { c -> c.isWhitespace() }
                        .takeWhile { c -> !c.isWhitespace() }

                Line.Link(
                    text = line,
                    url = resolveUrl(urlText, text.parent),
                    name = line.takeLastWhile { c -> !c.isWhitespace() }
                )
            }
            line.startsWith(HEADING_PREFIX) -> {
                // #...[<whitespace>]<heading>
                val level = line.takeWhile { c -> c == HEADING_PREFIX }.length

                if (level == line.length) {
                    Line.Heading(
                        text = line,
                        level = level,
                        heading = ""
                    )
                }

                if (!line[level].isWhitespace()) {
                    Line.Text(line)
                } else {
                    val heading = line
                        .slice(level..line.lastIndex)
                        .trimStart()

                    Line.Heading(
                        text = line,
                        level = level,
                        heading = heading
                    )
                }
            }
            line.startsWith(LIST_PREFIX) -> {
                Line.List(line, line.removePrefix(LIST_PREFIX))
            }
            line.startsWith(QUOTE_PREFIX) -> {
                Line.Quote(line, line.removePrefix(QUOTE_PREFIX))
            }
            line.startsWith(PREFORMAT_PREFIX) -> Line.Preformat(line, line.removePrefix(PREFORMAT_PREFIX))
            else -> Line.Text(line)
        }
    }
}