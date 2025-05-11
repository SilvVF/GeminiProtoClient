package ios.silv.gemclient.bar

import androidx.compose.animation.core.MutableTransitionState
import ios.silv.gemclient.BarMode
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.ui.UiState
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab

data class BarState(
    val activeTab: GeminiTab?,
    val showSearchbar: Boolean,
    val tabs: List<Pair<Tab, Page?>> = emptyList(),
    val query: String = "",
    val barMode: MutableTransitionState<BarMode>,
): UiState