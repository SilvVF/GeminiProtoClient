package ios.silv.shared.bar

import ios.silv.shared.types.StableTab
import ios.silv.shared.ui.UiEvent

sealed interface BarEvent : UiEvent {
    data class SearchChanged(val query: String) : BarEvent
    data class ReorderTabs(val from: Int, val to: Int) : BarEvent
    data class GoToTab(val tab: StableTab) : BarEvent
    data class DeleteTab(val id: Long) : BarEvent
    data object CreateNewTab : BarEvent
    data object CreateBlankTab : BarEvent
    data class CreateNewPage(val tabId: Long) : BarEvent
    data object GoToHome: BarEvent
}
