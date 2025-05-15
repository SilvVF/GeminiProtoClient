package ios.silv.libgemini.gemini


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
        data class Text(val text: String): Line(text)
        data class Link(val text: String, val url: String, val name: String): Line(text)
        data class Heading(val text: String, val level: Int, val heading: String): Line(text)
        data class List(val text: String, val line: String): Line(text)
        data class Quote(val text: String, val line: String): Line(text)
        data class Preformat(val text: String, val block: String): Line(text)
    }
}
