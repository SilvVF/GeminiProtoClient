package ios.silv.libgemini.gemini

// Gemini status codes as defined in the Gemini spec Appendix 1.
@Suppress("Unused", "MemberVisibilityCanBePrivate")
object GeminiCode {
    const val INPUT = 10
    const val SENSITIVE_INPUT = 11

    const val SUCCESS = 20

    const val REDIRECT = 30
    const val REDIRECT_TEMPORARY = 30
    const val REDIRECT_PERMANENT = 31

    const val TEMPORARY_FAILURE = 40
    const val UNAVAILABLE = 41
    const val CGI_ERROR = 42
    const val PROXY_ERROR = 43
    const val SLOW_DOWN = 44

    const val PERMANENT_FAILURE = 50
    const val NOT_FOUND = 51
    const val GONE = 52
    const val PROXY_REQUEST_REFUSED = 53
    const val BAD_REQUEST = 59

    const val CLIENT_CERTIFICATE_REQUIRED = 60
    const val CERTIFICATE_NOT_AUTHORISED = 61
    const val CERTIFICATE_NOT_VALID = 62

    fun statusText(code: Int): String? = statusToText[code]

    val statusToText = mapOf(
        INPUT to "Input",
        SENSITIVE_INPUT to "Sensitive Input",

        SUCCESS to "Success",

        REDIRECT_TEMPORARY to "Redirect - Temporary",
        REDIRECT_PERMANENT to "Redirect - Permanent",

        TEMPORARY_FAILURE to "Temporary Failure",
        UNAVAILABLE to "Server Unavailable",
        CGI_ERROR to "CGI Error",
        PROXY_ERROR to "Proxy Error",
        SLOW_DOWN to "Slow Down",

        PERMANENT_FAILURE to "Permanent Failure",
        NOT_FOUND to "Not Found",
        GONE to "Gone",
        PROXY_REQUEST_REFUSED to "Proxy Request Refused",
        BAD_REQUEST to "Bad Request",

        CLIENT_CERTIFICATE_REQUIRED to "Client Certificate Required",
        CERTIFICATE_NOT_AUTHORISED to "Certificate Not Authorised",
        CERTIFICATE_NOT_VALID to "Certificate Not Valid",
    )
}