package ios.silv.shared.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.Response
import ios.silv.shared.GeminiTab
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.RetainedObserver
import ios.silv.shared.ui.UiState
import ios.silv.shared.ui.collectAsRetainedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
internal class PageLoader(
    tab: GeminiTab,
    tabsDao: TabsDao,
    scope: CoroutineScope,
    private val client: GeminiClient,
) : RetainedObserver {

    sealed interface TabState : UiState {
        data object Idle : TabState
        data object Error : TabState
        data object NoPages : TabState
        data class Loaded(val page: StablePage) : TabState

        val pageOrNull get() = (this as? Loaded)?.page
    }

    val refresh = Channel<Unit>()

    private val response = MutableStateFlow<Result<Response>?>(null)

    private val tabState = tabsDao
        .observeTabWithActivePage(tab.id)
        .map { it ?: (null to null) }
        .map { (tab, page) ->
            when {
                tab == null -> TabState.Error
                page == null -> TabState.NoPages
                else -> TabState.Loaded(StablePage(page))
            }
        }
        .stateIn(scope, SharingStarted.Lazily, TabState.Idle)

    private fun updateResponse(new: Result<Response>?) = response.update { prev ->
        prev?.getOrNull()?.close()
        new
    }

    private suspend fun makeQuery(page: StablePage, network: Boolean) {
        updateResponse(null)
        val res = client.makeGeminiQuery(page.url, network)
        updateResponse(res)
    }

    init {
        scope.launch {
            tabState.collectLatest { loaded ->
                when (loaded) {
                    is TabState.Loaded -> {
                        makeQuery(loaded.page, false)

                        refresh.receiveAsFlow().collect {
                            makeQuery(loaded.page, true)
                        }
                    }
                    else -> updateResponse(null)
                }
            }
        }
    }

    @Composable
    fun tabState(): TabState {
        val state by tabState.collectAsRetainedState()
        return state
    }

    @Composable
    fun pageToResponseState(): Result<Response>? {
        val state by response.collectAsRetainedState()
        return state
    }

    override fun onRemembered() {
        logcat { "onRemembered" }
    }

    override fun onForgotten() {
        logcat { "onForgotten" }
        response.value?.getOrNull()?.close()
    }
}