package ios.silv.gemclient.bar

import ios.silv.gemclient.BarMode
import ios.silv.gemclient.ui.UiEvent
import ios.silv.sqldelight.Tab

sealed interface BarEvent : UiEvent {
    data class SearchChanged(val query: String) : BarEvent
    data class ReorderTabs(val from: Int, val to: Int) : BarEvent
    data class GoToTab(val tab: Tab) : BarEvent
    data class DeleteTab(val id: Long) : BarEvent
    data object CreateNewTab : BarEvent
    data object CreateBlankTab : BarEvent
    data class CreateNewPage(val tabId: Long) : BarEvent
}
