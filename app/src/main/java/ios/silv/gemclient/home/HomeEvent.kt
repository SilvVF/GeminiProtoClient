package ios.silv.gemclient.home

import ios.silv.gemclient.ui.UiEvent

sealed interface HomeEvent : UiEvent {
    data object ToggleIncognito : HomeEvent
    data class CreateTab(val url: String?) : HomeEvent
}

