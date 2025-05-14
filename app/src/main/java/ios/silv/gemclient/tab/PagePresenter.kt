package ios.silv.gemclient.tab

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.RetainedObserver
import io.github.takahirom.rin.produceRetainedState
import io.github.takahirom.rin.rememberRetained
import ios.silv.core_android.log.logcat
import ios.silv.database_android.dao.TabsDao
import ios.silv.gemclient.base.PreviewCache
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.tab.PageState.Content.UiNode
import ios.silv.gemclient.types.StablePage
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import ios.silv.gemini.GeminiClient
import ios.silv.gemini.GeminiCode
import ios.silv.gemini.GeminiParser
import ios.silv.gemini.Response
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import java.io.File

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(PagePresenter::class)
@Inject
class PagePresenter(
    private val client: GeminiClient,
    private val previewCache: PreviewCache,
    private val tabsDao: TabsDao
) : Presenter {

    @Stable
    private data class RetainedResponse(private val result: Result<Response>?) : RetainedObserver {

        var value by mutableStateOf(result)

        override fun onRemembered() {
            logcat { "onRemembered" }
        }

        override fun onForgotten() {
            logcat { "onForgotten" }
            value?.getOrNull()?.close()
            value = null
        }
    }

    @Composable
    fun present(page: StablePage, events: EventFlow<PageEvent>): PageState {

        var input by rememberRetained { mutableStateOf("") }
        val response = rememberRetained { RetainedResponse(null) }
        var fetchId by remember { mutableIntStateOf(0) }

        LaunchedEffect(page, fetchId) {
            response.value?.getOrNull()?.close()
            response.value = null
            response.value = client.makeGeminiQuery(page.url)
        }

        EventEffect(events) { event ->
            when (event) {
                is PageEvent.OnInputChanged -> input = event.input
                PageEvent.Refresh -> fetchId++
                PageEvent.Submit -> {
                    tabsDao.insertPage(page.tabId, page.url + "?query=${input}")
                }

                is PageEvent.PreviewSaved -> {
                    logcat { "writing preview to cache ${page.tabId}" }
                    previewCache.writeToCache(
                        tabId = page.tabId,
                        bitmap = event.bitmap.asAndroidBitmap()
                    ).onSuccess {
                        tabsDao.updatePreviewImageUpdatedAt(page.tabId)
                    }
                }
            }
        }

        val parsedNodes by produceRetainedState(emptyList<UiNode>()) {
            snapshotFlow { response.value }
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

        return when (val res = response.value) {
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
