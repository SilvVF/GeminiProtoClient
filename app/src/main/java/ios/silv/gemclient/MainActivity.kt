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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.gemclient.bar.BarEvent
import ios.silv.gemclient.bar.BarPresenter
import ios.silv.gemclient.bar.BottombarScaffold
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.base.LocalNavController
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.ActivityKey
import ios.silv.gemclient.dependency.LocalMetroPresenterFactory
import ios.silv.gemclient.dependency.PresenterFactory
import ios.silv.gemclient.dependency.metroPresenter
import ios.silv.gemclient.home.geminiHomeDestination
import ios.silv.gemclient.tab.geminiTabDestination
import ios.silv.gemclient.ui.rememberEventFlow
import ios.silv.gemclient.ui.theme.GemClientTheme
import kotlinx.coroutines.launch


@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val viewModelFactory: ViewModelProvider.Factory,
    private val presenterFactory: PresenterFactory,
    private val navigator: ComposeNavigator,
) : ComponentActivity() {

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = viewModelFactory

    @Composable
    private fun GeminiCompositionLocals(
        navController: NavController,
        content: @Composable () -> Unit
    ) {
        val scope = rememberCoroutineScope()

        LifecycleStartEffect(navController) {
            val job = scope.launch {
                with(navigator) {
                    handleNavigationCommands(navController)
                }
            }
            onStopOrDispose {
                job.cancel()
            }
        }

        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalMetroPresenterFactory provides presenterFactory,
            LocalNavController provides navController,
        ) {
            content()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi")
        setContent {

            val navController = rememberNavController()

            GeminiCompositionLocals(navController) {
                GemClientTheme {
                    Surface {
                        val barPresenter = metroPresenter<BarPresenter>()

                        val events = rememberEventFlow<BarEvent>()
                        val barState = barPresenter.present(events)

                        BottombarScaffold(
                            state = barState,
                            events = events,
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = GeminiHome,
                            ) {
                                geminiHomeDestination()

                                geminiTabDestination()

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




