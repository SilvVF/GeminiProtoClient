package ios.silv.shared

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.RecompositionMode
import app.cash.molecule.SnapshotNotifier
import app.cash.molecule.launchMolecule
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.UiEvent
import ios.silv.shared.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

internal expect val UiDispatcherContext: CoroutineContext

abstract class MoleculeViewModel<Event : UiEvent, Model : UiState> : ViewModel() {

    private val scope = CoroutineScope(viewModelScope.coroutineContext + UiDispatcherContext)

    // Events have a capacity large enough to handle simultaneous UI events, but
    // small enough to surface issues if they get backed up for some reason.
    val events = EventFlow<Event>(extraBufferCapacity = 20)

    val models: StateFlow<Model> by lazy(LazyThreadSafetyMode.NONE) {
        scope.launchMolecule(
            mode = RecompositionMode.ContextClock,
            snapshotNotifier = SnapshotNotifier.External
        ) {
            models(events)
        }
    }

    @Composable
    protected abstract fun models(events: EventFlow<Event>): Model
}