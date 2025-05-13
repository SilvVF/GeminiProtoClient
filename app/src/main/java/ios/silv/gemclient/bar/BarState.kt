package ios.silv.gemclient.bar

import androidx.compose.animation.core.MutableTransitionState
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.UiState
import java.io.File

data class BarState(
    val activeTab: GeminiTab?,
    val showSearchbar: Boolean,
    val tabs: List<Triple<StableTab, StablePage?, File?>> = emptyList(),
    val query: String = "",
): UiState