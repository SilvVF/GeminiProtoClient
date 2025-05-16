package ios.silv.shared.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.UiState

@Immutable
@Stable
data class UiNode(
    val node: ContentNode,
    val key: String? = null,
    val contentType: String = node::class.toString()
)

sealed interface PageState : UiState {

    sealed class Ready(val page: StablePage): PageState {

        data class Loading(val p: StablePage): Ready(p)

        data class Input(
            val p: StablePage,
            val query: String,
        ) : Ready(p)

        data class Content(
            val p: StablePage,
            val nodes: List<UiNode>
        ) : Ready(p)
    }

    data object Loading: PageState
    data object Blank : PageState
    data class Error(val message: String) : PageState

    val nodesOrNull get() = (this as? Ready.Content)?.nodes
}

