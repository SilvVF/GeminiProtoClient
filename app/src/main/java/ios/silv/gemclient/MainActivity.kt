@file:OptIn(ExperimentalMaterial3Api::class)

package ios.silv.gemclient

import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ios.silv.gemclient.log.logcat
import ios.silv.gemclient.ui.theme.GemClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

class MainActivity : ComponentActivity() {

    @OptIn(DependencyAccessor::class)
    val navigator = commonDeps.navigator

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GemClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->

                    val navController = rememberNavController()

                    LaunchedEffect(navController) {
                        navigator.handleTabNavigationCommands(navController)
                    }

                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        NavHost(
                            navController,
                            startDestination = GeminiTab(link = "gemini://geminiprotocol.net/docs/faq.gmi")
                        ) {
                            composable<GeminiTab> { backStackEntry ->

                                val route = backStackEntry.toRoute<GeminiTab>())

                                logcat { "$route ${route.link}" }
                                
                                route.TabHost(GeminiPage(route.link))
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface TabState {
    data object Loading : TabState
    data class Done(val nodes: List<ContentNode>) : TabState
}

class GeminiTabLoader @OptIn(DependencyAccessor::class) constructor(
    savedStateHandle: SavedStateHandle,
    private val client: GeminiClient = commonDeps.geminiClient,
): ViewModel() {

    val navArgs = savedStateHandle.toRoute<GeminiPage>()
    private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())


    private val state = MutableStateFlow<TabState>(TabState.Loading)

    val tabState = state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TabState.Loading
    )

    var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) {
            return
        }

        job = viewModelScope.launch {
            state.emit(TabState.Loading)
            client.makeGeminiQuery(GeminiQuery(navArgs.link)).onSuccess {
                logcat { it.toString() }
                state.emit(TabState.Done(GemTextParser.parse(it)))
            }.onFailure {
                logcat { it.stackTraceToString() }
                state.emit(TabState.Done(listOf(ContentNode.Error(it.message.orEmpty()))))
            }
        }.also {
            it.invokeOnCompletion { job = null }
        }
    }
}

@Composable
fun GeminiPageContent(tabLoader: GeminiTabLoader) {

    val tabState by tabLoader.tabState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(tabLoader.navArgs.link)
                },
                actions = {
                    Button(onClick = { tabLoader.refresh() }) {
                        Text("refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = tabState) {
            is TabState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is TabState.Done -> {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = paddingValues) {
                    items(state.nodes) { node ->
                        when (node) {
                            is ContentNode.Error -> {
                                Text(node.message)
                            }

                            is ContentNode.Line.Link -> {
                                TextButton(
                                    onClick = {
                                     //   navController.navigate(GeminiPage(node.url))
                                    }
                                ) {
                                    Text(node.name)
                                }
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

interface Screen

abstract class Tab {

    @Composable
    fun <T: Any> TabHost(start: T) {
        val navController = rememberNavController()

        LaunchedEffect(Unit) {
            @OptIn(DependencyAccessor::class)
            commonDeps.navigator.handleNavigationCommands(navController)
        }

        NavHost(
            navController,
            startDestination = start,
            builder = {
                graph()
            }
        )
    }

    abstract fun NavGraphBuilder.graph()
}

@Serializable
data class GeminiTab(
    val link: String
): Tab() {

    override fun NavGraphBuilder.graph() {
        composable<GeminiPage> { backStackEntry ->

            logcat { backStackEntry.savedStateHandle.toRoute<GeminiPage>().toString() }

            val viewModel = ios.silv.gemclient.stateViewModel {
                GeminiTabLoader(it)
            }

            GeminiPageContent(viewModel)
        }
    }
}

@Serializable
data class GeminiPage(val link: String = "")

