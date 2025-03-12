@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ActionDispatcher
import ios.silv.gemclient.base.createViewModel
import ios.silv.gemclient.ui.components.AutoScrollIndicator
import ios.silv.gemclient.ui.sampleScrollingState
import ios.silv.gemini.ContentNode
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.launch

fun NavGraphBuilder.geminiPageDestination() {
    composable<GeminiTab> { backStack ->
        val viewModel: GeminiTabViewModel = backStack.createViewModel {
            GeminiTabViewModel(it)
        }

        val state by viewModel.tabState.collectAsStateWithLifecycle()

        BackHandler {
            viewModel.dispatch {
                goBack()
            }
        }

        GeminiPageContent(
            dispatcher = viewModel,
            state = state
        )
    }
}

@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PreviewDoneGeminiPageContent() {

    val dispatcher = remember {
        object : ActionDispatcher<GeminiTabViewModelAction> {
            override fun dispatch(action: suspend GeminiTabViewModelAction.() -> Unit) {}
            override fun immediate(action: GeminiTabViewModelAction.() -> Unit) {}
        }
    }

    GeminiPageContent(
        dispatcher = dispatcher,
        state = TabState.Loaded.Done(
            page = Page(-1, -1, "gemini://testloadingstate.urmom", -1),
            nodes = remember {
                listOf(
                    UiNode(ContentNode.Line.Text("Text line")),
                    UiNode(
                        ContentNode.Line.Link(
                            "gemini://someurl",
                            url = "gemini://someurl",
                            "some url name"
                        )
                    )
                )
            }
        )
    )
}


@PreviewLightDark
@PreviewScreenSizes
@Composable
private fun PreviewLoadingGeminiPageContent() {

    val dispatcher = remember {
        object : ActionDispatcher<GeminiTabViewModelAction> {
            override fun dispatch(action: suspend GeminiTabViewModelAction.() -> Unit) {
            }

            override fun immediate(action: GeminiTabViewModelAction.() -> Unit) {
            }
        }
    }

    GeminiPageContent(
        dispatcher = dispatcher,
        state = TabState.Loaded.Loading(Page(-1, -1, "gemini://testloadingstate.urmom", -1))
    )
}

@Composable
private fun GeminiPageContent(
    dispatcher: ActionDispatcher<GeminiTabViewModelAction>,
    state: TabState,
) {
    PullToRefreshBox(
        isRefreshing = state is TabState.Idle,
        onRefresh = {
            dispatcher.dispatch {
                refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            is TabState.Loaded.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            is TabState.Loaded.Input -> {
                TabStateInputContent(dispatcher)
            }

            is TabState.Loaded.Done -> {
                TabStateDoneContent(dispatcher, state)
            }

            else -> Box(Modifier.fillMaxSize()) {
                Text(state.toString())
            }
        }
    }
}

@Composable
private fun TabStateInputContent(
    dispatcher: ActionDispatcher<GeminiTabViewModelAction>,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            trailingIcon = {
                IconButton(
                    onClick = {
                        dispatcher.dispatch {
                            submitInput(input)
                        }
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
private fun TabStateDoneContent(
    dispatcher: ActionDispatcher<GeminiTabViewModelAction>,
    state: TabState.Loaded.Done
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {

        val scrollToTopVisible by listState.sampleScrollingState()

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState
        ) {
            for ((node, key, contentType) in state.nodes) {
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
                        node.Render(dispatcher)
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

@Composable
private fun ContentNode.Render(
    dispatcher: ActionDispatcher<GeminiTabViewModelAction>,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (val node = this@Render) {
            is ContentNode.Error -> node.RenderError()
            is ContentNode.Line.Heading -> node.RenderHeading()
            is ContentNode.Line.Link -> node.RenderLink(
                onClick = {
                    dispatcher.dispatch {
                        loadPage(link = node.url)
                    }
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