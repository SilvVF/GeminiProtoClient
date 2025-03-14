package ios.silv.gemclient.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ios.silv.core_android.log.asLog
import ios.silv.core_android.log.logcat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Stable
@Immutable
interface ActionDispatcher<T> {
    fun immediate(action: T.() -> Unit)
    fun dispatch(action: suspend T.() -> Unit)
}

abstract class ViewModelActionHandler<T> : ViewModel(), ActionDispatcher<T> {

    protected abstract val handler: T

    private val uncaught = Channel<Exception>(Channel.BUFFERED)
    val errors = uncaught.receiveAsFlow()

    private val actions = Channel<suspend T.() -> Unit>(Channel.BUFFERED)

    override fun dispatch(action: suspend T.() -> Unit) {
        actions.trySend(action)
    }

    override fun immediate(action: T.() -> Unit) {
        try {
            action(handler)
        } catch (e: Exception){
            logcat { "$action had an error ${e.message} ${e.asLog()}" }
            uncaught.trySend(e)
        }
    }

    init {
        viewModelScope.launch {
            try {
                for (action in actions) {
                    launch {
                        try {
                            action(handler)
                        } catch (e: Exception){
                            logcat { "$action had an error ${e.message} ${e.asLog()}" }
                            uncaught.send(e)
                        }
                    }
                }
            } finally {
                actions.close()
            }
        }
    }
}