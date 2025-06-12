package ios.silv.gemclient.base

import androidx.compose.runtime.staticCompositionLocalOf
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.NavKey
import ios.silv.shared.types.SnapshotStateStack

/***
 *  Default provided for convince in previews
 */
val LocalNavigator = staticCompositionLocalOf<AppComposeNavigator> { error("") }
val LocalNavBackStack = staticCompositionLocalOf<SnapshotStateStack<NavKey>> { error("") }
