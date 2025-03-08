package ios.silv.gemclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.zhuinden.simplestack.AsyncStateChanger
import com.zhuinden.simplestack.BackHandlingModel
import com.zhuinden.simplestack.Backstack
import com.zhuinden.simplestack.Bundleable
import com.zhuinden.simplestack.History
import com.zhuinden.simplestack.ScopedServices
import com.zhuinden.simplestack.ServiceBinder
import com.zhuinden.simplestack.navigator.Navigator
import com.zhuinden.simplestackcomposeintegration.core.BackstackProvider
import com.zhuinden.simplestackcomposeintegration.core.ComposeStateChanger
import com.zhuinden.simplestackcomposeintegration.core.DefaultComposeKey
import com.zhuinden.simplestackcomposeintegration.core.util.historyAsState
import com.zhuinden.simplestackcomposeintegration.services.rememberService
import com.zhuinden.simplestackextensions.lifecyclektx.observeAheadOfTimeWillHandleBackChanged
import com.zhuinden.simplestackextensions.navigatorktx.androidContentFrame
import com.zhuinden.simplestackextensions.servicesktx.add
import com.zhuinden.simplestackextensions.servicesktx.lookup
import com.zhuinden.statebundle.StateBundle
import ios.silv.gemclient.ui.theme.GemClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.util.Stack

class MainActivity : ComponentActivity() {

    val composeStateChanger = ComposeStateChanger()
    lateinit var backstack: Backstack

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            backstack.goBack()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        backstack = Navigator.configure()
            .setBackHandlingModel(BackHandlingModel.AHEAD_OF_TIME)
            .setGlobalServices(App.globalServices)
            .setScopedServices(ServiceProvider())
            .setStateChanger(AsyncStateChanger(composeStateChanger))
            .install(
                this, androidContentFrame, History.of(
                    GeminiTab("gemini://geminiprotocol.net/docs/faq.gmi"),
                    GeminiTab("gemini://geminiprotocol.net/docs/faq.gmi")
                )
            )

        setContent {
            GemClientTheme {
                BackstackProvider(backstack) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                        var editMode by rememberSaveable { mutableStateOf(false) }

                        BoxWithConstraints(
                            Modifier.padding(innerPadding).fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (editMode) {

                                val history by backstack.historyAsState()

                                LazyVerticalGrid(
                                    GridCells.FixedSize(maxWidth / 2),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(history) { historyItem ->
                                        val key =
                                            historyItem as? DefaultComposeKey ?: return@items

                                        ElevatedCard(
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f / 2f)
                                        ) {
                                            key.RenderComposable()
                                        }
                                    }
                                }
                            } else {
                                composeStateChanger.RenderScreen()
                            }
                        }
                    }
                }
            }
        }

        backPressedCallback.isEnabled = backstack.willHandleAheadOfTimeBack()

        backstack.observeAheadOfTimeWillHandleBackChanged(this) {
            backPressedCallback.isEnabled = it
        }
    }
}

sealed interface TabState {
    data class Loading(val progress: Int) : TabState
    data class Done(val nodes: List<ContentNode>) : TabState
}


class GeminiTabLoader(
    val link: String,
    backstack: Backstack,
) : ScopedServices.Activated, Bundleable {

    private val workManager = backstack.lookup<WorkManager>()

    private val current = MutableStateFlow(link)

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val state = MutableStateFlow<TabState>(TabState.Loading(-1))

    override fun fromBundle(bundle: StateBundle?) {
        bundle?.getString("link")?.let { l ->
            current.value = l
        }
    }

    override fun toBundle(): StateBundle {
        return StateBundle().apply {
            putString("link", link)
        }
    }

    val tabState = state.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5_000),
        TabState.Loading(-1)
    )

    private fun refresh() {
        scope.launch {
            val (name, _) = GeminiFetchWorker.enqueue(
                workManager,
                GeminiQuery(current.value)
            )

            workManager.getWorkInfosForUniqueWorkFlow(name).collect { infos ->
                infos.map {
                    when (it.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.RUNNING -> state.emit(
                            TabState.Loading(
                                GeminiFetchWorker.getProgress(it).toInt()
                            )
                        )

                        WorkInfo.State.SUCCEEDED -> {
                            state.emit(
                                TabState.Done(GeminiFetchWorker.getNodes(it))
                            )
                            workManager.enqueue(
                                GeminiCacheWorker.request(it)
                            )
                            cancel()
                        }

                        WorkInfo.State.FAILED,
                        WorkInfo.State.BLOCKED,
                        WorkInfo.State.CANCELLED -> {
                            state.emit(TabState.Done(emptyList()))
                            cancel()
                        }
                    }
                }
            }
        }
    }

    fun goBack() {

    }

    override fun onServiceActive() {
        refresh()
    }

    override fun onServiceInactive() {
        scope.cancel()
    }
}

@Immutable
@Parcelize
data class GeminiTab(
    val link: String
) : Screen() {

    override fun bindServices(serviceBinder: ServiceBinder) {
        super.bindServices(serviceBinder)

        with(serviceBinder) {
            add(GeminiTabLoader(link, backstack))
        }
    }

    @Composable
    override fun ScreenComposable(modifier: Modifier) {
        val tabLoader = rememberService<GeminiTabLoader>()

        val tabState by tabLoader.tabState.collectAsStateWithLifecycle()
        when (val state = tabState) {
            is TabState.Loading -> {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(progress = {
                        (state.progress / 100f).coerceIn(0f..1f)
                    })
                }
            }

            is TabState.Done -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.nodes) { node ->
                        when (node) {
                            is ContentNode.Error -> {
                                Text(node.message)
                            }

                            is ContentNode.Line -> {
                                Text(node.raw)
                            }
                        }
                    }
                }
            }
        }
    }
}

