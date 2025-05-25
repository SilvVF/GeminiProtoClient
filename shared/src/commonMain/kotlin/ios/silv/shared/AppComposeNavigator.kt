package ios.silv.shared

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ios.silv.core.logcat.logcat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.coroutineContext

typealias NavCmd = SnapshotStateList<NavKey>.() -> Unit

inline fun <reified T> NavBackStackEntry.toRouteOrNull(): T? = try {
    toRoute(T::class)
} catch (e: SerializationException) {
    null
}

fun toTopLevel(topLevelDest: TopLevelDest): NavCmd = {
    // makes sure only one instance of this exists on the back stack
    val i = indexOf(topLevelDest)
    if (i == -1) {
        add(topLevelDest)
    } else {
        add(removeAt(i))
    }
}

@SingleIn(AppScope::class)
@Inject
class AppComposeNavigator {

    val navCmds = MutableSharedFlow<NavCmd>(extraBufferCapacity = Int.MAX_VALUE)

    val navBackStackFlow = MutableStateFlow<SnapshotStateList<NavKey>?>(null)

    suspend fun handleNavigationCommands(backStack: SnapshotStateList<NavKey>) {
        navCmds
            .onSubscription { navBackStackFlow.value = backStack }
            .onCompletion { navBackStackFlow.value = null }
            .collect { cmd ->
                logcat { "sending nav command $cmd" }
                Snapshot.withMutableSnapshot {
                    backStack.apply(cmd)
                }
                logcat { "${backStack.toList()}" }
            }
    }
}
