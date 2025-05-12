package ios.silv.gemclient.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.rememberRetained
import ios.silv.database_android.dao.TabsDao
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow


@ContributesIntoMap(PresenterScope::class)
@PresenterKey(HomePresenter::class)
@Inject
class HomePresenter(
    private val tabsDao: TabsDao,
    private val navigator: ComposeNavigator
): Presenter {

    @Composable
    fun present(eventFlow: EventFlow<HomeEvent>): HomeUiState {
        var incognito by rememberRetained { mutableStateOf(false) }

        EventEffect(eventFlow) { event ->
            when (event) {
                is HomeEvent.CreateTab -> {
                    val tab = tabsDao.insertTab(event.url)

                    navigator.navCmds.emit {
                        navigate(GeminiTab(tab.tid))
                    }
                }
                HomeEvent.ToggleIncognito -> incognito = !incognito
            }
        }

        return HomeUiState(
            incognito = incognito
        )
    }
}