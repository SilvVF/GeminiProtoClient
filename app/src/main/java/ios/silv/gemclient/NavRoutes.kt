package ios.silv.gemclient

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

sealed interface TopLevelDest

@Serializable
data object GeminiMain: TopLevelDest


@Serializable
data object GeminiHome

@Serializable
data class GeminiTab(
    val baseUrl: String,
    val id: Long? = null
)


@Serializable
data object GeminiSettings: TopLevelDest

