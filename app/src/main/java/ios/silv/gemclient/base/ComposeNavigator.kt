package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription

val LocalNavigator = staticCompositionLocalOf<ComposeNavigator> { error("Not Provided") }

typealias NavCmd = NavController.() -> Unit

abstract class Navigator {
    val navigationCommands = MutableSharedFlow<NavCmd>(extraBufferCapacity = Int.MAX_VALUE)
    // We use a StateFlow here to allow ViewModels to start observing navigation results before the initial composition,
    // and still get the navigation result later
    val navControllerFlow = MutableStateFlow<NavController?>(null)
}

class ComposeNavigator: Navigator() {

    suspend fun handleNavigationCommands(navController: NavController) {
        navigationCommands
            .onSubscription { this@ComposeNavigator.navControllerFlow.value = navController }
            .onCompletion { this@ComposeNavigator.navControllerFlow.value = null }
            .collect { cmd -> navController.cmd() }
    }
}
