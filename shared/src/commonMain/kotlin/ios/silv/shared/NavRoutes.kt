package ios.silv.shared

import kotlinx.serialization.Serializable

@Serializable
expect sealed interface NavKey

sealed interface TopLevelDest: NavKey

sealed interface Screen: NavKey

@Serializable
data object GeminiHome: TopLevelDest

@Serializable
data class GeminiTab(
    val id: Long
): Screen

@Serializable
data object GeminiSettings: TopLevelDest

