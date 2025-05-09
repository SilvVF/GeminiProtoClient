package ios.silv.gemclient.tab

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
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState
import ios.silv.gemini.GeminiClient
import ios.silv.sqldelight.Page
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TabEvent : UiEvent {
    data class LoadPage(val link: String) : TabEvent
    data object GoBack : TabEvent
}


data class TabState(
    val events: (TabEvent) -> Unit,
    val state: TabData
): UiState

sealed interface TabData {
    data object Idle : TabData
    data object Error : TabData
    data object NoPages : TabData
    data class Loaded(val presenter: PagePresenter) : TabData
}

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(TabViewModel::class)
@Inject
class TabViewModel(
    savedStateHandle: SavedStateHandle,
    private val pagePresenterFactory: PagePresenter.Factory,
    private val tabsRepo: TabsRepo
) : ViewModel() {

    private val geminiTab = savedStateHandle.toRoute<GeminiTab>()
    private val tabWithActivePage = tabsRepo
        .observeTabWithActivePage(geminiTab.id)
        .map { it ?: (null to null) }

    private val eventSink = { event: TabEvent ->
        when (event) {
            TabEvent.GoBack -> goBack()
            is TabEvent.LoadPage -> loadPage(event.link)
        }
    }

    val state = tabWithActivePage
        .distinctUntilChanged()
        .map { (tab, activePage) ->
            val tabData = when {
                tab == null -> TabData.Error
                activePage == null -> TabData.NoPages
                else -> TabData.Loaded(
                    pagePresenterFactory.create(activePage, viewModelScope)
                )
            }

            TabState(
                eventSink,
                tabData
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            TabState(eventSink, TabData.Idle)
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
            }
        }
    }
}