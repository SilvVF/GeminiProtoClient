package ios.silv.libgemini.gemini

import java.net.URI

actual fun resolveUrl(baseUrl: String, url: String): String {
    val baseUri = URI(baseUrl)
    val resolvedUri = baseUri.resolve(url)
    return resolvedUri.toString()
}

actual fun isRelativeUrl(url: String): Boolean {
    return try {
        val uri = URI(url)
        uri.scheme == null // If there's no scheme (http, https), it's relative
    } catch (e: Exception) {
        false // Invalid URLs are not considered relative
    }
}