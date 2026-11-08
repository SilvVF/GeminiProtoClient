package ios.silv.vendor.cascadecompose.internal

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalComposeUiApi::class)
internal fun PopupProperties.copy(
  usePlatformDefaultWidth: Boolean
): PopupProperties {
  return PopupProperties(
    focusable = focusable,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    clippingEnabled = clippingEnabled,
    usePlatformDefaultWidth = usePlatformDefaultWidth
  )
}
