package ios.silv.gemclient.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable


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