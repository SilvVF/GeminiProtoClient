package ios.silv.gemclient.tab

import ios.silv.gemclient.ui.UiEvent

sealed interface PageEvent : UiEvent {
    data class OnInputChanged(val input: String) : PageEvent
    data object Submit : PageEvent
    data object Refresh: PageEvent
}
