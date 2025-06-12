package ios.silv.shared.bar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.core.let
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiHome
import ios.silv.shared.GeminiTab
import ios.silv.shared.MoleculeViewModel
import ios.silv.shared.PreviewCache
import ios.silv.shared.di.ViewModelKey
import ios.silv.shared.di.ViewModelScope
import ios.silv.shared.types.StablePage
import ios.silv.shared.types.StableTab
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@ExperimentalCoroutinesApi
@ContributesIntoMap(ViewModelScope::class, binding<ViewModel>())
@ViewModelKey(BarPresenter::class)
@Inject
class BarPresenter(
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val previewCache: PreviewCache,
) : MoleculeViewModel<BarEvent, BarState>() {

    private fun <T> updateListPreserveOrder(
        prev: List<Triple<StableTab, StablePage?, T>>,
        new: List<Triple<StableTab, StablePage?, T>>
    ): List<Triple<StableTab, StablePage?, T>> {

        val newValues = new.groupBy { (tab) -> tab.id }
        val updated = prev.mapNotNull { (tab) -> newValues[tab.id]?.firstOrNull() }

        val updatedKeys = updated.map { (tab) -> tab.id }.toSet()
        val added = new.filter { (tab) -> tab.id !in updatedKeys }

        return buildList {
            addAll(updated)
            addAll(added)
        }.also {
            logcat { "preserveOrder = $it" }
        }
    }

    private val tabsWithActivePageFlow = tabsDao.observeTabsWithActivePage()

    @Composable
    override fun models(events: EventFlow<BarEvent>): BarState {

        val backstack by navigator.navBackStackFlow.collectAsState()

        var query by remember { mutableStateOf("") }

        val orderedTabs = remember {
            mutableStateListOf<Triple<StableTab, StablePage?, String?>>()
        }
        val tabs by tabsWithActivePageFlow.collectAsState(emptyList())

        val visibleTab by produceState<GeminiTab?>(null) {
            snapshotFlow { backstack?.lastItemOrNull }.filterNotNull()
                .map { it as? GeminiTab }
                .onEach { value = it }
                .filterNotNull()
                .flatMapLatest {
                    logcat { "active tab $it" }
                    tabsDao.observeTabWithActivePage(it.id).filterNotNull()
                }
                .collect { (_, page) ->
                    query = page?.url.orEmpty()
                }
        }

        LaunchedEffect(tabs) {
            val newTabs = updateListPreserveOrder(
                orderedTabs,
                tabs.map { (tab, page) ->
                    Triple(
                        StableTab(tab),
                        StablePage(page),
                        page?.url?.let(previewCache::read)?.toString()
                    )
                }
            )
            logcat { "Created new tabs $newTabs" }

            orderedTabs.clear()
            orderedTabs.addAll(newTabs)
        }


        EventEffect(events) { event ->
            when (event) {
                BarEvent.CreateBlankTab -> {
                    val tab = tabsDao.insertTab(null)

                    navigator.navCmds.tryEmit {
                        push(GeminiTab(tab.tid))
                    }
                }

                is BarEvent.CreateNewPage -> {
                    tabsDao.insertPage(event.tabId, query)
                }

                BarEvent.CreateNewTab -> {
                    val tab = tabsDao.insertTab(query)

                    navigator.navCmds.tryEmit {
                        push(GeminiTab(tab.tid))
                    }
                }

                is BarEvent.DeleteTab -> {
                    tabsDao.deleteTab(event.id)
                }

                is BarEvent.GoToTab -> {
                    navigator.navCmds.tryEmit { push(GeminiTab(id = event.tab.id)) }
                }

                is BarEvent.ReorderTabs -> {
                    orderedTabs.add(
                        event.to,
                        orderedTabs.removeAt(event.from)
                    )
                }

                is BarEvent.SearchChanged -> query = event.query
                BarEvent.GoToHome -> {
                    navigator.navCmds.tryEmit {
                        replaceAll(GeminiHome)
                    }
                }
            }
        }

        return BarState(
            tabs = orderedTabs,
            query = query,
            activeTab = visibleTab,
            showSearchbar = true
        )
    }
}