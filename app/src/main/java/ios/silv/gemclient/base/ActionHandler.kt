package ios.silv.gemclient.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Stable
@Immutable
interface ActionDispatcher<T> {
    fun dispatch(action: T.() -> Unit)
}

abstract class ViewModelActionHandler<T> : ViewModel(), ActionDispatcher<T> {

    protected abstract val handler: T

    private val actions = Channel<T.() -> Unit>(Channel.UNLIMITED)

    override fun dispatch(action: T.() -> Unit) {
        actions.trySend(action)
    }

    init {
        viewModelScope.launch {
            for (action in actions) {
                action(handler)
            }
        }
    }
}