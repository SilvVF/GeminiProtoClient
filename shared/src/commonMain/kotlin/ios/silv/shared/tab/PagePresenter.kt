package ios.silv.shared.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
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
import ios.silv.shared.PreviewCache
import ios.silv.shared.datastore.Keys
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.di.ViewModelKey
import ios.silv.shared.di.ViewModelScope
import ios.silv.shared.settings.SettingsStore
import ios.silv.shared.tab.PageLoader.TabState
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.produceRetainedState
import ios.silv.shared.ui.rememberRetained
import ios.silv.shared.ui.rememberRetainedCoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlin.math.log

@ContributesIntoMap(ViewModelScope::class, binding = binding<ViewModel>())
@ViewModelKey(PageViewModel::class)
@Inject
class PageViewModel(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle,
) : MoleculeViewModel<PageEvent, PageState>() {

    val tab = savedStateHandle.toRoute<GeminiTab>()

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

        val tabState by tabStateFlow.collectAsState(TabState.Idle)
        val activePage by remember {
            derivedStateOf { tabState.pageOrNull }
        }

        val fetchId = remember(activePage) { mutableIntStateOf(0) }
        val currentFetchId by rememberUpdatedState(fetchId)

        var input by remember { mutableStateOf("") }
        var response by remember { mutableStateOf<Result<Response>?>(null) }
        var loading by remember { mutableStateOf(false) }

        DisposableEffect(response) {
            logcat { "holding response $response" }
            val res = response?.getOrNull()
            onDispose {
                logcat { "disposing response $res" }
                res?.close()
            }
        }

        LaunchedEffect(response) {
            response?.onSuccess {
                settingsStore.edit { p ->
                    p[Keys.recentlyViewed] = buildSet {
                        add(tabState.pageOrNull?.url ?: return@edit)
                        addAll(p[Keys.recentlyViewed].orEmpty().take(9))
                    }
                }
            }
        }

        LaunchedEffect(activePage, currentFetchId.value) {
            logcat { "trying to load page $activePage" }
            when (val state = tabState) {
                is TabState.Loaded -> {
                    logcat { "loading page ${state.page} fetchId $fetchId" }
                    loading = true
                    val res = client.makeGeminiQuery(
                        query = state.page.url,
                        forceNetwork = currentFetchId.value > 0
                    )

                    Snapshot.withMutableSnapshot {
                        loading = false
                        response = res
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
                    currentFetchId.value += 1
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
                            popBackStack(tab::class, true)
                        }
                    }
                }

                is PageEvent.LoadPage -> tabsDao.insertPage(tab.id, event.link)
                is PageEvent.CreateTab -> {
                    val newTab = tabsDao.insertTab(event.link)
                    navigator.navCmds.tryEmit {
                        navigate(GeminiTab(id = newTab.tid))
                    }
                }
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
                val res = response
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
                            popBackStack(tab::class, true)
                        }
                    }
                }

                is PageEvent.LoadPage -> tabsDao.insertPage(tab.id, event.link)
                is PageEvent.CreateTab -> {
                    val newTab = tabsDao.insertTab(event.link)
                    navigator.navCmds.tryEmit {
                        navigate(GeminiTab(id = newTab.tid))
                    }
                }
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