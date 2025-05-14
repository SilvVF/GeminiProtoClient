package ios.silv.gemclient.bar

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.collectAsRetainedState
import io.github.takahirom.rin.rememberRetained
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsDao
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.PreviewCache
import ios.silv.gemclient.base.toRouteOrNull
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.lib.rin.LaunchedRetainedEffect
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.types.StableTab
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

        LaunchedRetainedEffect(tabs) {
            val newTabs = updateListPreserveOrder(
                orderedTabs,
                tabs.map { (tab, page) ->
                    Triple(
                        StableTab(tab),
                        StablePage(page),
                        previewCache.readFromCache(tab.tid)?.toUri().toString()
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