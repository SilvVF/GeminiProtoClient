package ios.silv.gemclient

import kotlinx.serialization.Serializable


sealed interface TopLevelDest

sealed interface Screen

@Serializable
data object GeminiHome: TopLevelDest

@Serializable
data class GeminiTab(
    val id: Long
): Screen


@Serializable
data object GeminiSettings: TopLevelDest

