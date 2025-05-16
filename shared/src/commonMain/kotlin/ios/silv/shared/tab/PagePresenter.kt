package ios.silv.shared.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.RetainedObserver
import io.github.takahirom.rin.collectAsRetainedState
import io.github.takahirom.rin.produceRetainedState
import io.github.takahirom.rin.rememberRetained
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.shared.types.StablePage
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.GeminiCode
import ios.silv.libgemini.gemini.GeminiParser
import ios.silv.libgemini.gemini.Response
import ios.silv.shared.PreviewCache
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.tab.PageState.Content.UiNode
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(PagePresenter::class)
@Inject
class PagePresenter(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao
) : Presenter {

    @Stable
    private data class RetainedResponse(private val page: StablePage, val client: GeminiClient) :
        RetainedObserver {

        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val pageWithResponse = MutableStateFlow<Pair<StablePage, Result<Response>?>>(page to null)

        private var loadJob: Job? = null

        @Composable
        fun pageToResponseState(): Pair<StablePage, Result<Response>?> {
            val state by pageWithResponse.collectAsRetainedState()
            return state
        }

        fun load(page: StablePage, forceNetwork: Boolean) {
            loadJob?.cancel()

            pageWithResponse.update { (_, prevResponse) ->
                prevResponse?.getOrNull()?.close()
                page to null
            }

            loadJob = scope.launch {
                val result = client.makeGeminiQuery(page.url, forceNetwork)
                pageWithResponse.update { (_, prevResponse) ->
                    prevResponse?.getOrNull()?.close()
                    page to result
                }
            }
        }

        override fun onRemembered() {
            logcat { "onRemembered" }
            load(page, false)
        }

        override fun onForgotten() {
            logcat { "onForgotten" }
            scope.cancel()
            pageWithResponse.value.second?.getOrNull()?.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    fun present(p: StablePage, events: EventFlow<PageEvent>): PageState {

        val page by rememberUpdatedState(p)
        var input by rememberRetained { mutableStateOf("") }

        val response = rememberRetained { RetainedResponse(page, client) }

        val pageToResponse by rememberUpdatedState(response.pageToResponseState())

        LaunchedEffect(page) {
            val (currentPage, currentResponse) = pageToResponse
            if (currentResponse == null || page != currentPage) {
                response.load(page, false)
            }
        }

        EventEffect(events) { event ->
            when (event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> response.load(page, true)
                PageEvent.Submit -> {
                    tabsDao.insertPage(page.tabId, page.url + "?query=${input}")
                }

                is PageEvent.PreviewSaved -> {
                    logcat { "writing preview to cache ${page.tabId}" }
                    previewCache.write(
                        tabId = page.tabId,
                        bitmap = event.bitmap
                    ).onSuccess {
                        tabsDao.updatePreviewImageUpdatedAt(page.tabId)
                    }
                }
            }
        }

        val parsedNodes by produceRetainedState(emptyList<UiNode>()) {
            snapshotFlow { pageToResponse.second }
                .map { it?.getOrNull() }
                .distinctUntilChanged()
                .mapLatest { res ->
                    logcat { "received new res $res" }
                    value = if (res == null || res.status == GeminiCode.INPUT) {
                        emptyList()
                    } else {
                        GeminiParser.parse(page.url, res)
                            .map(::UiNode)
                            .toList()
                    }
                }
                .collect()
        }

        return when (val res = pageToResponse.second) {
            null -> PageState.Loading
            else -> {
                res.fold(
                    onSuccess = {
                        if (it.status == GeminiCode.INPUT) {
                            PageState.Input(input)
                        } else {
                            PageState.Content(parsedNodes)
                        }
                    },
                    onFailure = {
                        PageState.Error(it.message ?: "error")
                    }
                )
            }
        }
    }
}