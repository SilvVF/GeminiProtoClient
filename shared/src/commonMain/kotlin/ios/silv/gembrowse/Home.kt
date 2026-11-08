package ios.silv.gembrowse

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import ios.silv.logcat.logcat
import kotlinx.coroutines.flow.map
import kotlin.collections.emptyList

data class HomeUiState(
    val incognito: Boolean = false,
    val recentlyViewed: List<String>,
    val bookmarked: List<String>
)

sealed interface HomeEvent {
    data object ToggleIncognito : HomeEvent
    data class CreateTab(val url: String?) : HomeEvent
}

@OptIn(DependencyAccessor::class)
@Composable
fun homePresenter(
    backStack: SnapshotStateStack<Route>,
    events: EventFlow<HomeEvent>,
    db: AppDatabase = commonDeps.database,
    historyDao: HistoryDao = commonDeps.historyDao,
    bookmarkDao: BookmarkDao = commonDeps.bookmarkDao,
    settings: Settings = commonDeps.settingsStore,
): HomeUiState {
    var incognito by retain { mutableStateOf(false) }

    val recent by produceRetainedState(emptyList(), historyDao) {
        historyDao.observeRecent(10).collect { entities -> value = entities.map { it.url } }
    }

    val bookmarked by produceRetainedState(emptyList(), bookmarkDao) {
        bookmarkDao.observeAll().collect { bookmarked ->
            value = bookmarked.map { it.url }
        }
    }

    EventEffect(events) { event ->
        when (event) {
            is HomeEvent.CreateTab -> {
                val tab = db.createTab(event.url)
                requireNotNull(tab)
                backStack.push(TabRoute(tab.id))
            }

            HomeEvent.ToggleIncognito -> incognito = !incognito
        }
    }

    return HomeUiState(
        incognito = incognito,
        recentlyViewed = recent,
        bookmarked = bookmarked
    )
}

@Composable
fun HomeContent(
    eventsFlow: EventFlow<HomeEvent>,
    backStack: SnapshotStateStack<Route>,
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
            HomeTopAppBar(containerColor, eventsFlow, backStack)
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
                    Button(
                        onClick = {
                            eventsFlow.tryEmit(HomeEvent.CreateTab("gemini://geminiprotocol.net/docs/"))
                        },
                    ) {
                        Text("gemini://geminiprotocol.net/docs/")
                    }
                    Button(
                        onClick = {
                            eventsFlow.tryEmit(HomeEvent.CreateTab("gemini://gemi.dev/tests/220px-Cat_flap.jpg"))
                        },
                    ) {
                        Text("gemini://gemi.dev/tests/220px-Cat_flap.jpg")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    containerColor: Color,
    eventsFlow: EventFlow<HomeEvent>,
    backStack: SnapshotStateStack<Route>,
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
            TerminalSectionButton(
                modifier = Modifier
                    .padding(start = 4.dp),
                containerColor = containerColor,
                label = {
                    TerminalSectionDefaults.Label("settings", color = containerColor)
                },
                onClick = {
                    backStack.push(SettingsRoute)
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
            TerminalSectionButton(
                modifier = Modifier
                    .padding(start = 4.dp),
                containerColor = containerColor,
                label = {
                    TerminalSectionDefaults.Label("history", color = containerColor)
                },
                onClick = {
                    backStack.push(HistoryRoute)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                )
            }
        }
    )
}