package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import ios.silv.shared.AppComposeNavigator

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf<AppComposeNavigator> { error("") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("") }
