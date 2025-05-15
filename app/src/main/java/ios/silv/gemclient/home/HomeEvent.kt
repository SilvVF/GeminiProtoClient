package ios.silv.gemclient.home

import ios.silv.shared.ui.UiEvent


sealed interface HomeEvent : UiEvent {
    data object ToggleIncognito : HomeEvent
    data class CreateTab(val url: String?) : HomeEvent
}

