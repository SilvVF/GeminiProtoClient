package ios.silv.gemclient.bar

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
import ios.silv.gemclient.base.PreviewCache
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.io.File

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(BarPresenter::class)
@Inject
class BarPresenter(
    private val tabsDao: TabsDao,
    private val navigator: ComposeNavigator,
    private val previewCache: PreviewCache,
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
            mutableStateListOf<Triple<StableTab, StablePage?, File?>>()
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()

        val visibleTab by produceState<GeminiTab?>(null) {
            snapshotFlow { navBackStackEntry }
                .filterNotNull()
                .map {
                    try {
                        it.toRoute<GeminiTab>()
                    } catch (e: Exception) {
                        null
                    }
                }
                .collect {
                    logcat { "active tab $it" }
                    value = it
                }
        }

        if (visibleTab != null) {
            LaunchedEffect(visibleTab) {
                tabsDao.observeTabWithActivePage(visibleTab?.id ?: return@LaunchedEffect)
                    .filterNotNull()
                    .collect { (_, activePage) ->
                        logcat { "received new active page $activePage" }
                        if (activePage != null) {
                            query = activePage.url
                        }
                    }
            }
        }

        LaunchedEffect(Unit) {
            combine(
                previewCache.invalidated,
                tabsDao.observeTabsWithActivePage(),
                ::Pair
            ).collect { (_, tabs)  ->
                logcat { "new items = $tabs" }
                val newTabs = updateListPreserveOrder(
                    orderedTabs.map { Pair(it.first, it.second) },
                    tabs.map { (t, p) ->
                        Pair(
                            StableTab(t),
                            if (p == null) {
                                // 1
                                // 2
                                null
                            } else {
                                StablePage(p)
                            }
                        )
                    }
                )
                orderedTabs.clear()
                orderedTabs.addAll(
                    newTabs.map { (tab, page) ->
                        Triple(tab, page, previewCache.readFromCache(tab.id))
                    }
                )
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
                BarEvent.GoToHome -> {
                    navigator.topLevelDest.tryEmit(GeminiHome)
                }
            }
        }

        return BarState(
            tabs = orderedTabs,
            query = query,
            activeTab = visibleTab,
            showSearchbar = navBackStackEntry?.destination?.hasRoute(GeminiTab::class) == true ||
                    navBackStackEntry?.destination?.hasRoute(GeminiHome::class) == true
        )
    }
}