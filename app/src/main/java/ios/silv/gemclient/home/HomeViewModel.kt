package ios.silv.gemclient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.ViewModelKey
import ios.silv.gemclient.dependency.ViewModelScope
import ios.silv.gemclient.ui.UiEvent
import ios.silv.gemclient.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeViewModelEvent: UiEvent {
    data object ToggleIncognito: HomeViewModelEvent
    data class CreateTab(val url: String?): HomeViewModelEvent
}

data class HomeViewModelState(
    val incognito: Boolean = false,
    val events: (HomeViewModelEvent) -> Unit = {}
): UiState

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
    private val tabsRepo: TabsRepo,
    private val navigator: ComposeNavigator
): ViewModel() {

    private val incognito = MutableStateFlow(false)

    private val eventSink = { event: HomeViewModelEvent ->
        when(event) {
            is HomeViewModelEvent.CreateTab -> createTab(url = event.url)
            HomeViewModelEvent.ToggleIncognito -> toggleIncognito()
        }
    }

    val state = incognito.asStateFlow().map { incog ->
        HomeViewModelState(
            incognito = incog,
            events = eventSink
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            HomeViewModelState(events = eventSink)
        )

    fun toggleIncognito() {
        incognito.update { i -> !i }
    }

    fun createTab(url: String?) {
        viewModelScope.launch {
            val tab = tabsRepo.insertTab(url)

            navigator.navCmds.emit {
                navigate(GeminiTab(tab.tid))
            }
        }
    }
}