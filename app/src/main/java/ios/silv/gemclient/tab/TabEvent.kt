package ios.silv.gemclient.tab

import ios.silv.gemclient.ui.UiEvent

sealed interface TabEvent : UiEvent {
    data class LoadPage(val link: String) : TabEvent
    data object GoBack : TabEvent
}
