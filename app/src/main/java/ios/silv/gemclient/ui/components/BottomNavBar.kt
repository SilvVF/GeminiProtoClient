package ios.silv.gemclient.ui.components

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.ui.conditional


@SuppressLint("RestrictedApi")
@Composable
fun BottomSearchBarWrapper(
    navController: NavController,
    activeTabs: List<GeminiTab>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
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

    LaunchedEffect(backStackEntry) {
        when (backStackEntry?.destination?.route) {
            "${GeminiTab::class}" -> {
                val route = backStackEntry?.toRoute<GeminiTab>()
                searchText = route?.baseUrl ?: searchText
            }
        }
    }

    BackHandler(
        enabled = searching
    ) {
        focusManager.clearFocus()
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(editing, Modifier.weight(1f)) { isEditing ->
            if (isEditing) {
                val colors = CardDefaults.elevatedCardColors()
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(2),
                    contentPadding = WindowInsets.systemBars.asPaddingValues(),
                ) {
                    items(tabsOrder) {

                        val swipeToDismissState = rememberSwipeToDismissBoxState()
                        val isTop = remember(backStackEntry) {
                            runCatching {
                                backStackEntry?.toRoute<GeminiTab>() == it
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

                        SwipeToDismissBox(
                            swipeToDismissState,
                            backgroundContent = {}
                        ) {
                            ElevatedCard(
                                modifier = Modifier
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
                                        text = it.baseUrl,
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
            } else {
                content()
            }
        }
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
                            .onFocusChanged {
                                searching = it.hasFocus
                            },
                        value = searchText,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        onValueChange = {
                            searchText = it
                        },
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
                                    .clickable { editing = !editing }
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
}