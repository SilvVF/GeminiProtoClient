package ios.silv.gemclient.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.ViewModelActionHandler
import ios.silv.gemclient.dependency.DependencyAccessor
import ios.silv.gemclient.dependency.commonDeps
import ios.silv.gemclient.tab.TabState.Loaded
import ios.silv.gemclient.tab.TabState.Loaded.Input.InputEvent.OnInputChanged
import ios.silv.gemclient.tab.TabState.Loaded.Input.InputEvent.Submit
import ios.silv.gemini.ContentNode
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import ios.silv.sqldelight.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

interface GeminiTabViewModelAction {
    suspend fun loadPage(link: String)
    suspend fun goBack()
    suspend fun refresh()
}

@Immutable
@Stable
data class UiNode(
    val node: ContentNode,
    val key: String? = null,
    val contentType: String = node::class.toString()
)


sealed interface TabState {
    data object Idle : TabState
    data object Error : TabState
    data object NoPages : TabState

    sealed class Loaded(val currentPage: Page) : TabState {
        data class Loading(val page: Page) : Loaded(page)
        data class Input(
            val page: Page,
            val prompt: String,
            val input: StateFlow<TextFieldValue>,
            val events: (InputEvent) -> Unit
        ) : Loaded(page) {

            sealed interface InputEvent {
                data class OnInputChanged(val input: TextFieldValue): InputEvent
                data object Submit: InputEvent
            }
        }
        data class Done(val page: Page, val nodes: List<UiNode>) : Loaded(page)
    }

    val activePage get() = (this as? Loaded)?.currentPage
}

class GeminiTabViewModel @OptIn(DependencyAccessor::class) constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: ComposeNavigator = commonDeps.navigator,
    private val client: ios.silv.gemini.GeminiClient = commonDeps.geminiClient,
    private val tabsRepo: TabsRepo = commonDeps.tabsRepo
) : GeminiTabViewModelAction, ViewModelActionHandler<GeminiTabViewModelAction>() {

    override val handler: GeminiTabViewModelAction = this

    private val geminiTab = savedStateHandle.toRoute<GeminiTab>()
    private val tabWithActivePage = tabsRepo.observeTabWithActivePage(geminiTab.id)

    private val _tabState = MutableStateFlow<TabState>(TabState.Idle)
    val tabState: StateFlow<TabState> get() = _tabState

    private val _input by lazy { MutableStateFlow(TextFieldValue()) }
    private val input: StateFlow<TextFieldValue> get() = _input

    var loadJob: Job? = null

    init {
        viewModelScope.launch {
            tabWithActivePage
                .collect { pair ->

                    val (tab, activePage) = pair ?: (null to null)
                    when {
                        tab == null -> {
                            navigator.navCmds.emit { popBackStack() }
                            _tabState.emit(TabState.Error)
                        }
                        activePage == null -> {
                            _tabState.emit(TabState.NoPages)
                        }
                        else -> refreshActivePage(activePage)
                    }
                }
        }
    }

    private fun CoroutineScope.refreshActivePage(page: Page) = launch {
        if (tabState.value is Loaded.Loading && tabState.value.activePage == page) {
            return@launch
        }

        loadJob?.cancelAndJoin()
        loadJob = launch {
            loadPageFromLink(page).collect(_tabState::emit)
        }
    }

    private fun loadPageFromLink(page: Page): Flow<Loaded> = flow {
        emit(Loaded.Loading(page))

        client.makeGeminiQuery(page.url).onSuccess { response ->
            logcat { "$response" }
            emit(transformSuccessResponse(page, response))
        }.onFailure {
            logcat { it.stackTraceToString() }
            emit(
                Loaded.Done(
                    page,
                    listOf(
                        UiNode(
                            ContentNode.Error(it.message.orEmpty()),
                            UUID.randomUUID().toString(),
                        )
                    )
                )
            )
        }
    }

    private suspend fun transformSuccessResponse(
        page: Page,
        response: Response
    ): Loaded {
        return if (response.status == GeminiCode.StatusInput) {
            Loaded.Input(page, response.meta, input) { event ->
                when(event) {
                    is OnInputChanged -> {
                        _input.value = event.input
                    }
                    Submit -> {
                        // TODO(handle submit input)
                    }
                }
            }
        } else {
            Loaded.Done(
                page,
                GeminiParser.parse(page.url, response).map { node ->
                    UiNode(
                        node,
                        UUID.randomUUID().toString()
                    )
                }
                    .toList()
            )
        }
    }

    override suspend fun loadPage(link: String) {
        tabsRepo.insertPage(geminiTab.id, link)
    }

    override suspend fun goBack() {
        val removed = tabsRepo.popActivePageByTabId(geminiTab.id)
        // if a page was not removed
        // the tab already had no pages so delete the tab
        if (!removed) {
            tabsRepo.deleteTab(geminiTab.id)
        }
    }

    override suspend fun refresh() {
        viewModelScope.launch {
            tabState.value.activePage?.let { page ->
                refreshActivePage(page)
            }
        }
    }
}