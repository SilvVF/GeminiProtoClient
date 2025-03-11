package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import ios.silv.gemclient.TopLevelDest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf { ComposeNavigator() }

typealias NavCmd = NavController.() -> Unit

class ComposeNavigator {

    val navigationCommands = MutableSharedFlow<NavCmd>(extraBufferCapacity = Int.MAX_VALUE)

    /***
     * Accepts only top level destinations. Use to navigate to destinations that
     * should not be wrapped with search bar and tab preview
     */
    val topLevelDest = MutableSharedFlow<TopLevelDest>(extraBufferCapacity = Int.MAX_VALUE)

    val topLevelNavController = MutableStateFlow<NavController?>(null)
    // We use a StateFlow here to allow ViewModels to start observing navigation results before the initial composition,
    // and still get the navigation result later
    val navControllerFlow = MutableStateFlow<NavController?>(null)

    /*
     Top level destinations are handled like this because it allows all destinations
     within a sub NavHost to be wrapped in a composable parent. this is similar
     to the TabNavigation API provided by Voyager.
     */
    suspend fun handleTopLevel(navController: NavController) {
        topLevelDest
            .onSubscription { this@ComposeNavigator.topLevelNavController.value = navController }
            .onCompletion { this@ComposeNavigator.topLevelNavController.value = null }
            .collect {

                val route = navController.currentDestination?.route
                val isCurrent = route == "${it::class}"

                if (!isCurrent) {
                    navController.navigate(it)
                }
            }
    }

    suspend fun handleNavigationCommands(navController: NavController) {
        navigationCommands
            .onSubscription { this@ComposeNavigator.navControllerFlow.value = navController }
            .onCompletion { this@ComposeNavigator.navControllerFlow.value = null }
            .collect { cmd -> navController.cmd() }
    }
}
