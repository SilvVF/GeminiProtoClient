package ios.silv.gemclient.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.shared.ui.UiState
import java.util.UUID

sealed interface PageState : UiState {

    data class Input(
        val query: String,
    ) : PageState

    data class Content(
        val nodes: List<UiNode>
    ) : PageState {


        @Immutable
        @Stable
        data class UiNode(
            val node: ContentNode,
            val key: String = UUID.randomUUID().toString(),
            val contentType: String = node::class.toString()
        )
    }

    data object Loading : PageState
    data class Error(val message: String) : PageState

    val nodesOrNull get() =  (this as? Content)?.nodes
}

