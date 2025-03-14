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

interface GeminiMainAction {
    fun onSearchChanged(query: String)
    fun reorderTabs(from: Int, to: Int)
    fun goToTab(tab: Tab)
    suspend fun deleteTab(id: Long)
    suspend fun createNewTab(query: String?)
    suspend fun createNewPage(query: String, tabId: Long)
}

data class GeminiMainState(
    val tabs: List<Pair<Tab, Page?>> = emptyList(),
    val query: String = ""
)

class GeminiMainTabViewModel @OptIn(DependencyAccessor::class) constructor(
    private val tabsRepo: TabsRepo = commonDeps.tabsRepo,
    private val navigator: ComposeNavigator = commonDeps.navigator,
) : GeminiMainAction,
    ViewModelActionHandler<GeminiMainAction>() {
    override val handler: GeminiMainAction = this

    private val _state = MutableStateFlow(GeminiMainState())
    val state: StateFlow<GeminiMainState> get() = _state

    init {
        tabsRepo
            .observeTabsWithActivePage()
            .onEach { items ->
                logcat { "new items = $items" }
                _state.update { state ->
                    logcat { "prev state = $state" }
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
                    add(to, removeAt(from))
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

    override suspend fun createNewTab(query: String?) {
        val tab = tabsRepo.insertTab(query)

        navigator.navCmds.emit {
            navigate(GeminiTab(tab.tid))
        }
    }

    override suspend fun createNewPage(query: String, tabId: Long) {
        tabsRepo.insertPage(tabId, query)
    }
}