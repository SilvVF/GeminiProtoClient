package ios.silv.gemclient

import kotlinx.serialization.Serializable

@Serializable
data class GeminiTab(
    val baseUrl: String,
    val id: Long? = null
)