package ios.silv.home

import ios.silv.gemclient.base.ViewModelActionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


interface GeminiHomeViewModelAction {
    fun toggleIncognito()
}

data class HomeViewModelState(
    val incognito: Boolean = false
)

class GeminiHomeViewModel: GeminiHomeViewModelAction, ViewModelActionHandler<GeminiHomeViewModelAction>() {

    override val handler: GeminiHomeViewModelAction = this

    private val _state = MutableStateFlow(HomeViewModelState())
    val state = _state.asStateFlow()

    override fun toggleIncognito() {
        _state.update { state -> state.copy(incognito = !state.incognito) }
    }
}