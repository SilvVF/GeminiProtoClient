package ios.silv.gemclient

import kotlinx.serialization.Serializable


sealed interface TopLevelDest

sealed interface Screen

@Serializable
data object GeminiMain: TopLevelDest

@Serializable
data object GeminiHome: Screen

@Serializable
data class GeminiTab(
    val id: Long
): Screen


@Serializable
data object GeminiSettings: TopLevelDest

