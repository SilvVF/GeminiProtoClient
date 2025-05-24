package ios.silv.gemclient.base

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation3.runtime.NavBackStack
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.NavKey

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf<AppComposeNavigator> { error("") }
val LocalNavBackStack = staticCompositionLocalOf<SnapshotStateList<NavKey>> { error("") }
