package ios.silv.gemclient.bar

import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.UiState

data class BarState(
    val activeTab: GeminiTab?,
    val showSearchbar: Boolean,
    val tabs: List<Triple<StableTab, StablePage?, String?>> = emptyList(),
    val query: String = "",
): UiState