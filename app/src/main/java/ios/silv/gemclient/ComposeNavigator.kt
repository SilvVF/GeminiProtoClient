package ios.silv.gemclient

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription

val LocalNavigator = staticCompositionLocalOf<ComposeNavigator> { error("Not Provided") }

typealias NavigationCommand = NavController.() -> Unit

abstract class Navigator {
    val navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = Int.MAX_VALUE)
    val currentTab = MutableSharedFlow<Tab>(extraBufferCapacity = Int.MAX_VALUE)
    // We use a StateFlow here to allow ViewModels to start observing navigation results before the initial composition,
    // and still get the navigation result later
    val navControllerFlow = MutableStateFlow<NavController?>(null)
    val tabNavControllerFlow = MutableStateFlow<NavController?>(null)

    fun navigateUp() {
        navigationCommands.tryEmit {
            navigateUp()
        }
    }
}

class ComposeNavigator: Navigator() {

    suspend fun handleTabNavigationCommands(navController: NavController) {
        currentTab
            .onSubscription { this@ComposeNavigator.tabNavControllerFlow.value = navController }
            .onCompletion { this@ComposeNavigator.tabNavControllerFlow.value = null }
            .collect { tab ->

                val currentDestination = navController.currentBackStackEntry?.destination
                val alreadyHere = currentDestination?.hierarchy?.any { it.hasRoute(tab::class) } == true

                if (alreadyHere) {
                    return@collect
                }

                navController.navigate(tab) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    // Avoid multiple copies of the same destination when
                    // reselecting the same item
                    launchSingleTop = true
                    // Restore state when reselecting a previously selected item
                    restoreState = true
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
