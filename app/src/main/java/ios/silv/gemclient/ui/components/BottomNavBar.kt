package ios.silv.gemclient.ui.components

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.ui.conditional
import ios.silv.gemclient.ui.nonGoogleRetardProgress
import kotlinx.coroutines.CoroutineScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState


@SuppressLint("RestrictedApi")
@Composable
fun BottomSearchBarWrapper(
    navController: NavController,
    activeTabs: List<GeminiTab>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val tabsOrder = remember {
        mutableStateListOf(*activeTabs.toTypedArray())
    }

    var searchText by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState,
    ) { from, to ->
        tabsOrder.add(to.index, tabsOrder.removeAt(from.index))
    }


    LaunchedEffect(backStackEntry) {
        when (backStackEntry?.destination?.route) {
            "${GeminiTab::class}" -> {
                val route = backStackEntry?.toRoute<GeminiTab>()
                searchText = route?.baseUrl ?: searchText
            }
        }
    }

    BackHandler(
        enabled = editing
    ) {
        editing = false
    }

    BackHandler(
        enabled = searching
    ) {
        focusManager.clearFocus()
        searching = false
    }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = editing,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { isEditing ->
                if (isEditing) {
                    val colors = CardDefaults.elevatedCardColors()
                    LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyGridState,
                        columns = GridCells.Fixed(2),
                        contentPadding = WindowInsets.systemBars.asPaddingValues(),
                    ) {
                        items(tabsOrder, key = { "${it.id}${it.baseUrl}" }) { tab ->
                            val swipeToDismissState = rememberSwipeToDismissBoxState()
                            val isTop = remember(backStackEntry) {
                                runCatching {
                                    backStackEntry?.toRoute<GeminiTab>() == tab
                                }.getOrDefault(false)
                            }

                            LaunchedEffect(swipeToDismissState.currentValue) {
                                if (swipeToDismissState.currentValue in listOf(
                                        SwipeToDismissBoxValue.StartToEnd,
                                        SwipeToDismissBoxValue.EndToStart
                                    )
                                ) {
                                    swipeToDismissState.reset()
                                }
                            }

                            TabPreviewItem(
                                reorderableLazyGridState = reorderableLazyGridState,
                                tab = tab,
                                swipeToDismissState = swipeToDismissState,
                                isTop = isTop,
                                colors = colors
                            )
                        }
                    }
                } else {
                    content()
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = editing,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = {},
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
            searching = searching,
            modifier = Modifier.fillMaxWidth(),
            focusRequester = focusRequester,
            onFocusChanged = { state ->
                searching = state.hasFocus
            },
            searchText = searchText,
            onTextChanged = { searchText = it },
            activeTabs = activeTabs,
            toggleEditing = { editing = !editing }
        )
    }
}

@Composable
private fun LazyGridItemScope.TabPreviewItem(
    reorderableLazyGridState: ReorderableLazyGridState,
    tab: GeminiTab,
    swipeToDismissState: SwipeToDismissBoxState,
    isTop: Boolean,
    colors: CardColors
) {
    val view = LocalView.current
    ReorderableItem(
        reorderableLazyGridState,
        key = "${tab.id}${tab.baseUrl}"
    ) {
        SwipeToDismissBox(
            swipeToDismissState,
            gesturesEnabled = !reorderableLazyGridState.isAnyItemDragging,
            modifier = Modifier,
            backgroundContent = {}
        ) {
            ElevatedCard(
                modifier = Modifier
                    .graphicsLayer {
                        alpha =
                            EaseInOutSine.transform(swipeToDismissState.nonGoogleRetardProgress)
                    }
                    .longPressDraggableHandle(
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
                        text = tab.baseUrl,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        fadeColor = colors.containerColor
                    )
                    CloseIconButton({})
                }
            }
        }
    }
}

@Composable
private fun BottomSearchBar(
    searching: Boolean,
    modifier: Modifier,
    focusRequester: FocusRequester,
    onFocusChanged: (state: FocusState) -> Unit,
    searchText: String,
    onTextChanged: (String) -> Unit,
    activeTabs: List<GeminiTab>,
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
                AnimatedVisibility(!searching) {
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
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    onValueChange = onTextChanged,
                    placeholder = {
                        Text(
                            text = "Search or enter address",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
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
                AnimatedVisibility(!searching) {
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
                                "${activeTabs.size}",
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