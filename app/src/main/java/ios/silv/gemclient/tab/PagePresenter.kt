package ios.silv.gemclient.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.Inject
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState
import ios.silv.gemini.ContentNode
import ios.silv.gemini.GeminiClient
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import ios.silv.sqldelight.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface InputEvent : UiEvent {
    data class OnInputChanged(val input: String) : InputEvent
    data object Submit : InputEvent
}

@Immutable
@Stable
data class UiNode(
    val node: ContentNode,
    val key: String? = UUID.randomUUID().toString(),
    val contentType: String = node::class.toString()
)

sealed interface PageState : UiState {

    data class Input(
        val query: String,
        val events: (InputEvent) -> Unit
    ) : PageState

    data class Content(
        val nodes: List<UiNode>
    ) : PageState

    data object Loading : PageState
    data class Error(val message: String) : PageState
}



@Inject
class PagePresenter(
    @Assisted private val page: Page,
    @Assisted private val scope: CoroutineScope,
    private val client: GeminiClient,
    private val tabsRepo: TabsRepo
) {

    private var loadJob: Job? = null

    private val inputFlow = MutableStateFlow("")
    private val responseFlow = MutableStateFlow<Result<Response>?>(null)
    private val parsedNodes = responseFlow
        .map { it?.getOrNull() }
        .distinctUntilChanged()
        .mapLatest { res ->
            if (res == null || res.status == GeminiCode.StatusInput) {
                emptyList()
            } else {
                GeminiParser.parse(page.url, res)
                    .map(::UiNode)
                    .toList()
            }
        }


    private val inputEventSink: (InputEvent) -> Unit = { e: InputEvent ->
        when (e) {
            is InputEvent.OnInputChanged -> inputFlow.update { e.input }
            InputEvent.Submit -> scope.launch {
                tabsRepo.insertPage(page.tab_id, page.url + "?query=${inputFlow.value}")
            }
        }
    }

    val state = combine(
        responseFlow,
        inputFlow,
        parsedNodes
    ) { res, inp, nodes ->
        when {
            res == null -> PageState.Loading
            else -> {
                res.fold(
                    onSuccess = {
                        if (it.status == GeminiCode.StatusInput) {
                            PageState.Input(inp, inputEventSink)
                        } else {
                            PageState.Content(nodes = nodes)
                        }
                    },
                    onFailure = {
                        PageState.Error(it.message ?: "error")
                    }
                )
            }
        }
    }
        .stateIn(scope, SharingStarted.Lazily, PageState.Loading)

    init {
        loadPage()
    }

    fun loadPage() {
        loadJob = scope.launch {
            val res = client.makeGeminiQuery(page.url)
            responseFlow.emit(res)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            page: Page,
            scope: CoroutineScope,
        ): PagePresenter
    }
}
