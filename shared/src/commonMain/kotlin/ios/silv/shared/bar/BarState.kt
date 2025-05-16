package ios.silv.shared.bar

import ios.silv.shared.GeminiTab
import ios.silv.shared.types.StablePage
import ios.silv.shared.types.StableTab
import ios.silv.shared.ui.UiState

data class BarState(
    val activeTab: GeminiTab?,
    val showSearchbar: Boolean,
    val tabs: List<Triple<StableTab, StablePage?, String?>> = emptyList(),
    val query: String = "",
): UiState