package ios.silv.gemclient.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import ios.silv.gemclient.ui.UiState
import ios.silv.gemini.ContentNode
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
            val key: String? = UUID.randomUUID().toString(),
            val contentType: String = node::class.toString()
        )
    }

    data object Loading : PageState
    data class Error(val message: String) : PageState
}

