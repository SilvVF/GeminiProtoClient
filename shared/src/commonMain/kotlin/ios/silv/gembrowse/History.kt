@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package ios.silv.gembrowse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.insertSeparators
import androidx.paging.map
import io.ktor.http.Url
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class HistoryState(
    val query: String,
    val items: LazyPagingItems<UiHistoryModel>,
    val selected: SnapshotStateSet<Long>,
)

sealed interface HistoryEvent {
    data class UpdateQuery(val text: String) : HistoryEvent
    data class MultiSelectItem(val item: UiHistoryModel.HistoryModel) : HistoryEvent
    data object ResetSelect : HistoryEvent
    data object DeleteMultiSelected : HistoryEvent
    data class DeleteItem(val item: UiHistoryModel.HistoryModel) : HistoryEvent
    data class DeleteTimeRange(val range: ClearHistoryRange) : HistoryEvent
}

sealed class UiHistoryModel {
    class HistoryModel(
        val visitId: Long,
        val visitedAt: Long,
        val historyId: Long,
        val url: String,
        val title: String?
    ) : UiHistoryModel() {
        constructor(visit: VisitRow) : this(
            visit.visitId, visit.visitedAt, visit.historyId, visit.url, visit.title,
        )

        @Composable
        fun hostName() = remember(url, title) {
            runCatching {
                Url(url).host
            }.getOrDefault(url.take(10))
        }
    }

    class SeparatorModel(val date: String) : UiHistoryModel()

    companion object {
        val timeZone = TimeZone.currentSystemDefault()
        val dateFormat = LocalDateTime.Format {
            monthName(MonthNames.ENGLISH_FULL)
            chars(" ")
            day()
            chars(",")
            year()
        }
    }
}

private fun formatDateString(visitTime: Long): String {
    return Instant.fromEpochSeconds(visitTime)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .format(UiHistoryModel.dateFormat)
}

private fun shouldSeparate(
    before: UiHistoryModel.HistoryModel,
    after: UiHistoryModel.HistoryModel,
    timeZone: TimeZone = UiHistoryModel.timeZone,
): Boolean {
    val beforeInstant = Instant.fromEpochSeconds(before.visitedAt).toLocalDateTime(timeZone)
    val afterInstant = Instant.fromEpochSeconds(after.visitedAt).toLocalDateTime(timeZone)

    return beforeInstant.date != afterInstant.date
}

@OptIn(DependencyAccessor::class)
@Composable
fun historyPresenter(
    backStack: SnapshotStateStack<Route>,
    events: EventFlow<HistoryEvent>,
    historyDao: HistoryDao = commonDeps.historyDao,
): HistoryState {

    val scope = rememberRetainedCoroutineScope()
    var query by retain { mutableStateOf("") }
    val selectedIds = retain { mutableStateSetOf<Long>() }

    val pagingDataFlow = retain {
        snapshotFlow { query }
            .map { escapeLike(query).trim() }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                Pager(
                    config = PagingConfig(500),
                    pagingSourceFactory = {
                        historyDao.pagingVisitsMatching(query)
                    }
                ).flow
            }
            .map { pagingData ->
                pagingData.map { visitRow ->
                    UiHistoryModel.HistoryModel(visitRow)
                }
                    .insertSeparators { before, after ->
                        when {
                            before == null -> if (after != null) {
                                UiHistoryModel.SeparatorModel(
                                    date = formatDateString(after.visitedAt)
                                )
                            } else {
                                null
                            }

                            after == null -> UiHistoryModel.SeparatorModel("end of history")
                            shouldSeparate(before, after) -> {
                                UiHistoryModel.SeparatorModel(
                                    date = formatDateString(after.visitedAt)
                                )
                            }

                            else -> null
                        }
                    }
            }
            .cachedIn(scope)
    }

    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()


    EventEffect(events) { event ->
        when (event) {
            is HistoryEvent.UpdateQuery -> query = event.text
            is HistoryEvent.MultiSelectItem -> {
                if (!selectedIds.add(event.item.visitId)) {
                    selectedIds.remove(event.item.visitId)
                }
            }

            HistoryEvent.ResetSelect -> selectedIds.clear()
            is HistoryEvent.DeleteItem -> {
                historyDao.deleteHistory(event.item.historyId)
            }

            HistoryEvent.DeleteMultiSelected -> {
                historyDao.deleteHistory(selectedIds.toList())
                selectedIds.clear()
            }

            is HistoryEvent.DeleteTimeRange -> {
                val (to, from) = event.range.getToFrom()
                historyDao.clearRange(to, from)
            }
        }
    }

    return HistoryState(
        query = query,
        items = pagingItems,
        selected = selectedIds
    )
}

@Composable
fun HistoryScreen(
    state: HistoryState,
    events: EventFlow<HistoryEvent>,
    backStack: SnapshotStateStack<Route>,
) {
    var topBarState by retain { mutableStateOf(TopBarState.Default) }
    var showClearTimeRangeDialog by retain { mutableStateOf(false) }

    val handleBack = { barState: TopBarState ->
        when (barState) {
            TopBarState.Default -> backStack.pop()
            TopBarState.Edit -> {
                events.tryEmit(HistoryEvent.ResetSelect)
                topBarState = TopBarState.Default
            }

            TopBarState.Search -> topBarState = TopBarState.Default
        }
    }
    val handleItemSelect = { barState: TopBarState, item: UiHistoryModel.HistoryModel ->
        when (barState) {
            TopBarState.Default,
            TopBarState.Search -> {
            }

            TopBarState.Edit -> events.tryEmit(HistoryEvent.MultiSelectItem(item))
        }
    }
    val handleDelete = { barState: TopBarState ->
        when (barState) {
            TopBarState.Default,
            TopBarState.Search -> showClearTimeRangeDialog = true

            TopBarState.Edit -> events.tryEmit(HistoryEvent.DeleteMultiSelected)
        }
    }

    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = backStack.canPop,
        onBackCompleted = {
            handleBack(topBarState)
        },
    )

    if (showClearTimeRangeDialog) {
        ClearHistoryDialog(
            onDismiss = { showClearTimeRangeDialog = false },
            onConfirm = { range ->
                events.tryEmit(HistoryEvent.DeleteTimeRange(range))
                showClearTimeRangeDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            HistoryTopAppBar(
                onSearchSelected = {
                    topBarState = TopBarState.Search
                },
                onDeleteSelected = {
                    handleDelete(topBarState)
                },
                query = state.query,
                onQueryChange = { query ->
                    events.tryEmit(HistoryEvent.UpdateQuery(query))
                },
                onBack = {
                    handleBack(topBarState)
                },
                selected = state.selected.size,
                topBarState = topBarState
            )
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
                LazyColumn {
                    items(
                        count = state.items.itemCount,
                        key = state.items.itemKey { item ->
                            when (item) {
                                is UiHistoryModel.HistoryModel -> "visit-${item.visitId}"
                                is UiHistoryModel.SeparatorModel -> "header-${item.date}"
                            }
                        },
                        contentType = state.items.itemContentType { item ->
                            when (item) {
                                is UiHistoryModel.HistoryModel -> "visit"
                                is UiHistoryModel.SeparatorModel -> "header"
                            }
                        },
                    ) { idx ->
                        when (val historyItem = state.items[idx]) {
                            null -> Unit
                            is UiHistoryModel.SeparatorModel -> {
                                Text(
                                    historyItem.date,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }

                            is UiHistoryModel.HistoryModel -> {
                                HistoryListItem(
                                    item = historyItem,
                                    onLongClick = {
                                        topBarState = TopBarState.Edit
                                        handleItemSelect(topBarState, historyItem)
                                    },
                                    onCloseClick = {
                                        events.tryEmit(HistoryEvent.DeleteItem(historyItem))
                                    },
                                    onClick = {
                                        handleItemSelect(topBarState, historyItem)
                                    },
                                    selected = topBarState == TopBarState.Edit &&
                                            state.selected.contains(historyItem.visitId),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun PreviewHistoryListItem() {
    val item = remember {
        UiHistoryModel.HistoryModel(
            visitId = 0,
            visitedAt = Clock.System.now().epochSeconds,
            historyId = 1,
            url = "gemini//geminiprotocol.net/x/s/z",
            title = "tab title thing"
        )
    }
    val itemNoTitle = remember {
        UiHistoryModel.HistoryModel(
            visitId = 0,
            visitedAt = Clock.System.now().epochSeconds,
            historyId = 1,
            url = "gemini://geminiprotocol.net/x/s/z",
            title = null
        )
    }
    MaterialTheme {
        Surface {
            Column {
                listOf(item, itemNoTitle).fastForEachIndexed { i, item ->
                    HistoryListItem(
                        item,
                        onClick = {},
                        onLongClick = {},
                        onCloseClick = {},
                        selected = (i % 2) == 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryListItem(
    item: UiHistoryModel.HistoryModel,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onCloseClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                )
            }
        } else {
            Icon(
                modifier = Modifier.size(38.dp),
                imageVector = Icons.Filled.Tab,
                contentDescription = null,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.title ?: item.hostName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.url,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(2.dp))
        if (!selected) {
            IconButton(
                onClick = onCloseClick
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                )
            }
        }
    }
}

enum class ClearHistoryRange(val label: String) {
    LastHour("Last hour"),
    TodayAndYesterday("Today and yesterday"),
    Everything("Everything");

    fun getToFrom(): Pair<Long, Long> {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val today = now.toLocalDateTime(zone).date

        return when (this) {
            LastHour -> (now - 1.hours).epochSeconds to now.epochSeconds + 1
            TodayAndYesterday -> today
                .minus(1, DateTimeUnit.DAY)
                .atStartOfDayIn(zone).epochSeconds to now.epochSeconds + 1

            Everything -> 0L to Long.MAX_VALUE
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (ClearHistoryRange) -> Unit,
    modifier: Modifier = Modifier,
    initialRange: ClearHistoryRange = ClearHistoryRange.LastHour,
) {
    var selected by rememberSaveable { mutableStateOf(initialRange) }

    AlertDialog(
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary
            ),
        onDismissRequest = onDismiss,
        title = { Text("Clear history") },
        shape = RectangleShape,
        text = {
            Column(Modifier.selectableGroup()) {
                ClearHistoryRange.entries.forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = range == selected,
                                onClick = { selected = range },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TerminalRadioButton(
                            checked = range == selected,
                            onCheckedChange = { selected = range }
                        )
                        Box(Modifier.weight(1f)) {
                            Text(range.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


@Preview
@Composable
private fun PreviewHistoryTopAppBar() {
    var topBarState by remember { mutableStateOf(TopBarState.Default) }
    MaterialTheme {
        HistoryTopAppBar(
            query = "",
            onSearchSelected = { topBarState = TopBarState.Search },
            onDeleteSelected = {},
            onQueryChange = {},
            onBack = {
                when (topBarState) {
                    TopBarState.Default -> Unit
                    TopBarState.Edit -> topBarState = TopBarState.Default
                    TopBarState.Search -> topBarState = TopBarState.Default
                }
            },
            selected = 10,
            topBarState = topBarState,
        )
    }
}

private enum class TopBarState {
    Default,
    Edit,
    Search
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopAppBar(
    onSearchSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    query: String,
    selected: Int,
    topBarState: TopBarState,
) {
    Surface(
        color = animateColorAsState(
            targetValue = when (topBarState) {
                TopBarState.Default -> MaterialTheme.colorScheme.background
                TopBarState.Edit -> MaterialTheme.colorScheme.secondaryContainer
                TopBarState.Search -> MaterialTheme.colorScheme.surface
            }
        ).value
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(topBarState) { state ->
                when (state) {
                    TopBarState.Default,
                    TopBarState.Edit -> {
                        NavigationAppBar(
                            onBack = onBack,
                            onSearchSelected = onSearchSelected,
                            onDeleteSelected = onDeleteSelected,
                            selected = selected,
                            editing = state == TopBarState.Edit
                        )
                    }

                    TopBarState.Search -> {
                        VoiceSearchField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                                .padding(4.dp)
                                .height(TopAppBarDefaults.TopAppBarExpandedHeight),
                            query = query,
                            onQueryChange = onQueryChange,
                            onSearch = {},
                            onVoiceClick = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationAppBar(
    onSearchSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    selected: Int,
    editing: Boolean,
) {
    val contentColor = LocalContentColor.current
    TopAppBar(
        title = {
            if (editing) {
                Text("$selected selected")
            } else {
                BasicText(
                    text = "History >",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 4.dp),
                    color = { contentColor },
                    autoSize = TextAutoSize.StepBased(
                        maxFontSize = MaterialTheme.typography.titleLarge.fontSize
                    )
                )
            }
        },
        navigationIcon = {
            TerminalSectionButton(
                modifier = Modifier
                    .padding(start = 4.dp),
                label = {
                    TerminalSectionDefaults.Label(
                        if (editing) "cancel" else "cd .."
                    )
                },
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (editing) {
                TerminalSectionButton(
                    modifier = Modifier
                        .padding(start = 4.dp),
                    label = {
                        TerminalSectionDefaults.Label("share")
                    },
                    onClick = onSearchSelected
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                    )
                }
                TerminalSectionButton(
                    modifier = Modifier
                        .padding(start = 4.dp),
                    label = {
                        TerminalSectionDefaults.Label("opts")
                    },
                    onClick = onDeleteSelected
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = null,
                    )
                }
            } else {
                TerminalSectionButton(
                    modifier = Modifier
                        .padding(start = 4.dp),
                    label = {
                        TerminalSectionDefaults.Label("search")
                    },
                    onClick = onSearchSelected
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                }
                TerminalSectionButton(
                    modifier = Modifier
                        .padding(start = 4.dp),
                    label = {
                        TerminalSectionDefaults.Label("rm")
                    },
                    onClick = onDeleteSelected
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                    )
                }
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search history",
) {
    Surface(
        modifier = modifier,
        shape = SearchBarDefaults.inputFieldShape,
        tonalElevation = SearchBarDefaults.TonalElevation,
        shadowElevation = SearchBarDefaults.ShadowElevation,
    ) {
        SearchBarDefaults.InputField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            expanded = false,
            onExpandedChange = {},
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = onVoiceClick) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice input")
                    }
                }
            },
        )
    }
}