package ios.silv.gemclient.lib.rin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.takahirom.rin.RetainedObserver
import io.github.takahirom.rin.rememberRetained
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.math.acos

private class ProduceRetainedStateScopeImpl<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext,
) : ProduceStateScope<T>, MutableState<T> by state {

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> {}
        } finally {
            onDispose()
        }
    }
}

@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    key1: Any,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(key1) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}

@Composable
fun <T : Any> rememberRetained(
    key: Any?,
    block: @DisallowComposableCalls () -> T,
): T {
    val hash = currentCompositeKeyHash.toString(36)
    return rememberRetained("${hash}_${key.hashCode()}", block)
}

@Composable
fun <T : Any> rememberRetained(
    key1: Any?,
    key2: Any?,
    block: @DisallowComposableCalls () -> T,
): T = rememberRetained(
    "${currentCompositeKeyHash.toString(36)}_${key1.hashCode()}_${key2.hashCode()}",
    block
)

@Composable
fun <T : Any> rememberRetained(
    key1: Any?,
    key2: Any?,
    key3: Any?,
    block: @DisallowComposableCalls () -> T,
): T = rememberRetained(
    "${currentCompositeKeyHash.toString(36)}_${key1.hashCode()}_${key2.hashCode()}_${key3.hashCode()}",
    block
)

@Composable
fun <T : Any> rememberRetained(
    vararg keys: Any?,
    block: @DisallowComposableCalls () -> T,
): T = rememberRetained("${currentCompositeKeyHash.toString(36)}_${keys.joinToString("_")}", block)


@Composable
fun LaunchedRetainedEffect(vararg inputs: Any?, action: suspend () -> Unit) {
    val callback by rememberUpdatedState(action)
    rememberRetained(*inputs) {
        object : RetainedObserver, CoroutineScope {
            override val coroutineContext: CoroutineContext = Job() + Dispatchers.Main.immediate

            override fun onForgotten() {
                cancel()
            }

            override fun onRemembered() {
                launch {
                    callback()
                }
            }
        }
    }
}

@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    key3: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(key1, key2, key3) {
        ProduceRetainedStateScopeImpl(
            result,
            coroutineContext
        ).producer()
    }
    return result
}


@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(keys) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}
