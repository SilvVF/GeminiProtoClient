package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.bar.BarMode
import ios.silv.gemclient.bar.LocalBarMode
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.ui.isImeVisibleAsState
import ios.silv.shared.GeminiTab
import ios.silv.shared.tab.PageEvent
import ios.silv.shared.tab.PageViewModel

fun NavGraphBuilder.geminiTabDestination() {
    composable<GeminiTab> {


        val presenter = metroViewModel<PageViewModel>()

        val state by presenter.models.collectAsStateWithLifecycle()

        val barMode = LocalBarMode.current
        val ime by isImeVisibleAsState()

        BackHandler(
            enabled = !ime
        ) {
            if (barMode.currentState != BarMode.NONE) {
                barMode.targetState = BarMode.NONE
            } else {
                presenter.events.tryEmit(PageEvent.GoBack)
            }
        }

        PageContent(
            pageState = state,
            events = presenter.events
        )
    }
}