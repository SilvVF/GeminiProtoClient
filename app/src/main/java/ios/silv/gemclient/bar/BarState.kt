package ios.silv.gemclient.bar

import ios.silv.shared.GeminiTab
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.shared.ui.UiState

data class BarState(
    val activeTab: GeminiTab?,
    val showSearchbar: Boolean,
    val tabs: List<Triple<StableTab, StablePage?, String?>> = emptyList(),
    val query: String = "",
): UiState