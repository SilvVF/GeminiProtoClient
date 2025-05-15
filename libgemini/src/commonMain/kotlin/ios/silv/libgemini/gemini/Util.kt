package ios.silv.libgemini.gemini
import ios.silv.core.logcat.logcat
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.readLineStrict

internal data class Header(
    val status: Int,
    val meta: String,
)

@Throws(IOException::class)
internal fun consumeHeader(source: Source): Result<Header> = runCatching {
    val line =  source.readLineStrict(4096)

    logcat("GeminiClient") { line }

    val fields = line.split(" ")

    if (fields.size < 2) {
        error("Header not formatted correctly")
    }

    val status = fields[0].toIntOrNull() ?: error("Unexpected status value ${fields[0]}")

    val meta = if (line.length <= 3) "" else line.substring(fields[0].length + 1)

    if (meta.length > MetaMaxLength) {
        error("Meta string is too long")
    }

    Header(status, meta)
}
