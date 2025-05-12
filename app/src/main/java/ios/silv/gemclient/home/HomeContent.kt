package ios.silv.gemclient.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.GeminiSettings
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.metroPresenter
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemclient.ui.components.TerminalSection
import ios.silv.gemclient.ui.components.TerminalSectionButton
import ios.silv.gemclient.ui.components.TerminalSectionDefaults
import ios.silv.gemclient.ui.rememberEventFlow

fun NavGraphBuilder.geminiHomeDestination() {
    composable<GeminiHome> {
        val presenter = metroPresenter<HomePresenter>()

        val eventsFlow = rememberEventFlow<HomeEvent>()
        val state = presenter.present(eventsFlow)

        GeminiHomeContent(
            eventsFlow,
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
            val contentColor = LocalContentColor.current
            TopAppBar(
                title = {
                    BasicText(
                        text = "Gemini Browser >",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 4.dp),
                        color = { contentColor },
                        autoSize = TextAutoSize.StepBased()
                    )
                },
                actions = {
                    val navigator = LocalNavigator.current
                    TerminalSectionButton(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(IntrinsicSize.Min)
                            .height(IntrinsicSize.Min),
                        containerColor = containerColor,
                        label = {
                            TerminalSectionDefaults.Label("settings", color = containerColor)
                        },
                        onClick = {
                            navigator.topLevelDest.tryEmit(GeminiSettings)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                        )
                    }
                    TerminalSectionButton(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .width(IntrinsicSize.Min)
                            .height(IntrinsicSize.Min),
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
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal
        ),
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        TerminalSection(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = TerminalSectionDefaults.horizontalPadding),
            label = {
                TerminalSectionDefaults.Label(
                    text = "home",
                    color = containerColor
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("HOME")
            }
        }
    }
}
