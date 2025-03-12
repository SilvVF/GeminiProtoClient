package ios.silv.gemclient

import androidx.lifecycle.viewModelScope
import ios.silv.core_android.log.asLog
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.ViewModelActionHandler
import ios.silv.gemclient.dependency.DependencyAccessor
import ios.silv.gemclient.dependency.commonDeps
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

interface GeminiBottomBarAction {
    fun onSearchChanged(query: String)
    fun reorderTabs(from: Int, to: Int)
    fun goToTab(tab: Tab)
    suspend fun deleteTab(id: Long)
    suspend fun search(query: String, tabId: Long? = null)
}

data class BottomBarState(
    val tabs: List<Pair<Tab, Page?>> = emptyList(),
    val query: String = "",
)

class GeminiBottomBarViewModel @OptIn(DependencyAccessor::class) constructor(
    private val tabsRepo: TabsRepo = commonDeps.tabsRepo,
    private val navigator: ComposeNavigator = commonDeps.navigator,
) : GeminiBottomBarAction,
    ViewModelActionHandler<GeminiBottomBarAction>() {
    override val handler: GeminiBottomBarAction = this

    private val _state = MutableStateFlow(BottomBarState())
    val state: StateFlow<BottomBarState> get() = _state

    init {
        tabsRepo
            .observeTabsWithActivePage()
            .onEach { items ->
                _state.update { state ->
                    state.copy(
                        tabs = updateListPreserveOrder(state.tabs, items)
                    )
                }
            }
            .catch { logcat { it.asLog() } }
            .launchIn(viewModelScope)
    }

    private fun updateListPreserveOrder(
        prev: List<Pair<Tab, Page?>>,
        new: List<Pair<Tab, Page?>>
    ): List<Pair<Tab, Page?>> {

        val newValues = new.groupBy { (tab) -> tab.tid }

        val updated = prev.filter { (tab) -> newValues.containsKey(tab.tid) }
        val updatedKeys = updated.map { (tab) -> tab.tid }.toSet()
        val added = new.filter { (tab) -> tab.tid !in updatedKeys }

        return buildList {
            addAll(updated)
            addAll(added)
        }
    }

    override fun onSearchChanged(query: String) {
        _state.update { state ->
            state.copy(
                query = query,
            )
        }
    }

    override fun reorderTabs(from: Int, to: Int) {
        _state.update { state ->
            state.copy(
                tabs = state.tabs.toMutableList().apply {
                    if (
                        to in 0..state.tabs.lastIndex &&
                        from in 0..state.tabs.lastIndex
                    ) {
                        add(to, removeAt(from))
                    }
                }
            )
        }
    }

    override fun goToTab(tab: Tab) {
        navigator.navCmds.tryEmit {
            navigate(GeminiTab(id = tab.tid))
        }
    }

    override suspend fun deleteTab(id: Long) {
        tabsRepo.deleteTab(id)
    }

    override suspend fun search(query: String, tabId: Long?) {
        logcat { "searching $query $tabId" }
        val tab = tabsRepo.insertTab(query)

        navigator.navCmds.emit {
            navigate(GeminiTab(tab.tid))
        }
    }
}