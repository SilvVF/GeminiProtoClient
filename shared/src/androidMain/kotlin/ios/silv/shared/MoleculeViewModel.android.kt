package ios.silv.shared

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.CoroutineContext

actual val UiDispatcherContext: CoroutineContext = AndroidUiDispatcher.Main