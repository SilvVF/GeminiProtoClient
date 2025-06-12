package ios.silv.gemclient.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.entry
import ios.silv.gemclient.BottomBarSinglePaneScene
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.tab.DraggableNavLayout
import ios.silv.gemclient.ui.components.TerminalSection
import ios.silv.gemclient.ui.components.TerminalSectionButton
import ios.silv.gemclient.ui.components.TerminalSectionDefaults
import ios.silv.shared.GeminiHome
import ios.silv.shared.GeminiSettings
import ios.silv.shared.home.HomeEvent
import ios.silv.shared.home.HomePresenter
import ios.silv.shared.home.HomeUiState
import ios.silv.shared.ui.EventFlow

fun EntryProviderBuilder<ios.silv.shared.NavKey>.geminiHomeDestination() {
    entry<GeminiHome>(
        metadata = mapOf(BottomBarSinglePaneScene.BOTTOM_BAR_KEY to true)
    ) {

        val presenter = metroViewModel<HomePresenter>(it)
        val state by presenter.models.collectAsStateWithLifecycle()

        GeminiHomeContent(
            eventsFlow = presenter.events,
            state = state,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiHomeContent(
    eventsFlow: EventFlow<HomeEvent>,
    state: HomeUiState,
) {
    val containerColor by animateColorAsState(
        if (state.incognito) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        label = "incog-background-color"
    )
    Scaffold(
        containerColor = containerColor,
        topBar = {
            HomeTopAppBar(containerColor, eventsFlow)
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal
        ),
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        DraggableNavLayout(
            modifier = Modifier.padding(paddingValues),
            navBlock = {
                TerminalSection(
                    label = {
                        TerminalSectionDefaults.Label("nav")
                    }
                ) {
                    Column(Modifier.fillMaxSize()) {

                    }
                }
            },
            stdOutBlock = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = TerminalSectionDefaults.horizontalPadding)
                ) {
                    TerminalItemsBlock(
                        urls = state.bookmarked,
                        label = "bookmarked",
                    ) { url ->
                        TextButton(
                            onClick = {
                                eventsFlow.tryEmit(HomeEvent.CreateTab(url))
                            }
                        ) {
                            Text(url)
                        }
                    }
                    TerminalItemsBlock(
                        urls = state.recentlyViewed,
                        label = "recently viewed",
                    ) { url ->
                        TextButton(
                            onClick = {
                                eventsFlow.tryEmit(HomeEvent.CreateTab(url))
                            }
                        ) {
                            Text(url)
                        }
                    }
                }
            }
        )
    }
}


@Composable
private fun TerminalItemsBlock(
    urls: List<String>,
    label: String,
    item: @Composable (url: String) -> Unit,
) {
    TerminalSection(
        modifier = Modifier.fillMaxWidth(),
        label = {
            TerminalSectionDefaults.Label(
                text = label
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(6.dp)
        ) {
            if (urls.isEmpty()) {
                Text(
                    "$label pages will appear here",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                )
            }
            urls.fastForEach {
                item(it)
            }
        }
    }
}

@Composable
private fun HomeTopAppBar(
    containerColor: Color,
    eventsFlow: EventFlow<HomeEvent>,
) {
    val contentColor = LocalContentColor.current
    TopAppBar(
        title = {
            BasicText(
                text = "Gemini Browser >",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
                color = { contentColor },
                autoSize = TextAutoSize.StepBased(
                    maxFontSize = MaterialTheme.typography.titleLarge.fontSize
                )
            )
        },
        actions = {
            val navigator = LocalNavigator.current
            TerminalSectionButton(
                modifier = Modifier
                    .padding(start = 4.dp),
                containerColor = containerColor,
                label = {
                    TerminalSectionDefaults.Label("settings", color = containerColor)
                },
                onClick = {
                    navigator.navCmds.tryEmit {
                        replaceAll(GeminiSettings)
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                )
            }
            TerminalSectionButton(
                modifier = Modifier
                    .padding(start = 4.dp),
                containerColor = containerColor,
                label = {
                    TerminalSectionDefaults.Label("incog", color = containerColor)
                },
                onClick = {
                    eventsFlow.tryEmit(HomeEvent.ToggleIncognito)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                )
            }
        }
    )
}
