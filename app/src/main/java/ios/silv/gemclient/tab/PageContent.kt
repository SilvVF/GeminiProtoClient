@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ios.silv.gemclient.tab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import ios.silv.gemclient.dependency.metroPresenter
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemclient.ui.components.AutoScrollIndicator
import ios.silv.gemclient.ui.rememberEventFlow
import ios.silv.gemclient.ui.sampleScrollingState
import ios.silv.gemini.ContentNode
import kotlinx.coroutines.launch

@Composable
fun PageContent(state: TabState.Loaded, tabEvents: (TabEvent) -> Unit) {
    val presenter = metroPresenter<PagePresenter>()

    val events = rememberEventFlow<PageEvent>()
    val pageState = presenter.present(state.page, events)


    PullToRefreshBox(
        isRefreshing = pageState is PageState.Loading,
        onRefresh = {
            events.tryEmit(PageEvent.Refresh)
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()),
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


@Composable
private fun PageLoadedContent(
    events: (TabEvent) -> Unit,
    pageState: PageState,
    pageEvents: EventFlow<PageEvent>
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    when (pageState) {
        is PageState.Content -> {
            Box(Modifier.fillMaxSize()) {

                val scrollToTopVisible by listState.sampleScrollingState()

                LazyColumn(
                    Modifier.fillMaxSize(),
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

                AutoScrollIndicator(
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
            in 1..4 -> MaterialTheme.typography.headlineLarge
            5 -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.headlineSmall
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