package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ios.silv.gemclient.App
import ios.silv.gemclient.bar.BarMode
import ios.silv.gemclient.bar.LocalBarMode
import ios.silv.gemclient.dependency.metroPresenter
import ios.silv.gemclient.ui.isImeVisibleAsState
import ios.silv.shared.GeminiTab
import ios.silv.shared.tab.PageEvent
import ios.silv.shared.tab.PagePresenter
import ios.silv.shared.ui.rememberEventFlow
import kotlinx.coroutines.delay

fun NavGraphBuilder.geminiTabDestination() {
    composable<GeminiTab> {


        val events = rememberEventFlow<PageEvent>()
        val presenter = metroPresenter<PagePresenter>()

        val state = presenter.present(it.toRoute(), events)

        val barMode = LocalBarMode.current
        val ime by isImeVisibleAsState()

        BackHandler(
            enabled = !ime
        ) {
            if (barMode.currentState != BarMode.NONE) {
                barMode.targetState = BarMode.NONE
            } else {
                events.tryEmit(PageEvent.GoBack)
            }
        }

        PageContent(
            pageState = state,
            events = events
        )
    }
}