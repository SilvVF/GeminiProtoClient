package ios.silv.gemclient.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.RetainedObserver
import io.github.takahirom.rin.rememberRetained
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.gemclient.base.PreviewCache
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.lib.rin.LaunchedRetainedEffect
import ios.silv.gemclient.lib.rin.produceRetainedState
import ios.silv.gemclient.lib.rin.rememberRetained
import ios.silv.gemclient.settings.Keys
import ios.silv.gemclient.settings.SettingsStore
import ios.silv.gemclient.tab.PageState.Content.UiNode
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemini.GeminiClient
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(PagePresenter::class)
@Inject
class PagePresenter(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao,
    private val settingsStore: SettingsStore
) : Presenter {

    @Stable
    private inner class RetainedResponse(private val page: StablePage) : RetainedObserver {

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        var value by mutableStateOf<Result<Response>?>(null)
        var loadJob: Job? = null

        fun load(forceNetwork: Boolean) {
            loadJob?.cancel()
            closeResponse()
            loadJob = scope.launch {
                value = client.makeGeminiQuery(page.url, forceNetwork)
            }
        }

        private fun closeResponse() {
            value?.getOrNull()?.close()
            value = null
        }

        override fun onRemembered() {
            logcat { "onRemembered" }
            load(false)
        }

        override fun onForgotten() {
            logcat { "onForgotten" }
            scope.cancel()
            closeResponse()
        }
    }

    @Composable
    fun present(page: StablePage, events: EventFlow<PageEvent>): PageState {
        var input by rememberRetained { mutableStateOf("") }
        val response = rememberRetained(page) { RetainedResponse(page) }

        var fetchId by remember { mutableIntStateOf(0) }

        if (fetchId != 0) {
            LaunchedEffect(fetchId) {
                response.load(fetchId != 0)
            }
        }

        LaunchedRetainedEffect(page) {
            settingsStore.edit {
                it[Keys.recentlyViewed] = buildSet {
                    add(page.url)
                    addAll(it[Keys.recentlyViewed].orEmpty().take(9))
                }
            }
        }

        EventEffect(events)
        { event ->
            when (event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> fetchId++
                PageEvent.Submit -> {
                    tabsDao.insertPage(page.tabId, page.url + "?query=${input}")
                }

                is PageEvent.PreviewSaved -> {
                    logcat { "writing preview to cache ${page.tabId}" }
                    previewCache.writeToCache(
                        tabId = page.tabId,
                        bitmap = event.bitmap.asAndroidBitmap()
                    ).onSuccess {
                        tabsDao.updatePreviewImageUpdatedAt(page.tabId)
                    }
                }
            }
        }

        val parsedNodes by produceRetainedState(emptyList<UiNode>(), response) {
            snapshotFlow { response.value }
                .map { it?.getOrNull() }
                .distinctUntilChanged()
                .mapLatest { res ->
                    logcat { "received new res $res" }
                    value = if (res == null || res.status == GeminiCode.StatusInput) {
                        emptyList()
                    } else {
                        GeminiParser.parse(page.url, res)
                            .map(::UiNode)
                            .toList()
                    }
                }
                .collect()
        }

        return when (
            val res = response.value) {
            null -> PageState.Loading
            else -> {
                res.fold(
                    onSuccess = {
                        if (it.status == GeminiCode.StatusInput) {
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
