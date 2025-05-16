package ios.silv.shared.tab

import ios.silv.shared.ui.UiEvent


sealed interface TabEvent : UiEvent {
    data class LoadPage(val link: String) : TabEvent
    data object GoBack : TabEvent
}
