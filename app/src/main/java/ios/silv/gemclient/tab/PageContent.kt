@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ios.silv.gemclient.tab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ios.silv.gemclient.dependency.metroPresenter
import ios.silv.gemclient.lib.capturable.capturable
import ios.silv.gemclient.lib.capturable.rememberCaptureController
import ios.silv.gemclient.ui.components.TerminalScrollToTop
import ios.silv.gemclient.ui.components.TerminalSection
import ios.silv.gemclient.ui.components.TerminalSectionDefaults
import ios.silv.gemclient.ui.sampleScrollingState
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.shared.tab.PageEvent
import ios.silv.shared.tab.PagePresenter
import ios.silv.shared.tab.PageState
import ios.silv.shared.tab.TabEvent
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.rememberEventFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PageContent(
    page: StablePage,
    tabEvents: (TabEvent) -> Unit,
) {
    val presenter = metroPresenter<PagePresenter>()

    val events = rememberEventFlow<PageEvent>()
    val pageState = presenter.present(page, events)

    PullToRefreshBox(
        isRefreshing = pageState is PageState.Loading,
        onRefresh = {
            events.tryEmit(PageEvent.Refresh)
        },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        PageLoadedContent(tabEvents, pageState, events)
    }
}


@Composable
private fun PageInputContent(
    state: PageState.Input,
    events: EventFlow<PageEvent>
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { events.tryEmit(PageEvent.OnInputChanged(it)) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        events.tryEmit(PageEvent.Submit)
                    }
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = null
                    )
                }
            }
        )
    }
}

@Stable
enum class NavLayoutAnchors {
    Start,
    End,
}

private val NavBlockSize = 128.dp

private fun createDraggable(
    density: Density,
    initial: NavLayoutAnchors
): AnchoredDraggableState<NavLayoutAnchors> {
    return AnchoredDraggableState(
        initialValue = initial,
        anchors = DraggableAnchors {
            with(density) {
                NavLayoutAnchors.End at (NavBlockSize + TerminalSectionDefaults.horizontalPadding).toPx()
                NavLayoutAnchors.Start at 0.dp.toPx()
            }
        }
    )
}

typealias NavLayoutDragState = AnchoredDraggableState<NavLayoutAnchors>

@Composable
fun rememberNavLayoutDraggableState(
    density: Density = LocalDensity.current
) = rememberSaveable(
    saver = Saver(
        save = { value -> value.currentValue.ordinal },
        restore = { saved ->
            createDraggable(density, NavLayoutAnchors.entries[saved])
        }
    )
) {
    createDraggable(density, NavLayoutAnchors.Start)
}

@Composable
fun DraggableNavLayout(
    navBlock: @Composable () -> Unit,
    stdOutBlock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    draggableState: NavLayoutDragState = rememberNavLayoutDraggableState()
) {
    Layout(
        modifier = modifier
            .anchoredDraggable(
                draggableState,
                Orientation.Horizontal
            ),
        content = {
            navBlock()
            stdOutBlock()
        }
    ) { measurables, constraints ->

        val navM = measurables[0]
        val stdoutM = measurables[1]

        val offset = draggableState.offset.roundToInt()

        val navP = navM.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = NavBlockSize.roundToPx(),
                maxHeight = constraints.maxHeight
            )
        )
        val stdoutP = stdoutM.measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = constraints.maxWidth - offset,
                maxHeight = constraints.maxHeight
            )
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            stdoutP.place(offset, 0)
            navP.place(offset - navP.width, 0)
        }
    }
}

@Composable
private fun PageLoadedContent(
    events: (TabEvent) -> Unit,
    pageState: PageState,
    pageEvents: EventFlow<PageEvent>
) {
    val listState = rememberLazyListState()

    DraggableNavLayout(
        navBlock = {
            NavBlock(
                listState = listState,
                events = events,
                pageState = pageState,
                pageEvents = pageEvents
            )
        },
        stdOutBlock = {
            StdOutBlock(
                events,
                pageState,
                pageEvents,
                listState
            )
        }
    )
}

@Composable
private fun NavBlock(
    events: (TabEvent) -> Unit,
    pageState: PageState,
    pageEvents: EventFlow<PageEvent>,
    listState: LazyListState,
) {
    val scope = rememberCoroutineScope()
    TerminalSection(
        label = {
            TerminalSectionDefaults.Label("nav")
        }
    ) {
        val headings by remember(pageState) {
            derivedStateOf {
                pageState.nodesOrNull
                    ?.map { it.node }
                    ?.filterIsInstance<ContentNode.Line.Heading>()
                    .orEmpty()
            }
        }
        val current by produceState<ContentNode.Line.Heading?>(null, listState, pageState) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull {
                    val node = pageState.nodesOrNull?.getOrNull(it.index)?.node
                    node as? ContentNode.Line.Heading
                }
            }
                .distinctUntilChanged()
                .sample(10L)
                .collect { heading ->
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
                        pageState.nodesOrNull?.let { uiNodes ->
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
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = node.heading,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StdOutBlock(
    events: (TabEvent) -> Unit,
    pageState: PageState,
    pageEvents: EventFlow<PageEvent>,
    listState: LazyListState,
) {
    val captureController = rememberCaptureController()
    val scope = rememberCoroutineScope()

    LaunchedEffect(pageState.nodesOrNull) {
        if (pageState.nodesOrNull != null) {
            val bitmapAsync = captureController.captureAsync()
            runCatching {
                val bitmap = bitmapAsync.await()
                pageEvents.tryEmit(PageEvent.PreviewSaved(bitmap))
            }
        }
    }


    TerminalSection(
        modifier = Modifier.padding(horizontal = TerminalSectionDefaults.horizontalPadding),
        label = {
            TerminalSectionDefaults.Label("std-out")
        }
    ) {
        when (pageState) {
            is PageState.Content -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp)
                        .capturable(captureController)
                ) {
                    val scrollToTopVisible by listState.sampleScrollingState()

                    LazyColumn(
                        state = listState
                    ) {
                        for ((node, key, contentType) in pageState.nodes) {
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
                                        loadPage = {
                                            events(TabEvent.LoadPage(it))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    TerminalScrollToTop(
                        visible = scrollToTopVisible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset { IntOffset(0, -12) },
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                }
            }

            is PageState.Error -> Box(Modifier.fillMaxSize()) {
                Text(pageState.message)
            }

            is PageState.Input -> PageInputContent(pageState, pageEvents)
            PageState.Loading -> Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}


@Composable
private fun ContentNode.Render(
    modifier: Modifier = Modifier,
    loadPage: (url: String) -> Unit
) {
    Box(modifier) {
        when (val node = this@Render) {
            is ContentNode.Error -> node.RenderError()
            is ContentNode.Line.Heading -> node.RenderHeading()
            is ContentNode.Line.Link -> node.RenderLink(
                onClick = {
                    loadPage(node.url)
                }
            )

            is ContentNode.Line -> Text(node.raw)
        }
    }
}

@Composable
private fun ContentNode.Error.RenderError() {
    Text(
        message,
        color = MaterialTheme.colorScheme.error
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Text(name)
    }
}