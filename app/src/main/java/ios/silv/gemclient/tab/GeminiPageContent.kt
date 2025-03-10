@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.rememberViewModel
import ios.silv.gemclient.dependency.ActionDispatcher
import ios.silv.gemclient.ui.components.AutoScrollIndicator
import ios.silv.gemclient.ui.sampleScrollingState
import ios.silv.gemini.ContentNode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

fun NavGraphBuilder.geminiPageDestination() {
    composable<GeminiTab> { backStack ->

        val args = backStack.toRoute<GeminiTab>()

        val viewModel: GeminiTabViewModel = rememberViewModel {
            GeminiTabViewModel(geminiTab = args)
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
            override fun dispatch(action: GeminiTabViewModelAction.() -> Unit) {}
        }
    }

    GeminiPageContent(
        dispatcher = dispatcher,
        state = TabState.Done(
            url = "gemini://testloadingstate.urmom",
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
            override fun dispatch(action: GeminiTabViewModelAction.() -> Unit) {}
        }
    }

    GeminiPageContent(
        dispatcher = dispatcher,
        state = TabState.Loading("gemini://testloadingstate.urmom")
    )
}

@Composable
private fun GeminiPageContent(
    dispatcher: ActionDispatcher<GeminiTabViewModelAction>,
    state: TabState,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = state is TabState.Loading,
        onRefresh = {
            dispatcher.dispatch {
                refresh()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            is TabState.Loading -> {}
            is TabState.Input -> {
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
            is TabState.Done -> {
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
        }
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