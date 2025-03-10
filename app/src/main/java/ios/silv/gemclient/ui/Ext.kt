package ios.silv.gemclient.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun LazyListState.sampleScrollingState(sample: Duration = 800.milliseconds): State<Boolean> {
    return produceState(false) {
        combine(
            snapshotFlow { firstVisibleItemIndex },
            snapshotFlow { isScrollInProgress },
            ::Pair
        )
            .onEach { (visibleIdx, scrolling) ->
                if (scrolling) value = true
                if (visibleIdx == 0) value = false
            }
            .sample(sample)
            .collect { (visibleIdx, scrolling) ->
                value = visibleIdx > 0 && scrolling
            }
    }
}