package ios.silv.gemclient.tab

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderBuilder
import androidx.navigation3.runtime.entry
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import ios.silv.gemclient.BottomBarSinglePaneScene
import ios.silv.gemclient.bar.BarMode
import ios.silv.gemclient.bar.LocalBarMode
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.ui.isImeVisibleAsState
import ios.silv.shared.GeminiTab
import ios.silv.shared.tab.PageEvent
import ios.silv.shared.tab.PageViewModel

fun EntryProviderBuilder<ios.silv.shared.NavKey>.geminiTabDestination() {
    entry<GeminiTab>(
        metadata = mapOf(BottomBarSinglePaneScene.BOTTOM_BAR_KEY to true)
    ) {
        val presenter = metroViewModel<PageViewModel>(it)

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