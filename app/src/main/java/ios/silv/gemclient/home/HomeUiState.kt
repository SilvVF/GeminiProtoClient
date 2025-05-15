package ios.silv.gemclient.home

import ios.silv.gemclient.ui.UiState

data class HomeUiState(
    val incognito: Boolean = false,
    val recentlyViewed: List<String>,
    val bookmarked: List<String>
) : UiState