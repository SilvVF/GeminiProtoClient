package ios.silv.gemclient.tab

import androidx.lifecycle.viewModelScope
import ios.silv.core_android.StateFlowStack
import ios.silv.core_android.restartableStateIn
import ios.silv.gemclient.dependency.DependencyAccessor
import ios.silv.gemclient.dependency.ViewModelActionHandler
import ios.silv.gemclient.dependency.commonDeps
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.GeminiTab
import ios.silv.gemini.ContentNode
import ios.silv.gemini.GeminiParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformLatest

interface GeminiTabViewModelAction {
    fun loadPage(link: String)
    fun goBack()
    fun refresh()
}

sealed class TabState(val route: String) {
    data class Loading(val url: String) : TabState(url)
    data class Done(val url: String, val nodes: List<ContentNode>) : TabState(url)
}

class GeminiTabViewModel @OptIn(DependencyAccessor::class) constructor(
    geminiTab: GeminiTab,
    private val client: ios.silv.gemini.GeminiClient = commonDeps.geminiClient,
) : GeminiTabViewModelAction, ViewModelActionHandler<GeminiTabViewModelAction>() {

    override val handler: GeminiTabViewModelAction = this

    private val stack = StateFlowStack(geminiTab.baseUrl, minSize = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tabState = stack.stackFlow.transformLatest { stack ->
        val current = stack.last()

        emit(TabState.Loading(current))

        client.makeGeminiQuery(current).onSuccess {
            logcat { it.toString() }
            emit(TabState.Done(current, GeminiParser.parse(current, it).toList()))
        }.onFailure {
            logcat { it.stackTraceToString() }
            emit(TabState.Done(current, listOf(ContentNode.Error(it.message.orEmpty()))))
        }
    }
        .restartableStateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            initialValue = TabState.Loading(geminiTab.baseUrl)
        )

    override fun loadPage(link: String) {
        stack.push(link)
    }

    override fun goBack() {
        stack.pop()
    }

    override fun refresh() {
        tabState.restart()
    }
}