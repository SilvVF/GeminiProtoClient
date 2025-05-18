package ios.silv.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotMutableState
import ios.silv.core.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.log

var RIN_DEBUG = false

interface RetainedObserver {
    fun onRemembered()

    fun onForgotten()
}

val LocalShouldRemoveRetainedWhenRemovingComposition =
    compositionLocalOf<(LifecycleOwner) -> Boolean> {
        { lifecycleOwner ->
            val state = lifecycleOwner.lifecycle.currentState
            state == Lifecycle.State.RESUMED
        }
    }

@Composable
fun <T : Any> rememberRetained(
    vararg inputs: Any?,
    key: String? = null,
    block: @DisallowComposableCalls () -> T,
): T {
    // Caution: currentCompositeKeyHash is not unique so we need to store multiple values with the same key
    val keyToUse: String = key ?: currentCompositeKeyHash.toString(36)
    val viewModelFactory = remember {
        viewModelFactory {
            addInitializer(RinViewModel::class) { RinViewModel() }
        }
    }
    val rinViewModel: RinViewModel =
        viewModel(modelClass = RinViewModel::class, factory = viewModelFactory)
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleOwnerHash = lifecycleOwner.hashCode().toString(36)
    val removeRetainedWhenRemovingComposition =
        LocalShouldRemoveRetainedWhenRemovingComposition.current

    val result = remember(lifecycleOwner, keyToUse, *inputs) {
        logcat("RinViewModel") { "rememberRetained: remember $keyToUse ${inputs.contentHashCode()}" }
        val consumedValue = rinViewModel.consume(keyToUse, inputs)

        @Suppress("UNCHECKED_CAST")
        val result = consumedValue ?: block()
        rinViewModel.onRestoreOrCreate(keyToUse, inputs)

        object : RememberObserver {
            val result = result
            val input = inputs

            override fun onAbandoned() {
                onForgot()
            }

            override fun onForgotten() {
                onForgot()
            }

            fun onForgot() {
                logcat("RinViewModel") { "RinViewModel: rememberRetained: onForgot ${input.contentHashCode()} $keyToUse lifecycleOwner:$lifecycleOwner lifecycleOwner.lifecycle.currentState:${lifecycleOwner.lifecycle.currentState}" }
                rinViewModel.onForget(
                    keyToUse,
                    input,
                    removeRetainedWhenRemovingComposition(lifecycleOwner)
                )
            }

            override fun onRemembered() {
                rinViewModel.onRemembered(keyToUse, input, result, consumedValue != null)
            }
        }
    }.result as T

    SideEffect {
        rinViewModel.onNewSideEffect(
            removeRetainedWhenRemovingComposition(lifecycleOwner),
            lifecycleOwnerHash
        )
    }

    return result
}

internal class RinViewModel : ViewModel() {

    internal val savedData = mutableMapOf<Pair<String, Int>, ArrayDeque<RinViewModelEntity<Any?>>>()
    private val rememberedData = mutableMapOf<Pair<String, Int>, ArrayDeque<RinViewModelEntity<Any?>>>()

    init {
        logcat { "RinViewModel($this): created" }
    }

    fun consume(key: String, inputs: Array<out Any?>): Any? {
        val value = savedData[Pair(key, inputs.contentHashCode())]?.removeFirstOrNull()?.value
        logcat { "RinViewModel($this): consume key:$key value:$value savedData:$savedData" }
        return value
    }

    fun onRestoreOrCreate(key: String, inputs: Array<out Any?>) {
        val entity = savedData[Pair(key, inputs.contentHashCode())]
        entity?.forEach {
            it.onRestore()
        }
        logcat { "RinViewModel: onRestoreOrCreate $key" }
    }

    fun onRemembered(key: String, inputs: Array<out Any?>,  value: Any, isRestored: Boolean) {
        val element: RinViewModelEntity<Any?> = RinViewModelEntity(
            value = value,
            hasBeenRestored = isRestored,
        )

        rememberedData.getOrPut(Pair(key, inputs.contentHashCode())) { ArrayDeque() }.add(
            element
        )

        element.onRemember()

        logcat { "RinViewModel: onRemembered key:$key element:$element isRestored:$isRestored" }
    }

    override fun onCleared() {
        super.onCleared()
        val tmp = savedData.toList()
        rememberedData.clear()
        clearSavedData()
        logcat { "RinViewModel($this): onCleared removed:$tmp" }
    }

    fun onForget(key: String, inputs: Array<out Any?>, canRemove: Boolean) {
        val vmKey = Pair(key, inputs.contentHashCode())
        logcat {
            "RinViewModel($this): onForget key:$key canRemove:$canRemove isInRemember{${rememberedData.contains(vmKey)}} isInSaved:{${
                savedData.contains(
                    vmKey
                )
            }}"
        }
        if (!canRemove) {
            return
        }
        val entity = savedData[vmKey]
        entity?.forEach {
            it.close()
        }
        savedData.remove(vmKey)
    }

    private var lastLifecycleOwnerHash = ""

    fun onNewSideEffect(canRemove: Boolean, lifecycleOwnerHash: String) {
        if (rememberedData.isEmpty()) {
            return
        }
        val tmp = savedData.toList()
        if (canRemove && lastLifecycleOwnerHash != lifecycleOwnerHash) {
            // If recomposition we don't remove the saved data
            lastLifecycleOwnerHash = lifecycleOwnerHash
            clearSavedData()
        }
        savedData.putAll(rememberedData)
        rememberedData.clear()
        logcat { "RinViewModel: onSideEffect savedData:$savedData rememberedData:$rememberedData removed:$tmp" }
    }

    private fun clearSavedData() {
        savedData.values.forEach {
            it.forEach { entity ->
                entity.close()
            }
        }
        savedData.clear()
    }

    data class RinViewModelEntity<T>(
        var value: T,
        var hasBeenRestored: Boolean = false,
    ) {

        fun onRestore() {
            hasBeenRestored = true
        }

        fun onRemember() {
            if (hasBeenRestored) {
                return
            }
            val v = value ?: return
            when (v) {
                is RetainedObserver -> v.onRemembered()
            }
        }

        fun close() {
            onForgot()
        }

        private fun onForgot() {
            val v = value ?: return
            when (v) {
                is RetainedObserver -> v.onForgotten()
            }
        }
    }
}


@Suppress("UNCHECKED_CAST")
private fun <T> mutableStateSaver(inner: Saver<T, out Any>) =
    with(inner as Saver<T, Any>) {
        Saver<MutableState<T>, MutableState<Any?>>(
            save = { state ->
                require(state is SnapshotMutableState<T>) {
                    "If you use a custom MutableState implementation you have to write a custom " +
                            "Saver and pass it as a saver param to rememberRetainedSaveable()"
                }
                val saved = save(state.value)
                if (saved != null) {
                    mutableStateOf(saved, state.policy as SnapshotMutationPolicy<Any?>)
                } else {
                    // if the inner saver returned null we need to return null as well so the
                    // user's init lambda will be used instead of restoring mutableStateOf(null)
                    null
                }
            },
            restore =
                @Suppress("UNCHECKED_CAST", "ExceptionMessage") {
                    require(it is SnapshotMutableState<Any?>)
                    mutableStateOf(
                        if (it.value != null) restore(it.value!!) else null,
                        it.policy as SnapshotMutationPolicy<T?>,
                    )
                            as MutableState<T>
                },
        )
    }

/**
 * A [LaunchedEffect] that will run a suspendable [impression]. The [impression] will run once until
 * it is forgotten based on the [RetainedStateRegistry], and/or until the [inputs] change. This is
 * useful for async single fire side effects like logging or analytics.
 *
 * @param inputs A set of inputs that when changed will cause the [impression] to be re-run.
 * @param impression The impression side effect to run.
 */
@Composable
public fun LaunchedImpressionEffect(
    vararg inputs: Any?,
    impression: suspend CoroutineScope.() -> Unit
) {
   var launched by rememberRetained(*inputs) { mutableStateOf(false) }
   LaunchedEffect(*inputs, launched)  {
       if (!launched) {
           logcat { "running impression" }
           impression()
       }
       launched = true
   }
}

@Composable
fun rememberRetainedCoroutineScope(): CoroutineScope {
    return rememberRetained("coroutine_scope") {
        RememberObserverHolder(
            value = CoroutineScope(context = Dispatchers.Main + Job()),
            onDestroy = CoroutineScope::cancel,
        )
    }.value
}

internal class RememberObserverHolder<T>(
    val value: T,
    private val onDestroy: (T) -> Unit,
) : RetainedObserver {

    override fun onForgotten() {
        onDestroy(value)
    }

    override fun onRemembered() = Unit
}

@Composable
public fun <T : R, R> Flow<T>.collectAsRetainedState(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
): State<R> =
    produceRetainedState(initial, this, context) {
        if (context == EmptyCoroutineContext) {
            collect { value = it }
        } else withContext(context) { collect { value = it } }
    }

@Composable
public fun <T> StateFlow<T>.collectAsRetainedState(
    context: CoroutineContext = EmptyCoroutineContext
): State<T> = collectAsRetainedState(value, context)


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

/**
 * Return an observable [snapshot][androidx.compose.runtime.snapshots.Snapshot] [State] that
 * produces values over time without a defined data source.
 *
 * [producer] is launched when [produceRetainedState] enters the composition and is cancelled when
 * [produceRetainedState] leaves the composition. [producer] should use [ProduceStateScope.value] to
 * set new values on the returned [State].
 *
 * The returned [State] conflates values; no change will be observable if [ProduceStateScope.value]
 * is used to set a value that is [equal][Any.equals] to its old value, and observers may only see
 * the latest value if several values are set in rapid succession.
 *
 * [produceRetainedState] may be used to observe either suspending or non-suspending sources of
 * external data, for example:
 * ```
 * @Composable
 * fun FavoritesPresenter(favoritesRepository: FavoritesRepository): State {
 *   val state by produceRetainedState<UiState<List<Person>>>(UiState.Loading, favoritesRepository) {
 *     favoritesRepository.people
 *       .map { UiState.Data(it) }
 *       .collect { value = it }
 *   }
 *   return state
 * }
 * ```
 */
@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(Unit) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}

/**
 * Return an observable [snapshot][androidx.compose.runtime.snapshots.Snapshot] [State] that
 * produces values over time from [key1].
 *
 * [producer] is launched when [produceRetainedState] enters the composition and is cancelled when
 * [produceRetainedState] leaves the composition. If [key1] changes, a running [producer] will be
 * cancelled and re-launched for the new source. [producer] should use [ProduceStateScope.value] to
 * set new values on the returned [State].
 *
 * The returned [State] conflates values; no change will be observable if [ProduceStateScope.value]
 * is used to set a value that is [equal][Any.equals] to its old value, and observers may only see
 * the latest value if several values are set in rapid succession.
 *
 * [produceRetainedState] may be used to observe either suspending or non-suspending sources of
 * external data, for example:
 * ```
 * @Composable
 * fun FavoritesPresenter(favoritesRepository: FavoritesRepository): State {
 *   val state by produceRetainedState<UiState<List<Person>>>(UiState.Loading, favoritesRepository) {
 *     favoritesRepository.people
 *       .map { UiState.Data(it) }
 *       .collect { value = it }
 *   }
 *   return state
 * }
 * ```
 */
@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    key1: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(key1) { ProduceRetainedStateScopeImpl(result, coroutineContext).producer() }
    return result
}

/**
 * Return an observable [snapshot][androidx.compose.runtime.snapshots.Snapshot] [State] that
 * produces values over time from [key1] and [key2].
 *
 * [producer] is launched when [produceRetainedState] enters the composition and is cancelled when
 * [produceRetainedState] leaves the composition. If [key1] or [key2] change, a running [producer]
 * will be cancelled and re-launched for the new source. [producer] should use
 * [ProduceStateScope.value] to set new values on the returned [State].
 *
 * The returned [State] conflates values; no change will be observable if [ProduceStateScope.value]
 * is used to set a value that is [equal][Any.equals] to its old value, and observers may only see
 * the latest value if several values are set in rapid succession.
 *
 * [produceRetainedState] may be used to observe either suspending or non-suspending sources of
 * external data, for example:
 * ```
 * @Composable
 * fun FavoritesPresenter(favoritesRepository: FavoritesRepository): State {
 *   val state by produceRetainedState<UiState<List<Person>>>(UiState.Loading, favoritesRepository) {
 *     favoritesRepository.people
 *       .map { UiState.Data(it) }
 *       .collect { value = it }
 *   }
 *   return state
 * }
 * ```
 */
@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(key1, key2) {
        ProduceRetainedStateScopeImpl(
            result,
            coroutineContext
        ).producer()
    }
    return result
}

/**
 * Return an observable [snapshot][androidx.compose.runtime.snapshots.Snapshot] [State] that
 * produces values over time from [key1], [key2] and [key3].
 *
 * [producer] is launched when [produceRetainedState] enters the composition and is cancelled when
 * [produceRetainedState] leaves the composition. If [key1], [key2] or [key3] change, a running
 * [producer] will be cancelled and re-launched for the new source.
 * [producer should use [ProduceStateScope.value] to set new values on the returned [State].
 *
 * The returned [State] conflates values; no change will be observable if [ProduceStateScope.value]
 * is used to set a value that is [equal][Any.equals] to its old value, and observers may only see
 * the latest value if several values are set in rapid succession.
 *
 * [produceRetainedState] may be used to observe either suspending or non-suspending sources of
 * external data, for example:
 * ```
 * @Composable
 * fun FavoritesPresenter(favoritesRepository: FavoritesRepository): State {
 *   val state by produceRetainedState<UiState<List<Person>>>(UiState.Loading, favoritesRepository) {
 *     favoritesRepository.people
 *       .map { UiState.Data(it) }
 *       .collect { value = it }
 *   }
 *   return state
 * }
 * ```
 */
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
        ProduceRetainedStateScopeImpl(result, coroutineContext).producer()
    }
    return result
}

/**
 * Return an observable [snapshot][androidx.compose.runtime.snapshots.Snapshot] [State] that
 * produces values over time from [keys].
 *
 * [producer] is launched when [produceRetainedState] enters the composition and is cancelled when
 * [produceRetainedState] leaves the composition. If [keys] change, a running [producer] will be
 * cancelled and re-launched for the new source. [producer] should use [ProduceStateScope.value] to
 * set new values on the returned [State].
 *
 * The returned [State] conflates values; no change will be observable if [ProduceStateScope.value]
 * is used to set a value that is [equal][Any.equals] to its old value, and observers may only see
 * the latest value if several values are set in rapid succession.
 *
 * [produceRetainedState] may be used to observe either suspending or non-suspending sources of
 * external data, for example:
 * ```
 * @Composable
 * fun FavoritesPresenter(favoritesRepository: FavoritesRepository): State {
 *   val state by produceRetainedState<UiState<List<Person>>>(UiState.Loading, favoritesRepository) {
 *     favoritesRepository.people
 *       .map { UiState.Data(it) }
 *       .collect { value = it }
 *   }
 *   return state
 * }
 * ```
 */
@Composable
public fun <T> produceRetainedState(
    initialValue: T,
    vararg keys: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = rememberRetained { mutableStateOf(initialValue) }
    LaunchedEffect(keys = keys) {
        ProduceRetainedStateScopeImpl(
            result,
            coroutineContext
        ).producer()
    }
    return result
}