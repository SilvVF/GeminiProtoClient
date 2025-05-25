package ios.silv.shared.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.binding
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.GeminiCode
import ios.silv.libgemini.gemini.GeminiParser
import ios.silv.libgemini.gemini.Response
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiTab
import ios.silv.shared.MoleculeViewModel
import ios.silv.shared.NavKey
import ios.silv.shared.PreviewCache
import ios.silv.shared.datastore.Keys
import ios.silv.shared.di.ViewModelKey
import ios.silv.shared.di.ViewModelScope
import ios.silv.shared.settings.SettingsStore
import ios.silv.shared.tab.PageLoader.TabState
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

@ContributesIntoMap(ViewModelScope::class, binding = binding<ViewModel>())
@ViewModelKey(PageViewModel::class)
@Inject
class PageViewModel(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
    key: GeminiTab,
) : MoleculeViewModel<PageEvent, PageState>() {

    val tab = key

    private val tabStateFlow = tabsDao.observeTabWithActivePage(tab.id)
        .map { it ?: (null to null) }
        .map { (tab, page) ->
            when {
                tab == null -> TabState.Error
                page == null -> TabState.NoPages
                else -> {
                    TabState.Loaded(StablePage(page))
                }
            }
        }
        .distinctUntilChanged()

    @Composable
    override fun models(events: EventFlow<PageEvent>): PageState {

        var tabState by remember { mutableStateOf<TabState>(TabState.Idle) }
        var fetchId by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            tabStateFlow.collect { state ->
                Snapshot.withMutableSnapshot {
                    tabState = state
                    fetchId = 0
                }
            }
        }

        var input by remember { mutableStateOf("") }
        var response by remember { mutableStateOf<Result<Response>?>(null) }
        var loading by remember { mutableStateOf(false) }

        val activePage by remember(tabState) { derivedStateOf { tabState.pageOrNull } }

        DisposableEffect(response) {
            logcat { "holding response $response" }
            val res = response?.getOrNull()
            onDispose {
                logcat { "disposing response $res" }
                res?.close()
            }
        }

        val page = activePage
        val res = response
        if (page != null && res != null) {
            LaunchedEffect(response) {
                res.onSuccess {
                    settingsStore.edit { prefs ->
                        prefs[Keys.recentlyViewed] = buildSet {
                            add(page.url)
                            addAll(prefs[Keys.recentlyViewed].orEmpty().take(9))
                        }
                    }
                }
            }
        }

        LaunchedEffect(activePage, fetchId) {
            logcat { "trying to load page $activePage" }
            when (val state = tabState) {
                is TabState.Loaded -> {
                    logcat { "loading page ${state.page} fetchId $fetchId" }
                    loading = true
                    val result = client.makeGeminiQuery(
                        query = state.page.url,
                        forceNetwork = fetchId > 0
                    )

                    Snapshot.withMutableSnapshot {
                        loading = false
                        response = result
                    }
                }

                else -> logcat { "refresh called while tabstate=$state" }
            }
        }

        val parsedNodes by produceState(emptyList<UiNode>()) {
            combine(
                snapshotFlow { activePage }.filterNotNull(),
                snapshotFlow { response },
                ::Pair
            )
                .map { (page, res) ->
                    val result = res?.getOrNull()
                    if (result == null) {
                        null
                    } else {
                        page to result
                    }
                }
                .filterNotNull()
                .collectLatest { (page, res) ->
                    logcat { "received new res $res" }
                    value = if (res.status == GeminiCode.INPUT) {
                        emptyList()
                    } else {
                        GeminiParser.parse(page.url, res)
                            .map(::UiNode)
                            .toList()
                    }
                }
        }

        EventEffect(events) { event ->
            when (event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> {
                    logcat { "refreshing" }
                    fetchId++
                }

                is PageEvent.Submit -> {
                    tabsDao.insertPage(tab.id, event.url + "?query=${input}")
                }

                is PageEvent.PreviewSaved -> {
                    logcat { "writing preview to cache ${event.page.tabId}" }
                    previewCache.write(
                        url = event.page.url,
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
                            remove(tab)
                        }
                    }
                }

                is PageEvent.LoadPage -> tabsDao.insertPage(tab.id, event.link)
                is PageEvent.CreateTab -> {
                    val newTab = tabsDao.insertTab(event.link)
                    navigator.navCmds.tryEmit {
                        add(GeminiTab(id = newTab.tid))
                    }
                }
            }
        }

        return when (val state = tabState) {
            TabState.Error -> {

                LaunchedEffect(Unit) {
                    navigator.navCmds.tryEmit {
                        remove(tab)
                    }
                }

                PageState.Error("Unable to load tab")
            }

            TabState.Idle -> PageState.Loading
            TabState.NoPages -> PageState.Blank
            is TabState.Loaded -> {
                when {
                    loading || res == null -> PageState.Ready.Loading(state.page)
                    else -> {
                        res.fold(
                            onSuccess = {
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