package ios.silv.gemclient.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope


/**
 * Marker interface for all UiEvent types.
 *
 * Events in Circuit should generally reflect user interactions with the UI. They are mediated by a
 * `Presenter` and may or may not influence the current [state][UiState].
 *
 * **Circuit event types are annotated as [@Immutable][Immutable] and should only use immutable
 * properties.**
 *
 * ## Testing
 *
 * To test events flowing from a UI, consider using `TestEventSink` from Circuit's test artifact.
 */
@Immutable
interface UiEvent

/**
 * Marker interface for all UiState types.
 *
 * States in Circuit should be minimal data models that a `Ui` can render. They are produced by a
 * `Presenter` that interpret the underlying data layer and mediate input user/nav
 * [events][UiEvent].
 *
 * `Ui`s receive state as a parameter and should act as pure functions that render the input state
 * as a UI. They should not have any side effects or directly interact with the underlying data
 * layer.
 *
 * **Circuit state types are annotated as [@Stable][Stable] and should only use stable properties.**
 */
@Stable
interface UiState

@Stable
class EventFlow<T: UiEvent>(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
): MutableSharedFlow<T> by MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)

@Composable
fun <T: UiEvent> rememberEventFlow(): EventFlow<T> {
    return remember {
        EventFlow(extraBufferCapacity = 20)
    }
}

@Composable
fun <EVENT: UiEvent> EventEffect(
    eventFlow: EventFlow<EVENT>,
    block: suspend CoroutineScope.(EVENT) -> Unit,
) {
    LaunchedEffect(eventFlow) {
        try {
            supervisorScope {
                eventFlow.collect { event ->
                    launch {
                        block(event)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }
}