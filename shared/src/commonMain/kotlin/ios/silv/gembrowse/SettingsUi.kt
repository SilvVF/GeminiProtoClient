package ios.silv.gembrowse

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import ios.silv.gembrowse.NavLayoutAnchors.End
import ios.silv.gembrowse.NavLayoutAnchors.Start
import ios.silv.gembrowse.Settings.Companion.Keys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed interface SettingsEvent {
    data class ChangeTheme(val theme: Theme) : SettingsEvent
    data object ToggleIncognito : SettingsEvent
    data class ChangeAppTheme(val theme: AppTheme) : SettingsEvent
    data object NavigateHome : SettingsEvent
}

data class SettingsState(
    val incognito: Boolean,
    val appTheme: AppTheme,
    val theme: Theme
)

@OptIn(DependencyAccessor::class)
@Composable
fun settingsPresenter(
    events: EventFlow<SettingsEvent>,
    backStack: SnapshotStateStack<Route>,
    settingsStore: Settings = commonDeps.settingsStore
): SettingsState {
    val incognito by settingsStore.incognito.collectAsRetainedState()
    val theme by settingsStore.theme.collectAsRetainedState()
    val appTheme by settingsStore.appTheme.collectAsRetainedState()

    EventEffect(events) { event ->
        when (event) {
            is SettingsEvent.ChangeAppTheme -> settingsStore.edit {
                it[Keys.appTheme] = event.theme.ordinal
            }

            is SettingsEvent.ChangeTheme -> settingsStore.edit {
                it[Keys.darkMode] = event.theme.ordinal
            }

            SettingsEvent.ToggleIncognito -> settingsStore.edit {
                it[Keys.incognito] = !incognito
            }

            SettingsEvent.NavigateHome -> backStack.replaceAll(HomeRoute)
        }
    }

    return SettingsState(
        incognito = incognito,
        theme = theme,
        appTheme = appTheme
    )
}

private data class SettingsListItem(
    val label: String,
    val sectionContent: @Composable (state: SettingsState, events: EventFlow<SettingsEvent>) -> Unit
)

private val settingsItems = listOf(
    SettingsListItem(
        "theme",
        sectionContent = { state, events ->
            SettingsRadioButtonItems(
                items = Theme.entries,
                modifier = Modifier.fillMaxWidth(),
                selected = state.theme,
                onSelected = {
                    events.tryEmit(SettingsEvent.ChangeTheme(it))
                },
            ) {
                Text(it.name)
            }
        }
    ),
    SettingsListItem(
        "color-theme",
        sectionContent = { state, events ->
            SettingsRadioButtonItems(
                modifier = Modifier.fillMaxWidth(),
                items = AppTheme.entries,
                selected = state.appTheme,
                onSelected = {
                    events.tryEmit(SettingsEvent.ChangeAppTheme(it))
                },
            ) {
                Text(it.name)
            }
        }
    ),
    SettingsListItem(
        "incognito",
        sectionContent = { state, events ->
            SettingsCheckBoxItem(
                modifier = Modifier.fillMaxWidth(),
                checked = state.incognito,
                toggle = {
                    events.tryEmit(SettingsEvent.ToggleIncognito)
                }
            ) {
                Text("incognito")
            }
        }
    )
)

@ExperimentalMaterial3Api
@Composable
fun SettingsContent(
    state: SettingsState,
    events: EventFlow<SettingsEvent>,
) {
    val scope = rememberCoroutineScope()
    val navDragState = rememberNavLayoutDraggableState()
    val settingsListState = rememberLazyListState()

    var blinkLabel by remember { mutableStateOf<String?>(null) }
    val color = remember { Animatable(Color.Transparent) }
    val primary = MaterialTheme.colorScheme.primaryContainer

    if (blinkLabel != null) {
        LaunchedEffect(blinkLabel) {
            try {
                val spec = tween<Color>(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
                with(color) {
                    repeat(2) {
                        animateTo(primary, spec)
                        animateTo(Color.Transparent, spec)
                    }
                    animateTo(primary)
                }

                delay(2.seconds)
                blinkLabel = null
            } finally {
                color.snapTo(Color.Transparent)
            }
        }
    }

    Scaffold(
        topBar = {
            val contentColor = LocalContentColor.current
            TopAppBar(
                title = {
                    BasicText(
                        text = "Settings >",
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
                        modifier = Modifier.padding(start = 4.dp),
                        label = {
                            TerminalSectionDefaults.Label("home")
                        },
                        onClick = {
                            events.tryEmit(SettingsEvent.NavigateHome)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = null,
                        )
                    }
                    TerminalSectionButton(
                        modifier = Modifier.padding(start = 4.dp),
                        label = {
                            TerminalSectionDefaults.Label("nav")
                        },
                        onClick = {
                            scope.launch {
                                navDragState.animateTo(
                                    when (navDragState.targetValue) {
                                        Start -> End
                                        End -> Start
                                    }
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        DraggableNavLayout(
            draggableState = navDragState,
            modifier = Modifier.padding(paddingValues),
            navBlock = {
                TerminalSection(
                    label = {
                        TerminalSectionDefaults.Label("nav")
                    }
                ) {
                    LazyColumn(Modifier.fillMaxHeight()) {
                        itemsIndexed(settingsItems) { i, item ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        settingsListState.animateScrollToItem(i)
                                        blinkLabel = item.label
                                    }
                                },
                                shape = RectangleShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            stdOutBlock = {
                LazyColumn(
                    state = settingsListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = TerminalSectionDefaults.horizontalPadding)
                ) {
                    items(
                        items = settingsItems,
                        key = { item -> item.label }
                    ) { item ->
                        TerminalSection(
                            label = {
                                TerminalSectionDefaults.Label(item.label)
                            }
                        ) {
                            Box(
                                modifier = if (blinkLabel == item.label) {
                                    Modifier.background(color.value)
                                } else {
                                    Modifier
                                }
                            ) {
                                item.sectionContent(state, events)
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun <T> SettingsRadioButtonItems(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (item: T) -> Unit,
) {
    Column(
        modifier.selectableGroup(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        items.fastForEach { item ->
            Row(
                modifier = Modifier
                    .selectable(
                        selected = item == selected,
                        onClick = { onSelected(item) },
                        role = Role.RadioButton,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TerminalRadioButton(
                    checked = item == selected,
                    onCheckedChange = { onSelected(item) }
                )
                Box(Modifier.weight(1f)) {
                    label(item)
                }
            }
        }
    }
}

@Composable
private fun SettingsCheckBoxItem(
    checked: Boolean,
    toggle: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clickable { toggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TerminalCheckBox(
            checked = checked,
            onCheckedChange = { toggle() }
        )
        Box(Modifier.weight(1f)) {
            label()
        }
    }
}