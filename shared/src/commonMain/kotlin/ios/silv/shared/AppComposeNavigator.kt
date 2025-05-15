package ios.silv.shared

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ios.silv.core.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

typealias NavCmd = NavController.() -> Unit

inline fun <reified T> NavBackStackEntry.toRouteOrNull(): T? = try {
    toRoute(T::class)
} catch (e: SerializationException) {
    null
}

@SingleIn(AppScope::class)
@Inject
class AppComposeNavigator {

    val navCmds = MutableSharedFlow<NavCmd>(extraBufferCapacity = Int.MAX_VALUE)

    /***
     * Accepts only top level destinations. Use to navigate to destinations that
     * should not be wrapped with search bar and tab preview
     */
    val topLevelDest = MutableSharedFlow<TopLevelDest>(extraBufferCapacity = Int.MAX_VALUE)

    // We use a StateFlow here to allow ViewModels to start observing navigation results before the initial composition,
    // and still get the navigation result later
    val navControllerFlow = MutableStateFlow<NavController?>(null)


    suspend fun handleNavigationCommands(navController: NavController) {
        coroutineScope {

            navControllerFlow.value = navController
            logcat { "set controller" }

            launch {
                navCmds.collect { cmd ->
                    logcat { "sending nav command $cmd" }
                    navController.cmd()
                }
            }
            launch {
                topLevelDest.collect { dest ->
                    logcat { "sending nav command $dest" }
                    navController.navigate(dest) {

                        launchSingleTop = true

                        popUpTo(route = dest::class) {
                            inclusive = true
                        }
                    }
                }
            }

            try {
                awaitCancellation()
            } finally {
                logcat { "clearing controller" }
                navControllerFlow.value = null
            }
        }
    }
}
