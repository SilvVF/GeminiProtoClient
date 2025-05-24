@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ios.silv.gemclient.tab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import ios.silv.core.suspendRunCatching
import ios.silv.gemclient.lib.capturable.capturable
import ios.silv.gemclient.lib.capturable.rememberCaptureController
import ios.silv.gemclient.ui.components.TerminalScrollToTop
import ios.silv.gemclient.ui.components.TerminalSection
import ios.silv.gemclient.ui.components.TerminalSectionDefaults
import ios.silv.gemclient.ui.sampleScrollingState
import ios.silv.libgemini.gemini.ContentNode
import ios.silv.shared.tab.PageEvent
import ios.silv.shared.tab.PageState
import ios.silv.shared.types.StablePage
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.LaunchedImpressionEffect
import ios.silv.shared.ui.rememberRetained
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PageContent(
    pageState: PageState,
    events: EventFlow<PageEvent>,
) {
    when (pageState) {
        PageState.Blank -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Pages for tab")
            }
        }

        is PageState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("error loading page ${pageState.message}")
            }
        }

        PageState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is PageState.Ready.Input -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PageInputContent(pageState, events)
            }
        }

        is PageState.Ready -> {
            PullToRefreshBox(
                isRefreshing = pageState is PageState.Ready.Loading,
                onRefresh = {
                    events.tryEmit(PageEvent.Refresh)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
            ) {
                PageReadyContent(pageState, events)
            }
        }
    }
}


@Composable
private fun PageInputContent(
    state: PageState.Ready.Input,
    events: EventFlow<PageEvent>
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { events.tryEmit(PageEvent.OnInputChanged(it)) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        events.tryEmit(PageEvent.Submit(""))
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
private fun PageReadyContent(
    pageState: PageState.Ready,
    pageEvents: EventFlow<PageEvent>
) {
    val listState = rememberLazyListState()
    val savedScrollPositions = rememberRetained { mutableMapOf<StablePage, Int>() }

    LaunchedEffect(pageState.page) {
        val lastScrollIdx = savedScrollPositions[pageState.page] ?: 0
        launch {
            listState.scrollToItem(lastScrollIdx)
        }

        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest {
                savedScrollPositions[pageState.page] = it
            }
    }

    DraggableNavLayout(
        navBlock = {
            NavBlock(
                listState = listState,
                state = pageState,
                events = pageEvents
            )
        },
        stdOutBlock = {
            StdOutBlock(
                pageState,
                pageEvents,
                listState
            )
        }
    )
}

@Composable
private fun NavBlock(
    state: PageState.Ready,
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
                state.nodesOrNull
                    ?.map { it.node }
                    ?.filterIsInstance<ContentNode.Line.Heading>()
                    .orEmpty()
            }
        }
        val current by produceState<ContentNode.Line.Heading?>(null, listState, state) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull {
                    val node = state.nodesOrNull?.getOrNull(it.index)?.node
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
    pageState: PageState.Ready,
    events: EventFlow<PageEvent>,
    listState: LazyListState,
) {
    val captureController = rememberCaptureController()
    val scope = rememberCoroutineScope()

    val controller by rememberUpdatedState(captureController)

    TerminalSection(
        modifier = Modifier.padding(horizontal = TerminalSectionDefaults.horizontalPadding),
        label = {
            TerminalSectionDefaults.Label("std-out")
        }
    ) {
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
                            node.Render(events = events)
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
        // This must be placed at the bottom so the content above is rendered first
        // in order to capture the image
        LaunchedImpressionEffect(pageState.page, pageState.nodesOrNull) {
            if (pageState.nodesOrNull.isNullOrEmpty().not()) {
                val bitmapAsync = controller.captureAsync()
                suspendRunCatching {
                    val bitmap = bitmapAsync.await()
                    events.tryEmit(PageEvent.PreviewSaved(pageState.page, bitmap))
                }
            }
        }
    }
}


@Composable
private fun ContentNode.Render(
    events: EventFlow<PageEvent>,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (val node = this@Render) {
            is ContentNode.Error -> node.RenderError()
            is ContentNode.Line.Heading -> node.RenderHeading()
            is ContentNode.Line.Link -> node.RenderLink(
                onLongClick = {
                    events.tryEmit(PageEvent.CreateTab(node.url))
                },
                onClick = {
                    events.tryEmit(PageEvent.LoadPage(node.url))
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.combinedClickable(
            onLongClick = onLongClick,
            onClick = onClick
        )
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .padding(ButtonDefaults.ContentPadding),
    )  {
        Text(
            this@RenderLink.name,
            color = ButtonDefaults.textButtonColors().contentColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}