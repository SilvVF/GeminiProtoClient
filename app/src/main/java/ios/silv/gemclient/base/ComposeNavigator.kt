package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.TopLevelDest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.suspendCoroutine

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf<ComposeNavigator> { error("") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("") }

typealias NavCmd = NavController.() -> Unit

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
                popUpTo(
                    route = dest::class
                ) {
                    saveState = true
                }
                launchSingleTop = true
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
