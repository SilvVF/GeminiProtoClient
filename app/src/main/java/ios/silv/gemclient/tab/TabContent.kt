package ios.silv.gemclient.tab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.BarMode
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.LocalBarMode
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.ui.EventFlow

fun NavGraphBuilder.geminiTabDestination() {
    composable<GeminiTab> {
        val viewModel: TabViewModel = metroViewModel()

        val state by viewModel.state.collectAsStateWithLifecycle()
        val barMode = LocalBarMode.current

        BackHandler {
            if (barMode.currentState != BarMode.NONE) {
                barMode.targetState = BarMode.NONE
            } else {
                viewModel.goBack()
            }
        }

        TabContent(
            state = state,
            events = viewModel::onEvent
        )
    }
}

@Composable
private fun TabContent(
    state: TabState,
    events: (TabEvent) -> Unit
) {
    when (state) {
        is TabState.Idle -> {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        is TabState.Loaded -> PageContent(state, events)

        TabState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("error")
        }
        TabState.NoPages ->  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Pages for tab")
        }
    }
}