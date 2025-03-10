@file:OptIn(ExperimentalMaterial3Api::class)

package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    ContentNode.Line.Text("Text line"),
                    ContentNode.Line.Link(
                        "gemini://someurl",
                        url = "gemini://someurl",
                        "some url name"
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
            is TabState.Done -> {
                Box(Modifier.fillMaxSize()) {

                    val scrollToTopVisible by produceState(false) {
                        combine(
                            snapshotFlow { listState.firstVisibleItemIndex },
                            snapshotFlow { listState.isScrollInProgress },
                            ::Pair
                        )
                            .onEach { (visibleIdx, scrolling) ->
                                if (scrolling) value = true
                                if (visibleIdx == 0) value = false
                            }
                            .sample(800.milliseconds)
                            .collect { (visibleIdx, scrolling) ->
                                value = visibleIdx > 0 && scrolling
                            }
                    }

                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(state.nodes) { node ->
                            when (node) {
                                is ContentNode.Error -> {
                                    Text(node.message)
                                }

                                is ContentNode.Line.Link -> {
                                    TextButton(
                                        onClick = {
                                            dispatcher.dispatch {
                                                loadPage(link = node.url)
                                            }
                                        }
                                    ) {
                                        Text(node.name)
                                    }
                                }

                                is ContentNode.Line -> {
                                    Text(node.raw)
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
