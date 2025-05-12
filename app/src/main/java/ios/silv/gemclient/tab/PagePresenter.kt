package ios.silv.gemclient.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.produceRetainedState
import io.github.takahirom.rin.rememberRetained
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.tab.PageState.Content.UiNode
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemini.GeminiClient
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import ios.silv.sqldelight.Page
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(PagePresenter::class)
@Inject
class PagePresenter(
    private val client: GeminiClient,
    private val tabsRepo: TabsRepo
): Presenter {

    @Composable
    fun present(page: Page, events: EventFlow<PageEvent>): PageState {
        var input by rememberRetained { mutableStateOf("") }
        var response by rememberRetained {
            mutableStateOf<Result<Response>?>(null)
        }

        LaunchedEffect(page) {
            response = client.makeGeminiQuery(page.url)
        }

        EventEffect(events) { event ->
            when(event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> {

                }
                PageEvent.Submit -> {
                    tabsRepo.insertPage(page.tab_id, page.url + "?query=${input}")
                }
            }
        }

        val parsedNodes by produceRetainedState(emptyList<UiNode>()) {
            snapshotFlow { response }
                .map { it?.getOrNull() }
                .distinctUntilChanged()
                .mapLatest { res ->
                    value = if (res == null || res.status == GeminiCode.StatusInput) {
                        emptyList()
                    } else {
                        GeminiParser.parse(page.url, res)
                            .map(::UiNode)
                            .toList()
                    }
                }
                .collect()
        }

        return  when(val res = response) {
            null -> PageState.Loading
            else -> {
                res.fold(
                    onSuccess = {
                        if (it.status == GeminiCode.StatusInput) {
                            PageState.Input(input)
                        } else {
                            PageState.Content(parsedNodes)
                        }
                    },
                    onFailure = {
                        PageState.Error(it.message ?: "error")
                    }
                )
            }
        }
    }
}
