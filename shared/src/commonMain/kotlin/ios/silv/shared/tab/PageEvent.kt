package ios.silv.shared.tab

import androidx.compose.ui.graphics.ImageBitmap
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.UiEvent

sealed interface PageEvent : UiEvent {
    data class OnInputChanged(val input: String) : PageEvent
    data class Submit(val url: String) : PageEvent
    data object Refresh: PageEvent
    data class PreviewSaved(val page: StablePage, val bitmap: ImageBitmap): PageEvent
    data class LoadPage(val link: String): PageEvent
    data class CreateTab(val link: String): PageEvent
    data object GoBack : PageEvent
}
