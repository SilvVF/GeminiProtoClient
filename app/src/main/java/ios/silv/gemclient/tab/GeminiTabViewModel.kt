package ios.silv.gemclient.tab

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
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
import ios.silv.gemini.ContentNode
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import ios.silv.sqldelight.Page
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.UUID

interface GeminiTabViewModelAction {
    suspend fun loadPage(link: String)
    suspend fun goBack()
    suspend fun refresh()
    suspend fun submitInput(input: String)
}

@Immutable
@Stable
data class UiNode(
    val node: ContentNode,
    val key: String? = null,
    val contentType: String = node::class.toString()
)

data class TabVMState(

    val tabState: TabState,
)

sealed interface TabState {
    data object Idle : TabState
    data object Error : TabState
    data object NoPages : TabState

    sealed class Loaded(val currentPage: Page) : TabState {
        data class Loading(val page: Page) : Loaded(page)
        data class Input(val page: Page, val prompt: String) : Loaded(page)
        data class Done(val page: Page, val nodes: List<UiNode>) : Loaded(page)
    }
}

class GeminiTabViewModel @OptIn(DependencyAccessor::class) constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: ComposeNavigator = commonDeps.navigator,
    private val client: ios.silv.gemini.GeminiClient = commonDeps.geminiClient,
    private val tabsRepo: TabsRepo = commonDeps.tabsRepo
) : GeminiTabViewModelAction, ViewModelActionHandler<GeminiTabViewModelAction>() {

    private val geminiTab = savedStateHandle.toRoute<GeminiTab>()
    override val handler: GeminiTabViewModelAction = this

    private val stack = tabsRepo.observeTabStackById(geminiTab.id)

    private val _tabState = MutableStateFlow<TabState>(TabState.Idle)
    val tabState: StateFlow<TabState> get() = _tabState

    init {
        viewModelScope.launch {
            stack.transform { value ->
                if (value == null) {
                    _tabState.emit(TabState.Error)
                } else {
                    val (tab, pages) = value
                    val active = pages.find { page -> page.pid == tab.active_page_id }
                    if (active == null) {
                        _tabState.emit(TabState.NoPages)
                    } else {
                        emit(active)
                    }
                }
            }
                .distinctUntilChanged()
                .combine(_tabState, ::Pair)
                .collect { (activePage, state) ->
                    if (
                        state is TabState.Loaded &&
                        state.currentPage == activePage
                    ) {
                        return@collect
                    }

                    loadPageFromLink(activePage)
                        .collect(_tabState::emit)
                }
        }
    }

    private fun loadPageFromLink(page: Page): Flow<TabState.Loaded> = flow {
        emit(TabState.Loaded.Loading(page))

        client.makeGeminiQuery(page.url).onSuccess { response ->
            logcat { "$response" }
            emit(transformSuccessResponse(page, response))
        }.onFailure {
            logcat { it.stackTraceToString() }
            emit(
                TabState.Loaded.Done(
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
    ): TabState.Loaded {
        return if (response.status == GeminiCode.StatusInput) {
            TabState.Loaded.Input(page, response.meta)
        } else {
            TabState.Loaded.Done(
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
        when(val state = _tabState.value) {
            is TabState.Loaded -> {
                kotlin.runCatching {
                    tabsRepo.deletePage(state.currentPage.pid)
                }.onFailure {
                    tabsRepo.deleteTab(state.currentPage.tab_id)
                    navigator.navCmds.tryEmit { popBackStack() }
                }
            }
            else -> navigator.navCmds.tryEmit { popBackStack() }
        }
    }

    override suspend fun refresh() {
        val state = _tabState.value
        if (
            state is TabState.Loaded &&
            state !is TabState.Loaded.Loading
        ) {
            viewModelScope.launch {
                loadPageFromLink(state.currentPage)
                    .collect(_tabState::emit)
            }
        }
    }

    override suspend fun submitInput(input: String) {
        val state = _tabState.value
        if (state is TabState.Loaded.Input) {
            tabsRepo.insertPage(geminiTab.id, state.page.url + input)
        }
    }
}