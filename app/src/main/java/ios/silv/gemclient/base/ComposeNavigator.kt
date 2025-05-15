package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ios.silv.core.logcat.logcat
import ios.silv.gemclient.TopLevelDest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerializationException

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf<ComposeNavigator> { error("") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("") }

typealias NavCmd = NavController.() -> Unit

inline fun <reified T> NavBackStackEntry.toRouteOrNull(): T? = try {
    toRoute(T::class)
} catch (e: SerializationException) {
    null
}

@SingleIn(AppScope::class)
@Inject
class ComposeNavigator {

    val navCmds = MutableSharedFlow<NavCmd>(extraBufferCapacity = Int.MAX_VALUE)

    /***
     * Accepts only top level destinations. Use to navigate to destinations that
     * should not be wrapped with search bar and tab preview
     */
    val topLevelDest = MutableSharedFlow<TopLevelDest>(extraBufferCapacity = Int.MAX_VALUE)

    // We use a StateFlow here to allow ViewModels to start observing navigation results before the initial composition,
    // and still get the navigation result later
    val navControllerFlow = MutableStateFlow<NavController?>(null)


    suspend fun CoroutineScope.handleNavigationCommands(navController: NavController) {
        navControllerFlow.value = navController
        logcat { "set controller" }
        navCmds.onEach { cmd ->
            logcat { "sending nav command $cmd" }
            navController.cmd()
        }
            .launchIn(this)

        topLevelDest.onEach { dest ->
            logcat { "sending nav command $dest" }
            navController.navigate(dest) {

                launchSingleTop = true

                popUpTo(route = dest::class) {
                    inclusive = true
                }
            }
        }
            .launchIn(this)

        try {
            awaitCancellation()
        } finally {
            logcat { "clearing controller" }
            navControllerFlow.value = null
        }
    }
}
