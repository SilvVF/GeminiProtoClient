package ios.silv.gembrowse

import androidx.annotation.FloatRange
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

enum class BarMode {
    EDITING, SEARCHING, NONE
}

val LocalBarMode =
    compositionLocalOf<MutableTransitionState<BarMode>> { error("not provided in scope") }

@Stable
data class TabItem(
    val state: TabState,
    val previewLink: String? = null,
)

@OptIn(DependencyAccessor::class)
@Composable
fun searchPresenter(
    backStack: SnapshotStateStack<Route>,
    events: EventFlow<BarEvent>,
    tabsDao: TabDao = commonDeps.tabDao,
    db: AppDatabase = commonDeps.database,
    previewCache: PreviewCache = commonDeps.previewCache
): BarState {

    val scope = rememberRetainedCoroutineScope()
    var query by retain { mutableStateOf("") }

    val tabs by produceRetainedState(emptyList()) {
        tabsDao.observeTabStates().collect { states ->
            value = states.map { state ->
                TabItem(
                    state = state,
                    previewLink = previewCache.read(state.page?.url.orEmpty()).toString()
                )
            }
        }
    }

    val visibleItem by remember {
        derivedStateOf {
            val route = backStack.lastItemOrNull as? TabRoute
            tabs.firstOrNull { (state, _) -> state.tab.id == route?.id }
        }
    }

    val orderedTabs = retain { mutableStateListOf(*tabs.toTypedArray()) }
    LaunchedEffect(tabs) {
        Snapshot.withMutableSnapshot {
            orderedTabs.clear()
            orderedTabs.addAll(tabs)
            orderedTabs.sortBy { tabItem -> tabItem.state.tab.sortOrder }
        }
    }

    LaunchedEffect(visibleItem) {
        if (visibleItem != null) {
            query = visibleItem?.state?.page?.url.orEmpty()
        }
    }

    EventEffect(events) { event ->
        when (event) {
            BarEvent.CreateBlankTab -> {
                val tab = db.createTab(null)
                if (tab != null) {
                    backStack.push(TabRoute(tab.id))
                }
            }

            is BarEvent.CreateNewPage -> {
                db.navigate(event.tabId, query)
            }

            BarEvent.CreateNewTab -> {
                val tab = db.createTab(query) ?: return@EventEffect
                backStack.push(TabRoute(tab.id))
            }

            is BarEvent.DeleteTab -> {
                tabsDao.deleteTabById(event.id)
            }

            is BarEvent.GoToTab -> {
                backStack.push(TabRoute(id = event.tab.id))
            }

            is BarEvent.ReorderTabs -> {
                orderedTabs.add(event.to, orderedTabs.removeAt(event.from))
                scope.launch {
                    db.reorderTabs(orderedTabs.map { tabItem -> tabItem.state.tab.id })
                }
            }

            is BarEvent.SearchChanged -> query = event.query
            BarEvent.GoToHome -> {
                backStack.replaceAll(HomeRoute)
            }
        }
    }

    return BarState(
        tabs = orderedTabs,
        query = query,
        active = visibleItem,
        showSearchbar = true
    )
}

sealed interface BarEvent {
    data class SearchChanged(val query: String) : BarEvent
    data class ReorderTabs(val from: Int, val to: Int) : BarEvent
    data class GoToTab(val tab: TabEntity) : BarEvent
    data class DeleteTab(val id: Long) : BarEvent
    data object CreateNewTab : BarEvent
    data object CreateBlankTab : BarEvent
    data class CreateNewPage(val tabId: Long) : BarEvent
    data object GoToHome : BarEvent
}

data class BarState(
    val active: TabItem?,
    val showSearchbar: Boolean,
    val tabs: List<TabItem> = emptyList(),
    val query: String = "",
)

@Composable
fun BottombarScaffold(
    state: BarState,
    events: EventFlow<BarEvent>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val barMode = rememberSaveable(
        saver = Saver(
            save = { it.currentState.ordinal },
            restore = { MutableTransitionState(BarMode.entries[it]) })
    ) {
        MutableTransitionState(
            BarMode.NONE
        )
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState,
    ) { from, to ->
        events.tryEmit(BarEvent.ReorderTabs(from.index, to.index))
    }

    val barModeTransition = rememberTransition(
        transitionState = barMode, label = "bar-mode-search-transition"
    )
    val navState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navState, isBackEnabled = true, onBackCompleted = {
            focusManager.clearFocus()
            barMode.targetState = BarMode.NONE
        })

    val ime by isImeVisibleAsState()

    LaunchedEffect(ime) {
        barMode.targetState = if (ime) {
            BarMode.SEARCHING
        } else {
            BarMode.NONE
        }
    }
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomSearchBar(
                barModeTransition = barModeTransition,
                modifier = Modifier
                    .windowInsetsPadding(BottomAppBarDefaults.windowInsets)
                    .windowInsetsPadding(WindowInsets.ime)
                    .fillMaxWidth()
                    .padding(BottomAppBarDefaults.ContentPadding),
                focusRequester = focusRequester,
                onFocusChanged = { focusState ->
                    if (focusState.hasFocus) {
                        barMode.targetState = BarMode.SEARCHING
                    }
                },
                onHomeClicked = {
                    events.tryEmit(BarEvent.GoToHome)
                },
                searchText = state.query,
                onTextChanged = {
                    events.tryEmit(BarEvent.SearchChanged(it))
                },
                tabsCount = state.tabs.size,
                onSearch = {
                    val active = state.active
                    if (active != null) {
                        events.tryEmit(BarEvent.CreateNewPage(active.state.tab.id))
                    } else {
                        events.tryEmit(BarEvent.CreateNewTab)
                    }
                },
                toggleEditing = {
                    barMode.targetState = if (barMode.currentState == BarMode.EDITING) {
                        BarMode.NONE
                    } else {
                        BarMode.EDITING
                    }
                })
        },
        floatingActionButton = {
            barModeTransition.AnimatedVisibility(
                visible = { it == BarMode.EDITING },
            ) {
                TerminalSectionButton(
                    label = {
                        TerminalSectionDefaults.Label("add")
                    },
                    onClick = {
                        events.tryEmit(BarEvent.CreateBlankTab)
                    },
                ) {
                    Box(Modifier.width(92.dp).height(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }) { paddingValues ->
        Box(
            Modifier.padding(paddingValues)
        ) {
            CompositionLocalProvider(
                LocalBarMode provides barMode
            ) {
                content()
            }
            barModeTransition.AnimatedVisibility(
                modifier = Modifier.matchParentSize(),
                visible = { it == BarMode.EDITING },
            ) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TabReorderGrid(
                        state = state,
                        events = events,
                        lazyGridState = lazyGridState,
                        barMode = barMode,
                        reorderableLazyGridState = reorderableLazyGridState
                    )
                }
            }
        }
    }
}

@Composable
private fun CloseIconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick, modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
        )
    }
}

@Composable
private fun TabReorderGrid(
    state: BarState,
    events: EventFlow<BarEvent>,
    reorderableLazyGridState: ReorderableLazyGridState,
    barMode: MutableTransitionState<BarMode>,
    lazyGridState: LazyGridState,
) {
    val colors = CardDefaults.elevatedCardColors()

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        state = lazyGridState,
        columns = GridCells.Fixed(2),
        contentPadding = WindowInsets.systemBars.asPaddingValues()
    ) {
        itemsIndexed(
            items = state.tabs,
            key = { _, tabItem -> tabItem.state.tab.id }
        ) { idx, (tabState, previewLink) ->
            val swipeToDismissState = rememberSwipeToDismissBoxState()
            val isTop = state.active?.state?.tab?.id == tabState.tab.id

            LaunchedEffect(swipeToDismissState.settledValue) {
                when (swipeToDismissState.settledValue) {
                    SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart -> {
                        events.tryEmit(
                            BarEvent.DeleteTab(tabState.tab.id)
                        )
                    }

                    else -> Unit
                }
            }

            TabPreviewItem(
                reorderableLazyGridState = reorderableLazyGridState,
                swipeToDismissState = swipeToDismissState,
                isTop = isTop,
                onClose = {
                    events.tryEmit(
                        BarEvent.DeleteTab(tabState.tab.id)
                    )
                },
                onSelected = {
                    barMode.targetState = BarMode.NONE
                    events.tryEmit(BarEvent.GoToTab(tabState.tab))
                },
                tab = tabState.tab,
                activePage = tabState.page,
                previewImage = previewLink,
                colors = colors,
                idx = idx
            )
        }
    }
}

@Composable
private fun LazyGridItemScope.TabPreviewItem(
    reorderableLazyGridState: ReorderableLazyGridState,
    tab: TabEntity,
    previewImage: String?,
    activePage: PageEntity?,
    swipeToDismissState: SwipeToDismissBoxState,
    isTop: Boolean,
    idx: Int,
    onClose: () -> Unit,
    onSelected: () -> Unit,
    colors: CardColors
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    ReorderableItem(
        reorderableLazyGridState,
        key = tab.id
    ) {
        SwipeToDismissBox(
            state = swipeToDismissState,
            gesturesEnabled = !reorderableLazyGridState.isAnyItemDragging,
            modifier = Modifier,
            backgroundContent = {}) {
            TerminalSection(
                modifier = Modifier.padding(horizontal = TerminalSectionDefaults.horizontalPadding)
                    .background(MaterialTheme.colorScheme.background), label = {
                    TerminalSectionDefaults.Label(
                        "tab-$idx"
                    )
                }, borderColor = if (isTop) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                ElevatedCard(
                    interactionSource = interactionSource,
                    onClick = { onSelected() },
                    shape = RectangleShape,
                    modifier = Modifier.graphicsLayer {
                        alpha = EaseInOutSine.transform(swipeToDismissState.actualProgress)
                    }.longPressDraggableHandle(
                        interactionSource = interactionSource,
                        onDragStarted = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.GestureThresholdActivate
                            )
                        },
                        onDragStopped = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.GestureEnd
                            )
                        },
                    ).fillMaxWidth().height(220.dp).padding(2.dp),
                    colors = colors
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FadingEndText(
                            modifier = Modifier.weight(1f),
                            text = activePage?.url.orEmpty().ifBlank { "gemini://" },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            fadeColor = colors.containerColor
                        )
                        CloseIconButton(onClose)
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(previewImage)
                            .memoryCacheKey("${previewImage}_${tab.thumbnailUpdatedAt}")
                            .diskCacheKey("${previewImage}_${tab.thumbnailUpdatedAt}")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSearchBar(
    barModeTransition: Transition<BarMode>,
    modifier: Modifier,
    focusRequester: FocusRequester,
    onFocusChanged: (state: FocusState) -> Unit,
    searchText: String,
    onTextChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onHomeClicked: () -> Unit,
    tabsCount: Int,
    toggleEditing: () -> Unit
) {
    val barBorder by barModeTransition.animateColor {
        if (it == BarMode.SEARCHING) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondary
    }
    TerminalSection(
        modifier = modifier.padding(horizontal = TerminalSectionDefaults.horizontalPadding),
        label = {
            TerminalSectionDefaults.Label(
                text = "std-in"
            )
        },
        borderColor = barBorder
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarContent(
                barModeTransition,
                focusRequester,
                onFocusChanged,
                searchText,
                onTextChanged,
                onHomeClicked,
                onSearch,
                tabsCount,
                toggleEditing
            )
        }
    }
}

@Composable
fun RowScope.BottomBarContent(
    barModeTransition: Transition<BarMode>,
    focusRequester: FocusRequester,
    onFocusChanged: (state: FocusState) -> Unit,
    searchText: String,
    onTextChanged: (String) -> Unit,
    onHomeClicked: () -> Unit,
    onSearch: (String) -> Unit,
    tabsCount: Int,
    toggleEditing: () -> Unit
) {
    barModeTransition.AnimatedVisibility(
        visible = { it != BarMode.SEARCHING }) {
        IconButton(
            onClick = onHomeClicked
        ) {
            Icon(
                imageVector = Icons.Rounded.Home, contentDescription = null
            )
        }
    }
    OutlinedTextField(
        modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester)
            .onFocusChanged(onFocusChanged),
        value = searchText,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
        ),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        onValueChange = onTextChanged,
        placeholder = {
            Text(
                text = "Search or enter address", style = MaterialTheme.typography.labelMedium
            )
        },
        keyboardActions = KeyboardActions(
            onDone = {
                onSearch(searchText)
            }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        )
    )
    barModeTransition.AnimatedVisibility(
        visible = { it != BarMode.SEARCHING }) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.minimumInteractiveComponentSize().clip(RectangleShape).height(32.dp)
                    .border(
                        2.dp, MaterialTheme.colorScheme.onSurface, RectangleShape
                    ).clickable { toggleEditing() }.aspectRatio(1.2f / 1f).padding(Dp.Hairline),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$tabsCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert, contentDescription = null
                )
            }
        }
    }
}

@Composable
fun FadingEndText(
    text: String,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    @FloatRange(from = 0.0, to = 1.0) fadeStartPct: Float = 0.6f,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, fadeColor),
                    startX = size.width * fadeStartPct,
                    endX = size.width
                ), size = size
            )
        })
}
