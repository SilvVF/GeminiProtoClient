package ios.silv.libgemini.gemini

import platform.Foundation.NSURL

actual fun resolveUrl(baseUrl: String, url: String): String {
    val base = NSURL.URLWithString(baseUrl)
        ?: error("Invalid base URL: $baseUrl")

    val resolved = NSURL.URLWithString(url, relativeToURL = base)
        ?: error("Invalid URL: $url")

    return requireNotNull(resolved.absoluteURL?.absoluteString)
}

actual fun isRelativeUrl(url: String): Boolean {
    return try {
        val uri = NSURL.URLWithString(url) ?: return false
        uri.scheme == null
    } catch (e: Exception) {
        false
    }
}