package ios.silv.shared.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.database.dao.TabsDao
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiTab
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.settings.SettingsStore
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.collectAsRetainedState
import ios.silv.shared.ui.rememberRetained


@ContributesIntoMap(PresenterScope::class)
@PresenterKey(HomePresenter::class)
@Inject
class HomePresenter(
    private val tabsDao: TabsDao,
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
): Presenter {

    @Composable
    fun present(eventFlow: EventFlow<HomeEvent>): HomeUiState {
        var incognito by rememberRetained { mutableStateOf(false) }

        val recentlyViewed by settingsStore.recentlyViewed.collectAsRetainedState()
        val bookmarked by settingsStore.bookmarked.collectAsRetainedState()

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