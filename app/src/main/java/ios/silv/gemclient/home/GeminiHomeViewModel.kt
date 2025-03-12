package ios.silv.gemclient.home

import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.GeminiTab
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.ViewModelActionHandler
import ios.silv.gemclient.dependency.DependencyAccessor
import ios.silv.gemclient.dependency.commonDeps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


interface GeminiHomeViewModelAction {
    fun toggleIncognito()
    suspend fun createTab(url: String?)
}

data class HomeViewModelState(
    val incognito: Boolean = false
)

class GeminiHomeViewModel @OptIn(DependencyAccessor::class) constructor(
    private val tabsRepo: TabsRepo = commonDeps.tabsRepo,
    private val navigator: ComposeNavigator = commonDeps.navigator
): GeminiHomeViewModelAction, ViewModelActionHandler<GeminiHomeViewModelAction>() {

    override val handler: GeminiHomeViewModelAction = this

    private val _state = MutableStateFlow(HomeViewModelState())
    val state = _state.asStateFlow()

    override fun toggleIncognito() {
        _state.update { state -> state.copy(incognito = !state.incognito) }
    }

    override suspend fun createTab(url: String?) {
        val tab = tabsRepo.insertTab(url)

        navigator.navCmds.emit {
            navigate(GeminiTab(tab.tid))
        }
    }
}