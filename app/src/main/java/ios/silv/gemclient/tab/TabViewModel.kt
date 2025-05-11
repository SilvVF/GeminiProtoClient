package ios.silv.gemclient.tab

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.ViewModelKey
import ios.silv.gemclient.dependency.ViewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(TabViewModel::class)
@Inject
class TabViewModel(
    savedStateHandle: SavedStateHandle,
    private val tabsRepo: TabsRepo,
    private val navigator: ComposeNavigator
) : ViewModel() {

    private val geminiTab = savedStateHandle.toRoute<GeminiTab>()
    private val tabWithActivePage = tabsRepo
        .observeTabWithActivePage(geminiTab.id)
        .map { it ?: (null to null) }

    fun onEvent(event: TabEvent) {
        when (event) {
            TabEvent.GoBack -> goBack()
            is TabEvent.LoadPage -> loadPage(event.link)
        }
    }

    val state = tabWithActivePage
        .distinctUntilChanged()
        .map { (tab, activePage) ->
            when {
                tab == null -> TabState.Error
                activePage == null -> TabState.NoPages
                else -> TabState.Loaded(activePage)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            TabState.Idle
        )

    private fun loadPage(link: String) {
        viewModelScope.launch {
            tabsRepo.insertPage(geminiTab.id, link)
        }
    }

    fun goBack() {
        viewModelScope.launch {
            val removed = tabsRepo.popActivePageByTabId(geminiTab.id)
            // if a page was not removed
            // the tab already had no pages so delete the tab
            if (!removed) {
                tabsRepo.deleteTab(geminiTab.id)

                navigator.navCmds.tryEmit {
                    popBackStack()
                }
            }
        }
    }
}