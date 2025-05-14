package ios.silv.gemclient.bar

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
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
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.takahirom.rin.rememberRetained
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.R
import ios.silv.gemclient.bar.BarEvent.CreateBlankTab
import ios.silv.gemclient.bar.BarEvent.CreateNewPage
import ios.silv.gemclient.bar.BarEvent.CreateNewTab
import ios.silv.gemclient.bar.BarEvent.DeleteTab
import ios.silv.gemclient.bar.BarEvent.GoToTab
import ios.silv.gemclient.bar.BarEvent.ReorderTabs
import ios.silv.gemclient.bar.BarEvent.SearchChanged
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemclient.ui.components.TerminalSection
import ios.silv.gemclient.ui.components.TerminalSectionButton
import ios.silv.gemclient.ui.components.TerminalSectionDefaults
import ios.silv.gemclient.ui.isImeVisibleAsState
import ios.silv.gemclient.ui.nonGoogleRetardProgress
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
fun BottombarScaffold(
    state: BarState,
    events: EventFlow<BarEvent>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val barMode = rememberRetained { MutableTransitionState(BarMode.NONE) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState,
    ) { from, to ->
        logcat { "reordering $from, $to" }
        events.tryEmit(ReorderTabs(from.index, to.index))
    }

    val barModeTransition = rememberTransition(
        transitionState = barMode,
        label = "bar-mode-search-transition"
    )

    BackHandler(
        enabled = barMode.currentState != BarMode.NONE
    ) {
        focusManager.clearFocus()
        barMode.targetState = BarMode.NONE
    }

    val ime by isImeVisibleAsState()

    LaunchedEffect(ime) {
        barMode.targetState = if (ime) {
            BarMode.SEARCHING
        } else {
            BarMode.NONE
        }
    }


    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeContent.only(WindowInsetsSides.Bottom)
            )
    ) {
        Box(Modifier.weight(1f, true)) {
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
            barModeTransition.AnimatedVisibility(
                visible = { it == BarMode.EDITING },
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                TerminalSectionButton(
                    modifier = Modifier
                        .padding(end = TerminalSectionDefaults.horizontalPadding)
                        .padding(bottom = 6.dp)
                        .width(IntrinsicSize.Min)
                        .height(IntrinsicSize.Min),
                    label = {
                        TerminalSectionDefaults.Label("add")
                    },
                    onClick = {
                        events.tryEmit(CreateBlankTab)
                    },
                ) {
                    Box(Modifier.height(48.dp).width(92.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.showSearchbar,
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            BottomSearchBar(
                barModeTransition = barModeTransition,
                modifier = Modifier.fillMaxWidth(),
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
                    events.tryEmit(SearchChanged(it))
                },
                tabsCount = state.tabs.size,
                onSearch = {
                    val tab = state.activeTab
                    if (tab != null) {
                        events.tryEmit(CreateNewPage(tab.id))
                    } else {
                        events.tryEmit(CreateNewTab)
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
}

@Composable
private fun CloseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.close)
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
        contentPadding = WindowInsets.systemBars.asPaddingValues(),
    ) {
        itemsIndexed(
            items = state.tabs,
            key = { _, it -> it.first.id }
        ) { idx, (tab, activePage, previewImage) ->
            val swipeToDismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    when (it) {
                        SwipeToDismissBoxValue.StartToEnd,
                        SwipeToDismissBoxValue.EndToStart -> {
                            events.tryEmit(
                                DeleteTab(tab.id)
                            )
                            true
                        }

                        SwipeToDismissBoxValue.Settled -> false
                    }
                }
            )
            val isTop = state.activeTab?.id == tab.id

            TabPreviewItem(
                reorderableLazyGridState = reorderableLazyGridState,
                swipeToDismissState = swipeToDismissState,
                isTop = isTop,
                onClose = {
                    events.tryEmit(
                        DeleteTab(tab.id)
                    )
                },
                onSelected = {
                    barMode.targetState = BarMode.NONE
                    events.tryEmit(GoToTab(tab))
                },
                tab = tab,
                activePage = activePage,
                previewImage = previewImage,
                colors = colors,
                idx = idx
            )
        }
    }
}

@Composable
private fun LazyGridItemScope.TabPreviewItem(
    reorderableLazyGridState: ReorderableLazyGridState,
    tab: StableTab,
    previewImage: String?,
    activePage: StablePage?,
    swipeToDismissState: SwipeToDismissBoxState,
    isTop: Boolean,
    idx: Int,
    onClose: () -> Unit,
    onSelected: () -> Unit,
    colors: CardColors
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    ReorderableItem(
        reorderableLazyGridState,
        key = tab.id
    ) {
        SwipeToDismissBox(
            swipeToDismissState,
            gesturesEnabled = !reorderableLazyGridState.isAnyItemDragging,
            modifier = Modifier,
            backgroundContent = {}
        ) {
            TerminalSection(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = TerminalSectionDefaults.horizontalPadding)
                    .background(MaterialTheme.colorScheme.background),
                label = {
                    TerminalSectionDefaults.Label(
                        "tab-$idx"
                    )
                },
                borderColor = if (isTop) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                ElevatedCard(
                    interactionSource = interactionSource,
                    onClick = { onSelected() },
                    shape = RectangleShape,
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
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(2.dp),
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
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(previewImage)
                            .memoryCacheKey("${previewImage}_${tab.previewUpdatedAt}")
                            .diskCacheKey("${previewImage}_${tab.previewUpdatedAt}")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
        modifier = modifier
            .padding(horizontal = TerminalSectionDefaults.horizontalPadding),
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
        visible = { it != BarMode.SEARCHING }
    ) {
        IconButton(
            onClick = onHomeClicked
        ) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null
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
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
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
                    .clip(RectangleShape)
                    .height(32.dp)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface,
                        RectangleShape
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
                ),
                size = size
            )
        }
    )
}