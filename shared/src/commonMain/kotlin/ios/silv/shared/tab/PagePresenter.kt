package ios.silv.shared.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.GeminiCode
import ios.silv.libgemini.gemini.GeminiParser
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiTab
import ios.silv.shared.PreviewCache
import ios.silv.shared.datastore.Keys
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.settings.SettingsStore
import ios.silv.shared.tab.PageLoader.TabState
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.produceRetainedState
import ios.silv.shared.ui.rememberRetained
import ios.silv.shared.ui.rememberRetainedCoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList


@ContributesIntoMap(PresenterScope::class)
@PresenterKey(PagePresenter::class)
class PagePresenter @Inject constructor(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
) : Presenter {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    fun present(
        navArgs: GeminiTab,
        events: EventFlow<PageEvent>
    ): PageState {
        val tab by rememberUpdatedState(navArgs)
        var input by rememberRetained { mutableStateOf("") }
        val scope = rememberRetainedCoroutineScope()

        val loader = rememberRetained(tab) {
            PageLoader(tab, tabsDao, scope, client)
        }
        val response by rememberUpdatedState(loader.pageToResponseState())
        val tabState by rememberUpdatedState(loader.tabState())

        val parsedNodes by produceRetainedState(emptyList<UiNode>(), tab) {
            snapshotFlow { response }
                .map { it?.getOrNull() }
                .distinctUntilChanged()
                .mapLatest { res ->
                    val url = tabState.pageOrNull?.url
                    logcat { "received new res $res" }
                    value = if (res == null || res.status == GeminiCode.INPUT || url == null) {
                        emptyList()
                    } else {
                        GeminiParser.parse(url, res)
                            .map(::UiNode)
                            .toList()
                    }
                }
                .collect()
        }

        EventEffect(events) { event ->
            when (event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> loader.refresh.trySend(Unit)
                is PageEvent.Submit -> {
                    tabsDao.insertPage(tab.id, event.url + "?query=${input}")
                }

                is PageEvent.PreviewSaved -> {
                    logcat { "writing preview to cache ${event.page.tabId}" }
                    previewCache.write(
                        tabId = event.page.tabId,
                        bitmap = event.bitmap
                    ).onSuccess {
                        tabsDao.updatePreviewImageUpdatedAt(event.page.tabId)
                    }
                }

                PageEvent.GoBack -> {
                    val removed = tabsDao.popActivePageByTabId(tab.id)
                    // if a page was not removed
                    // the tab already had no pages so delete the tab
                    if (!removed) {
                        tabsDao.deleteTab(tab.id)

                        navigator.navCmds.tryEmit {
                            popBackStack(tab::class, true)
                        }
                    }
                }

                is PageEvent.LoadPage -> tabsDao.insertPage(tab.id, event.link)
            }
        }

        return when (val state = tabState) {
            TabState.Error -> {

                LaunchedEffect(Unit) {
                    navigator.navCmds.tryEmit {
                        popBackStack(tab::class, true)
                    }
                }

                PageState.Error("Unable to load tab")
            }
            TabState.Idle -> PageState.Loading
            TabState.NoPages -> PageState.Blank
            is TabState.Loaded -> {
                when (val res = response) {
                    null -> PageState.Ready.Loading(state.page)
                    else -> {
                        res.fold(
                            onSuccess = {
                                LaunchedEffect(Unit) {
                                    settingsStore.edit { p ->
                                        p[Keys.recentlyViewed] = buildSet {
                                            add(state.page.url)
                                            addAll(p[Keys.recentlyViewed].orEmpty().take(9))
                                        }
                                    }
                                }

                                if (it.status == GeminiCode.INPUT) {
                                    PageState.Ready.Input(state.page, input)
                                } else {
                                    PageState.Ready.Content(state.page, parsedNodes)
                                }
                            },
                            onFailure = {
                                PageState.Ready.Content(
                                    state.page,
                                    listOf(
                                        UiNode(ContentNode.Error(it.message ?: "error"))
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}