package ios.silv.shared.bar

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
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.collectAsRetainedState
import io.github.takahirom.rin.rememberRetained
import ios.silv.core.logcat.logcat
import ios.silv.database.dao.TabsDao
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiHome
import ios.silv.shared.GeminiTab
import ios.silv.shared.PreviewCache
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.toRouteOrNull
import ios.silv.shared.types.StablePage
import ios.silv.shared.types.StableTab
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(BarPresenter::class)
@Inject
class BarPresenter(
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val previewCache: PreviewCache,
    private val navController: NavController,
) : Presenter {

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    fun present(events: EventFlow<BarEvent>): BarState {

        var query by rememberRetained { mutableStateOf("") }

        val orderedTabs = rememberRetained {
            mutableStateListOf<Triple<StableTab, StablePage?, String?>>()
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val tabs by tabsWithActivePageFlow.collectAsRetainedState(emptyList())

        val visibleTab by produceState<GeminiTab?>(null) {
            snapshotFlow { navBackStackEntry }.filterNotNull()
                .map { it.toRouteOrNull<GeminiTab>() }
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
                        previewCache.read(tab.tid)?.toString()
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