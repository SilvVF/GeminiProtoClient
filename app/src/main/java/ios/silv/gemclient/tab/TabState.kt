package ios.silv.gemclient.tab

import ios.silv.gemclient.ui.UiState
import ios.silv.sqldelight.Page

sealed interface TabState: UiState {
    data object Idle : TabState
    data object Error : TabState
    data object NoPages : TabState
    data class Loaded(val page: Page) : TabState
}