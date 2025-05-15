package ios.silv.gemclient.tab

import ios.silv.gemclient.types.StablePage
import ios.silv.shared.ui.UiState

sealed interface TabState: UiState {
    data object Idle : TabState
    data object Error : TabState
    data object NoPages : TabState
    data class Loaded(val page: StablePage) : TabState
}