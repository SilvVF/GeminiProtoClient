package ios.silv.libgemini.gemini

import io.ktor.http.ContentType


/*
Text lines
Link lines
Heading lines
List items
Quote lines
Preformat toggle lines
 */

sealed interface ContentNode {

    data class Other(val contentType: String, val raw: ByteArray): ContentNode

    data class Image(val type: String, val raw: ByteArray): ContentNode {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Image

            if (type != other.type) return false
            if (!raw.contentEquals(other.raw)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + raw.contentHashCode()
            return result
        }
    }

    data class Error(val status: Int, val statusText: String, val message: String): ContentNode

    data class InternalError(val message: String): ContentNode

    sealed class Line(val raw: String): ContentNode {
        data class Text(val text: String): Line(text)
        data class Link(val text: String, val url: String, val name: String): Line(text)
        data class Heading(val text: String, val level: Int, val heading: String): Line(text)
        data class List(val text: String, val line: String): Line(text)
        data class Quote(val text: String, val line: String): Line(text)
        data class Preformat(val text: String, val block: String): Line(text)
    }
}
