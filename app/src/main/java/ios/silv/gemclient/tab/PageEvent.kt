package ios.silv.gemclient.tab

import androidx.compose.ui.graphics.ImageBitmap
import ios.silv.shared.ui.UiEvent

sealed interface PageEvent : UiEvent {
    data class OnInputChanged(val input: String) : PageEvent
    data object Submit : PageEvent
    data object Refresh: PageEvent
    data class PreviewSaved(val bitmap: ImageBitmap): PageEvent
}
