package ios.silv.gemclient.bar

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.rememberRetained
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsDao
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import ios.silv.sqldelight.Page
import ios.silv.sqldelight.Tab
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(BarPresenter::class)
@Inject
class BarPresenter(
    private val tabsDao: TabsDao,
    private val navigator: ComposeNavigator,
    private val navController: NavController,
) : Presenter {

    private fun updateListPreserveOrder(
        prev: List<Pair<StableTab, StablePage?>>,
        new: List<Pair<StableTab, StablePage?>>
    ): List<Pair<StableTab, StablePage?>> {

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

    @Composable
    fun present(events: EventFlow<BarEvent>): BarState {

        var query by rememberRetained { mutableStateOf("") }

        val orderedTabs = rememberRetained {
            mutableStateListOf<Pair<StableTab, StablePage?>>()
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()

        val visibleTab by produceState<GeminiTab?>(null) {
            snapshotFlow { navBackStackEntry }
                .filterNotNull()
                .map { it.toRoute<GeminiTab>() }
                .catch { value = null }
                .collect { value = it }
        }

        val barMode = rememberRetained { MutableTransitionState(BarMode.NONE) }

        if (visibleTab != null) {
            LaunchedEffect(visibleTab) {
                tabsDao.observeTabWithActivePage(visibleTab?.id ?: return@LaunchedEffect)
                    .filterNotNull()
                    .collect { (_, activePage) ->
                        if (activePage != null) {
                            query = activePage.url
                        }
                    }
            }
        }

        LaunchedEffect(Unit) {
            tabsDao
                .observeTabsWithActivePage()
                .collect { items ->
                    logcat { "new items = $items" }
                    val newTabs = updateListPreserveOrder(
                        orderedTabs,
                        items.map { (t, p) ->
                            Pair(
                                StableTab(t),
                                if (p == null) {
                                    null
                                } else {
                                    StablePage(p)
                                }
                            )
                        }
                    )
                    orderedTabs.clear()
                    orderedTabs.addAll(newTabs)
                }
        }


        EventEffect(events) { event ->
            when (event) {
                BarEvent.CreateBlankTab -> {
                    val tab = tabsDao.insertTab(null)

                    navigator.navCmds.tryEmit {
                        navigate(GeminiTab(tab.tid))
                    }
                }

                is BarEvent.CreateNewPage -> {
                    tabsDao.insertPage(event.tabId, query)
                }

                BarEvent.CreateNewTab -> {
                    val tab = tabsDao.insertTab(query)

                    navigator.navCmds.tryEmit {
                        navigate(GeminiTab(tab.tid))
                    }
                }

                is BarEvent.DeleteTab -> {
                    tabsDao.deleteTab(event.id)
                }

                is BarEvent.GoToTab -> {
                    navigator.navCmds.tryEmit { navigate(GeminiTab(id = event.tab.id)) }
                }

                is BarEvent.ReorderTabs -> {
                    orderedTabs.add(
                        event.to,
                        orderedTabs.removeAt(event.from)
                    )
                }

                is BarEvent.SearchChanged -> query = event.query
            }
        }

        return BarState(
            tabs = orderedTabs,
            query = query,
            barMode = barMode,
            activeTab = visibleTab,
            showSearchbar = navBackStackEntry?.destination?.hasRoute(GeminiTab::class) == true ||
                    navBackStackEntry?.destination?.hasRoute(GeminiHome::class) == true
        )
    }
}