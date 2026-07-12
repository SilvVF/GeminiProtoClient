package ios.silv.libgemini.gemini

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