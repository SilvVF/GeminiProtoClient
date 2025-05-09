package ios.silv.gemclient

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.MainTabEvent.*
import ios.silv.gemclient.ui.components.CloseIconButton
import ios.silv.gemclient.ui.components.FadingEndText
import ios.silv.gemclient.ui.conditional
import ios.silv.gemclient.ui.isImeVisibleAsState
import ios.silv.gemclient.ui.nonGoogleRetardProgress
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

enum class BarMode {
    EDITING,
    SEARCHING,
    NONE
}

val LocalBarMode =
    compositionLocalOf<MutableTransitionState<BarMode>> { error("not provided in scope") }

@Composable
fun GeminiBasePage(
    state: MainState,
    modifier: Modifier = Modifier,
    backStackEntry: NavBackStackEntry? = null,
    content: @Composable BoxScope.() -> Unit
) {

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState,
    ) { from, to ->
        logcat { "reordering $from, $to" }
        state.eventSink(ReorderTabs(from.index, to.index))
    }

    val ime by isImeVisibleAsState()

    val activeTab by remember(backStackEntry) {
        derivedStateOf {
            runCatching {
                backStackEntry?.toRoute<GeminiTab>()
            }
                .getOrNull()
        }
    }

    val barMode = remember { MutableTransitionState(BarMode.NONE) }
    val barModeTransition = rememberTransition(
        transitionState = barMode,
        label = "bar-mode-search-transition"
    )

    LaunchedEffect(ime) {
        barMode.targetState = if (ime) {
            BarMode.SEARCHING
        } else {
            BarMode.NONE
        }
    }

    BackHandler(
        enabled = barMode.currentState != BarMode.NONE
    ) {
        focusManager.clearFocus()
        barMode.targetState = BarMode.NONE
    }


    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            barModeTransition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { mode ->
                if (mode == BarMode.EDITING) {
                    val colors = CardDefaults.elevatedCardColors()
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = WindowInsets.systemBars.asPaddingValues(),
                    ) {
                        items(state.tabs, { it.first.tid }) { (tab, activePage) ->
                            val swipeToDismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    when (it) {
                                        SwipeToDismissBoxValue.StartToEnd,
                                        SwipeToDismissBoxValue.EndToStart-> {
                                            state.eventSink(
                                                DeleteTab(tab.tid)
                                            )
                                            true
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )
                            val isTop = activeTab?.id == tab.tid

                            TabPreviewItem(
                                reorderableLazyGridState = reorderableLazyGridState,
                                swipeToDismissState = swipeToDismissState,
                                isTop = isTop,
                                onClose = {
                                    state.eventSink(
                                        DeleteTab(tab.tid)
                                    )
                                },
                                onSelected = {
                                    state.eventSink(
                                        GoToTab(tab)
                                    )
                                    barMode.targetState = BarMode.NONE
                                },
                                tab = tab,
                                activePage = activePage,
                                colors = colors
                            )
                        }
                    }
                } else {
                    CompositionLocalProvider(
                        LocalBarMode provides barMode
                    ) {
                        content()
                    }
                }
            }
            barModeTransition.AnimatedVisibility(
                visible = { it == BarMode.EDITING },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = {
                        state.eventSink(CreateBlankTab)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = CircleShape,
                    content = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    }
                )
            }
        }
        BottomSearchBar(
            barModeTransition = barModeTransition,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            onFocusChanged = { state ->
                if (state.hasFocus) {
                    barMode.targetState = BarMode.SEARCHING
                }
            },
            searchText = state.query,
            onTextChanged = {
                state.eventSink(SearchChanged(it))
            },
            tabsCount = state.tabs.size,
            onSearch = {
               val tab = activeTab
                if (tab != null) {
                    state.eventSink(CreateNewPage(tab.id))
                } else {
                    state.eventSink(CreateNewTab)
                }
            },
            toggleEditing = {
                barMode.targetState = if (barMode.currentState == BarMode.EDITING) {
                    BarMode.NONE
                } else {
                    BarMode.EDITING
                }
            }
        )
    }
}

@Composable
private fun LazyGridItemScope.TabPreviewItem(
    reorderableLazyGridState: ReorderableLazyGridState,
    tab: Tab,
    activePage: Page?,
    swipeToDismissState: SwipeToDismissBoxState,
    isTop: Boolean,
    onClose: () -> Unit,
    onSelected: () -> Unit,
    colors: CardColors
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    ReorderableItem(
        reorderableLazyGridState,
        key = tab.tid
    ) {
        SwipeToDismissBox(
            swipeToDismissState,
            gesturesEnabled = !reorderableLazyGridState.isAnyItemDragging,
            modifier = Modifier,
            backgroundContent = {}
        ) {
            ElevatedCard(
                interactionSource = interactionSource,
                onClick = { onSelected() },
                modifier = Modifier
                    .graphicsLayer {
                        alpha =
                            EaseInOutSine.transform(swipeToDismissState.nonGoogleRetardProgress)
                    }
                    .longPressDraggableHandle(
                        interactionSource = interactionSource,
                        onDragStarted = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_START
                            )
                        },
                        onDragStopped = {
                            ViewCompat.performHapticFeedback(
                                view,
                                HapticFeedbackConstantsCompat.GESTURE_END
                            )
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(220.dp)
                    .conditional(
                        isTop,
                        ifTrue = Modifier.border(
                            3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ),
                        ifFalse = Modifier.border(
                            1.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small
                        )
                    ),
                colors = colors,
                shape = MaterialTheme.shapes.small,

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
    tabsCount: Int,
    toggleEditing: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier.padding(
                    bottom = with(LocalDensity.current) {
                        WindowInsets.systemBars.getBottom(this).toDp()
                    }
                ),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                barModeTransition.AnimatedVisibility(
                    visible = { it != BarMode.SEARCHING }
                ) {
                    IconButton(
                        onClick = {}
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = null
                        )
                    }
                }
                TextField(
                    modifier = Modifier
                        .padding(4.dp)
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged(onFocusChanged),
                    value = searchText,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = "Search or enter address",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSearch(searchText)
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        errorContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.inverseOnSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    )
                )
                barModeTransition.AnimatedVisibility(
                    visible = { it != BarMode.SEARCHING }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.small)
                                .height(32.dp)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    MaterialTheme.shapes.small
                                )
                                .clickable { toggleEditing() }
                                .aspectRatio(1.2f / 1f)
                                .padding(Dp.Hairline),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$tabsCount",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}