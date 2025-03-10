@file:OptIn(ExperimentalMaterial3Api::class)

package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.rememberViewModel
import ios.silv.gemclient.dependency.ActionDispatcher
import ios.silv.gemini.ContentNode

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
                    ContentNode.Line.Link("gemini://someurl", url = "gemini://someurl", "some url name")
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
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = state.route)
                },
                actions = {
                    Button(onClick = {
                        dispatcher.dispatch {
                            refresh()
                        }
                    }
                    ) {
                        Text("refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (state) {
            is TabState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is TabState.Done -> {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = paddingValues) {
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
            }
        }
    }
}
