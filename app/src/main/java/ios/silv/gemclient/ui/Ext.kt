package ios.silv.gemclient.ui

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/***
remembers the most recent mutable state and proxies
all reads and writes current mutable state

ends up being mutableStateOf(mutableStateOf())
proxy the read and write to the inner mutable state
 ***/
@Composable
fun <T> rememberMutableState(
    vararg keys: Any?,
    calculation: @DisallowComposableCalls () -> MutableState<T>
): MutableState<T> {
    val state = remember(*keys) { calculation() }
    // ends up being a mutableStateOf MutableState
    val currentState by rememberUpdatedState(state)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = currentState.value
                set(value) {
                    currentState.value = value
                }

            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}


@Composable
fun LazyListState.sampleScrollingState(sample: Duration = 800.milliseconds): State<Boolean> {
    return produceState(false) {
        combine(
            snapshotFlow { firstVisibleItemIndex },
            snapshotFlow { isScrollInProgress },
            ::Pair
        )
            .onEach { (visibleIdx, scrolling) ->
                if (scrolling) value = true
                if (visibleIdx == 0) value = false
            }
            .sample(sample)
            .collect { (visibleIdx, scrolling) ->
                value = visibleIdx > 0 && scrolling
            }
    }
}

@Composable
fun isImeVisibleAsState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}

val SwipeToDismissBoxState.nonGoogleRetardProgress: Float
    @FloatRange(0.0, 1.0) get() = if (
        dismissDirection != SwipeToDismissBoxValue.Settled &&
        (progress != 1f || targetValue != SwipeToDismissBoxValue.Settled)
    ) {
        1f - progress
    } else {
        1f
    }


fun Modifier.conditional(condition: Boolean, other: Modifier): Modifier =
    if (condition) {
        this.then(other)
    } else {
        this
    }

fun Modifier.conditional(condition: Boolean, ifFalse: Modifier, ifTrue: Modifier): Modifier =
    if (condition) {
        this.then(ifTrue)
    } else {
        this.then(ifFalse)
    }

fun Modifier.conditional(condition: Boolean, other: Modifier.() -> Unit): Modifier =
    if (condition) {
        this.then(Modifier.apply(other))
    } else {
        this
    }

fun Modifier.conditional(condition: () -> Boolean, other: Modifier.() -> Unit): Modifier =
    if (condition()) {
        this.then(Modifier.apply(other))
    } else {
        this
    }

@Composable
fun LaunchedOnStartedEffect(
    vararg keys: Any?,
    block: suspend CoroutineScope.() -> Unit
) {
    val lifecycle = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(block)

    LaunchedEffect(lifecycle, *keys) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                callback()
            }
        }
    }
}
