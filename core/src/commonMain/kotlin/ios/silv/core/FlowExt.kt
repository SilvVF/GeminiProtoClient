package ios.silv.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingCommand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

@JvmName("mutate_List")
fun <T> MutableStateFlow<List<T>>.mutate(update: MutableList<T>.() -> Unit) {
    return update { value ->
        value.toMutableList().apply(update)
    }
}

@JvmName("mutate_Map")
fun <K, V> MutableStateFlow<Map<K, V>>.mutate(update: MutableMap<K, V>.() -> Unit) {
    return update { value ->
        value.toMutableMap().apply(update)
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MutableStateFlowList<T: Any>(
    value: List<T>
) : MutableStateFlow<List<T>> by MutableStateFlow(value) {

    operator fun get(index: Int): T = this.value[index]

    operator fun set(index: Int, value: T) {
        mutate {
            this[index] = value
        }
    }
}

/**
 * Attempts [block], returning a successful [Result] if it succeeds, otherwise a [Result.Failure]
 * taking care not to break structured concurrency
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (exception: Exception) {
        Result.failure(exception)
    }


/**
 * The restartable interface defines one action: [restart].
 */
public interface Restartable {

    /**
     * The representation of the [Restartable] object should be able to restart an action.
     */
    public fun restart()
}

/**
 * [RestartableStateFlow] extends both [StateFlow] and [Restartable], and is designed to restart
 * the emission of the upstream flow. It functions just like a regular [StateFlow], but with the
 * added ability to restart the upstream emission when needed.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
public interface RestartableStateFlow<out T> : StateFlow<T>, Restartable {

    /**
     * The representation of the [Restartable] object that should be able to restart an action.
     */
    override fun restart()
}

/**
 * `restartableStateIn` returns a [RestartableStateFlow] that implements both [StateFlow] and
 * [Restartable], and is designed to restart the emission of the upstream flow. It functions just
 * like a regular [StateFlow], but with the added ability to restart the upstream emission when needed.
 *
 * @param scope the coroutine scope in which sharing is started.
 * @param started the strategy that controls when sharing is started and stopped.
 * @param initialValue the initial value of the state flow. This value is also used when the state flow
 * is reset using the SharingStarted. WhileSubscribed strategy with the replayExpirationMillis par
 */
public fun <T> Flow<T>.restartableStateIn(
    scope: CoroutineScope,
    started: SharingStarted,
    initialValue: T,
): RestartableStateFlow<T> {
    val sharingRestartable = SharingRestartableImpl(started)
    val stateFlow = stateIn(scope, sharingRestartable, initialValue)
    return object : RestartableStateFlow<T>, StateFlow<T> by stateFlow {
        override fun restart() = sharingRestartable.restart()
    }
}

/**
 * The internal implementation of the [SharingStarted], and [Restartable].
 */
private data class SharingRestartableImpl(
    private val sharingStarted: SharingStarted,
) : SharingStarted, Restartable {

    private val restartFlow = MutableSharedFlow<SharingCommand>(extraBufferCapacity = 2)

    override fun command(subscriptionCount: StateFlow<Int>): Flow<SharingCommand> {
        return merge(restartFlow, sharingStarted.command(subscriptionCount))
    }

    override fun restart() {
        restartFlow.tryEmit(SharingCommand.STOP_AND_RESET_REPLAY_CACHE)
        restartFlow.tryEmit(SharingCommand.START)
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MutableStateFlowMap<K: Any, V: Any>(
    value: Map<K, V>
) : MutableStateFlow<Map<K, V>> by MutableStateFlow(value) {

    operator fun get(key: K): V? = this.value[key]

    operator fun set(key: K, value: V) {
        mutate {
            this[key] = value
        }
    }
}