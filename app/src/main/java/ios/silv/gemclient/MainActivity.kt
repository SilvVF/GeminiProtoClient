package ios.silv.gemclient

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.ActivityKey
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.tab.geminiPageDestination
import ios.silv.gemclient.ui.LaunchedOnStartedEffect
import ios.silv.gemclient.ui.theme.GemClientTheme
import ios.silv.gemclient.home.geminiHomeDestination
import kotlinx.coroutines.launch


@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val viewModelFactory: ViewModelProvider.Factory,
    private val navigator: ComposeNavigator,
) : ComponentActivity() {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi")
        setContent {

            val navController = rememberNavController()
            val scope = rememberCoroutineScope()

            LifecycleStartEffect(navController) {
                val job = scope.launch {
                    navigator.handleNavigationCommands(this, navController)
                }
                onStopOrDispose {
                    job.cancel()
                }
            }

            GemClientTheme {
                Surface {
                    CompositionLocalProvider(
                        LocalNavigator provides navigator
                    ) {

                        val backStackEntry by navController.currentBackStackEntryAsState()
                        val bottomBarViewModel = metroViewModel<BasePageViewModel>()

                        val bottomBarState by bottomBarViewModel.state.collectAsStateWithLifecycle()

                        GeminiBasePage(
                            state = bottomBarState,
                            backStackEntry = backStackEntry
                        ) {
                            NavHost(
                                navController,
                                startDestination = GeminiHome,
                            ) {
                                geminiHomeDestination()

                                geminiPageDestination()

                                composable<GeminiSettings> {
                                    Box(Modifier.fillMaxSize()) {
                                        Button(
                                            onClick = {
                                                navigator.topLevelDest.tryEmit(GeminiHome)
                                            },
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Text("settings")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}




