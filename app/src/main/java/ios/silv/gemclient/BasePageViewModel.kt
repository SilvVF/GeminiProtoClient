package ios.silv.gemclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.core_android.MutableStateFlowList
import ios.silv.core_android.log.asLog
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.ViewModelKey
import ios.silv.gemclient.dependency.ViewModelScope
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MainTabEvent : UiEvent {
    data class SearchChanged(val query: String) : MainTabEvent
    data class ReorderTabs(val from: Int, val to: Int) : MainTabEvent
    data class GoToTab(val tab: Tab) : MainTabEvent
    data class DeleteTab(val id: Long) : MainTabEvent
    data object CreateNewTab : MainTabEvent
    data object CreateBlankTab : MainTabEvent
    data class CreateNewPage(val tabId: Long) : MainTabEvent
}

data class MainState(
    val tabs: List<Pair<Tab, Page?>> = emptyList(),
    val query: String = "",
    val eventSink: (MainTabEvent) -> Unit
) : UiState

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(BasePageViewModel::class)
@Inject
class BasePageViewModel(
    private val tabsRepo: TabsRepo,
    private val navigator: ComposeNavigator,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val orderedTabs = MutableStateFlowList<Pair<Tab, Page?>>(emptyList())
    private val visibleTab = navigator.navControllerFlow.filterNotNull()
        .flatMapLatest { it.currentBackStackEntryFlow }
        .map { runCatching { it.toRoute<GeminiTab>() }.getOrNull() }
        .filterNotNull()

    private val eventSink = { query: String, event: MainTabEvent ->
        when (event) {
            is MainTabEvent.CreateNewPage -> createNewPage(query, event.tabId)
            is MainTabEvent.CreateNewTab -> createNewTab(query)
            is MainTabEvent.CreateBlankTab -> createNewTab(null)
            is MainTabEvent.DeleteTab -> deleteTab(event.id)
            is MainTabEvent.GoToTab -> goToTab(event.tab)
            is MainTabEvent.ReorderTabs -> reorderTabs(event.from, event.to)
            is MainTabEvent.SearchChanged -> onSearchChanged(event.query)
        }
    }

    val state = combine(
        queryFlow,
        orderedTabs
    ) { query, orderTabs ->
        MainState(
            tabs = orderTabs,
            query = query,
            eventSink = { eventSink(query, it) }
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            MainState { eventSink("", it) }
        )


    init {
        tabsRepo
            .observeTabsWithActivePage()
            .onEach { items ->
                logcat { "new items = $items" }
                orderedTabs.update { tabs ->
                    updateListPreserveOrder(tabs, items)
                }
            }
            .catch { logcat { it.asLog() } }
            .launchIn(viewModelScope)

        visibleTab.flatMapLatest { activeTab ->
            tabsRepo.observeTabWithActivePage(activeTab.id)
        }
            .filterNotNull()
            .onEach { (_, activePage) ->
                onSearchChanged(activePage?.url.orEmpty().ifEmpty { "gemini://" })
            }
            .launchIn(viewModelScope)
    }

    private fun updateListPreserveOrder(
        prev: List<Pair<Tab, Page?>>,
        new: List<Pair<Tab, Page?>>
    ): List<Pair<Tab, Page?>> {

        val newValues = new.groupBy { (tab) -> tab.tid }
        val updated = prev.mapNotNull { (tab) -> newValues[tab.tid]?.firstOrNull() }

        val updatedKeys = updated.map { (tab) -> tab.tid }.toSet()
        val added = new.filter { (tab) -> tab.tid !in updatedKeys }

        return buildList {
            addAll(updated)
            addAll(added)
        }.also {
            logcat { "preserveOrder = $it" }
        }
    }

    private fun onSearchChanged(query: String) {
        queryFlow.update { query }
    }

    private fun reorderTabs(from: Int, to: Int) {
        orderedTabs.update { tabs ->
            tabs.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
    }

    private fun goToTab(tab: Tab) {
        val sent = navigator.navCmds.tryEmit {
            navigate(GeminiTab(id = tab.tid))
        }
        logcat { "Going to tab $tab $sent" }
    }

    private fun deleteTab(id: Long) {
        viewModelScope.launch {
            tabsRepo.deleteTab(id)
        }
    }

    private fun createNewTab(query: String?) {
        viewModelScope.launch {
            val tab = tabsRepo.insertTab(query)

            navigator.navCmds.emit {
                navigate(GeminiTab(tab.tid))
            }
        }
    }

    private fun createNewPage(query: String, tabId: Long) {
        viewModelScope.launch {
            tabsRepo.insertPage(tabId, query)
        }
    }
}