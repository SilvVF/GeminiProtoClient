package ios.silv.gembrowse

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import ios.silv.gembrowse.PageEvent.*
import ios.silv.gembrowse.Settings.Companion.Keys
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.GeminiCode
import ios.silv.libgemini.gemini.GeminiParser
import ios.silv.libgemini.gemini.onFailureLog
import ios.silv.libgemini.gemini.suspendRunCatching
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty
import kotlin.collections.take
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

private suspend fun GeminiClient.loadPage(
    activePage: PageEntity,
    forceNetwork: Boolean
): PageLoad {
    return makeGeminiQuery(query = activePage.url, forceNetwork = forceNetwork).fold(onSuccess = {
        logcat { "res ${it.status}" }
        if (it.status == GeminiCode.INPUT) {
            PageLoad.Ready.Done.Input(activePage, "enter input")
        } else {
            val nodes = GeminiParser.parse(activePage.baseUrl, it).map(::UiNode).toList()

            PageLoad.Ready.Done.Content(
                activePage, nodes
            )
        }
    }, onFailure = {
        logcat { it.asLog() }
        PageLoad.Error(it.message.orEmpty())
    })
}

@OptIn(DependencyAccessor::class)
@Composable
fun pagePresenter(
    backStack: SnapshotStateStack<Route>,
    events: EventFlow<PageEvent>,
    tabId: Long,
    client: GeminiClient = commonDeps.geminiClient,
    settings: Settings = commonDeps.settingsStore,
    db: AppDatabase = commonDeps.database,
    tabDao: TabDao = commonDeps.tabDao,
    pageDao: PageDao = commonDeps.pageDao,
    historyDao: HistoryDao = commonDeps.historyDao,
    previewCache: PreviewCache = commonDeps.previewCache,
): PageState {

    var error by retain { mutableStateOf<String?>(null) }
    val active by produceRetainedState<TabState?>(null) {
        tabDao.observeTabState(tabId)
            .catch { error = "failed to load" }
            .collect { tabState ->
                logcat { "tab $tabState" }
                Snapshot.withMutableSnapshot {
                    if (tabState == null) {
                        error = "failed to load tab"
                        value = null
                    } else {
                        value = tabState
                        error = null
                    }
                }
            }
    }

    val waitingForTabState by remember { derivedStateOf { active == null && error == null } }
    var input by retain { mutableStateOf("") }

    var page by retain { mutableStateOf<PageLoad>(PageLoad.Idle) }
    val activePage = active?.page
    val url = active?.page?.baseUrl

    var refreshing by retain { mutableStateOf(false) }
    var refreshCount by retain(url) { mutableIntStateOf(0) }

    LaunchedEffect(activePage?.id, url, refreshCount) {
        if (waitingForTabState) return@LaunchedEffect
        if (activePage == null) {
            page = PageLoad.Error("no active page"); return@LaunchedEffect
        }

        page = PageLoad.Ready.Loading(activePage)
        refreshing = true
        try {
            val result = client.loadPage(activePage, refreshCount > 0)
            page = result

            if (result is PageLoad.Ready.Done) {
                suspendRunCatching {
                    pageDao.updateResolvedUrl(
                        activePage.id,
                        resolvedUrl = result.page.resolvedUrl,
                    )
                    if (active?.tab?.isIncognito != true) {
                        db.recordVisitOnce(
                            activePage.id,
                            result.page.baseUrl,
                            result.page.title,
                        )
                    }
                }.onFailureLog()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            page = PageLoad.Error(e.message.orEmpty())
        } finally {
            refreshing = false
        }
    }

    EventEffect(events) { event ->
        when (event) {
            is OnInputChanged -> input = event.input
            Refresh -> {
                logcat { "refreshing" }
                refreshCount += 1
            }

            is Submit -> {
                db.navigate(
                    tabId,
                    event.url + "?query=${input}"
                )
            }

            is PreviewSaved -> {
                logcat { "writing preview to cache ${event.page.tabId}" }
                previewCache.write(
                    url = event.page.url, bitmap = event.bitmap
                ).onSuccess {
                    tabDao.updateThumbnailUpdatedAt(event.page.tabId)
                }
            }

            is GoBack -> {
                val page = db.goBack(tabId)
                logcat { "navigated back to $page" }
                if (page == null) {
                    backStack.pop()
                }
            }

            is LoadPage -> {
                if (event.link.isNotBlank()) {
                    db.navigate(tabId, event.link)
                }
            }

            is CreateTab -> {
                val tab = db.createTab(event.link)
                if (tab != null) {
                    backStack.push(TabRoute(id = tab.id))
                }
            }
        }
    }

    return PageState(
        tab = active?.tab,
        page = page,
        loading = waitingForTabState,
        error = error
    )
}

sealed interface PageEvent {
    data class OnInputChanged(val input: String) : PageEvent
    data class Submit(val url: String) : PageEvent
    data object Refresh : PageEvent
    data class PreviewSaved(val page: PageEntity, val bitmap: ImageBitmap) : PageEvent
    data class LoadPage(val link: String) : PageEvent
    data class CreateTab(val link: String) : PageEvent
    data class GoBack(val tab: TabEntity) : PageEvent
}

@Immutable
@Stable
data class UiNode(
    val node: ContentNode,
    val key: String? = null,
    val contentType: KClass<out ContentNode> = node::class
)

data class PageState(
    val tab: TabEntity? = null,
    val input: String = "",
    val page: PageLoad = PageLoad.Idle,
    val loading: Boolean = true,
    val error: String? = null,
    val refreshing: Boolean = false,
)

sealed interface PageLoad {

    sealed class Ready(open val page: PageEntity) : PageLoad {
        data class Loading(override val page: PageEntity) : Ready(page)

        sealed class Done(override val page: PageEntity) : Ready(page) {
            data class Content(override val page: PageEntity, val nodes: List<UiNode>) : Done(page)
            data class Input(override val page: PageEntity, val query: String) : Done(page)
        }
    }

    data object Idle : PageLoad
    data class Error(val message: String) : PageLoad

    val nodesOrNull get() = (this as? Ready.Done.Content)?.nodes
    val urlOrNull get() = (this as? Ready)?.page?.baseUrl
}

@Composable
fun PageContent(
    pageState: PageState,
    events: EventFlow<PageEvent>,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)

    if (pageState.error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load tab")
        }
        return
    }

    if (pageState.tab == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavigationBackHandler(
        state = state, onBackCompleted = {
            events.tryEmit(GoBack(pageState.tab))
        }
    )

    when (val page = pageState.page) {
        is PageLoad.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(page.message)
            }
        }

        PageLoad.Idle -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is PageLoad.Ready.Done.Input -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PageInputContent(page, events)
            }
        }

        is PageLoad.Ready -> {
            PullToRefreshBox(
                isRefreshing = pageState.refreshing,
                onRefresh = {
                    events.tryEmit(Refresh)
                },
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            ) {
                PageReadyContent(page, events)
            }
        }
    }
}


@Composable
private fun PageInputContent(
    state: PageLoad.Ready.Done.Input, events: EventFlow<PageEvent>
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { events.tryEmit(OnInputChanged(it)) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        events.tryEmit(Submit(""))
                    }) {
                    Icon(
                        Icons.Default.Done, contentDescription = null
                    )
                }
            }
        )
    }
}

@Composable
private fun PageReadyContent(
    pageState: PageLoad.Ready, pageEvents: EventFlow<PageEvent>
) {
    val listState = rememberLazyListState()
    val savedScrollPositions = retain { mutableMapOf<PageEntity, Int>() }

    LaunchedEffect(pageState.page) {
        val lastScrollIdx = savedScrollPositions[pageState.page] ?: 0
        launch {
            listState.scrollToItem(lastScrollIdx)
        }

        snapshotFlow { listState.firstVisibleItemIndex }.collectLatest {
            savedScrollPositions[pageState.page] = it
        }
    }

    DraggableNavLayout(
        navBlock = {
            NavBlock(
                listState = listState, state = pageState, events = pageEvents
            )
        },
        stdOutBlock = {
            StdOutBlock(
                pageState, pageEvents, listState
            )
        }
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun NavBlock(
    state: PageLoad.Ready,
    events: EventFlow<PageEvent>,
    listState: LazyListState,
) {
    val scope = rememberCoroutineScope()
    TerminalSection(
        label = {
            TerminalSectionDefaults.Label("nav")
        }
    ) {
        val headings by remember(state) {
            derivedStateOf {
                state.nodesOrNull?.map { it.node }?.filterIsInstance<ContentNode.Line.Heading>()
                    .orEmpty()
            }
        }
        val current by produceState<ContentNode.Line.Heading?>(null, listState, state) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull {
                    val node = state.nodesOrNull?.getOrNull(it.index)?.node
                    node as? ContentNode.Line.Heading
                }
            }.distinctUntilChanged().sample(10L).collect { heading ->
                value = heading
            }
        }

        LazyColumn(
            Modifier.fillMaxSize()
        ) {
            items(
                items = headings
            ) { node ->
                TextButton(
                    onClick = {
                        state.nodesOrNull?.let { uiNodes ->
                            val i = uiNodes.indexOfFirst { uiNode -> uiNode.node == node }
                            if (i != -1) {
                                scope.launch {
                                    listState.animateScrollToItem(i)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (current == node) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Unspecified
                        }
                    ),
                    shape = RectangleShape, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = node.heading, style = MaterialTheme.typography.labelSmall
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StdOutBlock(
    pageState: PageLoad.Ready,
    events: EventFlow<PageEvent>,
    listState: LazyListState,
) {
    val captureController = rememberCaptureController()
    val controller by rememberUpdatedState(captureController)
    // used by content image node to update when image is loaded by coil
    var recapId by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    TerminalSection(
        modifier = Modifier.padding(horizontal = TerminalSectionDefaults.horizontalPadding),
        label = {
            TerminalSectionDefaults.Label("std-out")
        }) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
                .capturable(captureController)
        ) {
            val scrollToTopVisible by listState.sampleScrollingState()

            LazyColumn(
                state = listState
            ) {
                for ((node, key, contentType) in pageState.nodesOrNull.orEmpty()) {
                    if (node is ContentNode.Line.Heading) {
                        stickyHeader(key = key, contentType = contentType) {
                            Surface(
                                Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface.copy(
                                    alpha = 0.78f
                                )
                            ) {
                                node.RenderHeading()
                            }
                        }
                    } else {
                        item(key = key, contentType = contentType) {
                            node.Render(
                                events = events,
                                recapture = {
                                    recapId += 1
                                }
                            )
                        }
                    }
                }
            }

            TerminalScrollToTop(
                visible = scrollToTopVisible,
                modifier = Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -12) },
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                })
        }
        // This must be placed at the bottom so the content above is rendered first
        // in order to capture the image
        LaunchedEffect(pageState, recapId) {
            if (pageState is PageLoad.Ready.Done.Content) {
                delay(1.seconds)
                val bitmapAsync = controller.captureAsync()
                suspendRunCatching {
                    val bitmap = bitmapAsync.await()
                    events.tryEmit(PreviewSaved(pageState.page, bitmap))
                }
            }
        }
    }
}


@Composable
private fun ContentNode.Render(
    events: EventFlow<PageEvent>,
    recapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (val node = this@Render) {
            is ContentNode.Error -> node.RenderError()
            is ContentNode.Line.Heading -> node.RenderHeading()
            is ContentNode.Line.Link -> node.RenderLink(onLongClick = {
                events.tryEmit(CreateTab(node.url))
            }, onClick = {
                events.tryEmit(LoadPage(node.url))
            })

            is ContentNode.Line -> Text(node.raw)
            is ContentNode.Image -> {
                Column {
                    Text(node.type)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current).data(node.raw)
                            .build(),
                        onSuccess = {
                            recapture()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }

            is ContentNode.InternalError -> node.RenderError()
            is ContentNode.Other -> Unit
        }
    }
}

@Composable
private fun ContentNode.InternalError.RenderError() {
    Text(
        message, color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ContentNode.Error.RenderError() {
    Text(
        message, color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ContentNode.Line.Heading.RenderHeading() {

    if (level == 0) {
        Text(raw)
        return
    }

    Text(
        text = heading,
        style = when (level) {
            1 -> MaterialTheme.typography.titleLarge
            in 2..3 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleSmall
        },
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun ContentNode.Line.Link.RenderLink(
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.combinedClickable(
            onLongClick = onLongClick, onClick = onClick
        ).minimumInteractiveComponentSize().fillMaxWidth().padding(ButtonDefaults.ContentPadding),
    ) {
        Text(
            this@RenderLink.name,
            color = ButtonDefaults.textButtonColors().contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}