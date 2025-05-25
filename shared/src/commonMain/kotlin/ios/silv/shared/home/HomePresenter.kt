package ios.silv.shared.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.database.dao.TabsDao
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiTab
import ios.silv.shared.MoleculeViewModel
import ios.silv.shared.di.ViewModelKey
import ios.silv.shared.di.ViewModelScope
import ios.silv.shared.settings.SettingsStore
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow


@ContributesIntoMap(ViewModelScope::class, binding<ViewModel>())
@ViewModelKey(HomePresenter::class)
@Inject
class HomePresenter(
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
): MoleculeViewModel<HomeEvent, HomeUiState>() {

    @Composable
    override fun models(eventFlow: EventFlow<HomeEvent>): HomeUiState {
        var incognito by remember { mutableStateOf(false) }

        val recentlyViewed by settingsStore.recentlyViewed.collectAsState()
        val bookmarked by settingsStore.bookmarked.collectAsState()

        EventEffect(eventFlow) { event ->
            when (event) {
                is HomeEvent.CreateTab -> {
                    val tab = tabsDao.insertTab(event.url)

                    navigator.navCmds.emit {
                        add(GeminiTab(tab.tid))
                    }
                }
                HomeEvent.ToggleIncognito -> incognito = !incognito
            }
        }

        return HomeUiState(
            incognito = incognito,
            recentlyViewed = recentlyViewed.toList(),
            bookmarked = bookmarked.toList()
        )
    }
}