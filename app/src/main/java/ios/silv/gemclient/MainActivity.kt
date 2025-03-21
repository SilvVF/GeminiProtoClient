package ios.silv.gemclient

import android.annotation.SuppressLint
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.base.createViewModel
import ios.silv.gemclient.dependency.rememberDependency
import ios.silv.gemclient.tab.geminiPageDestination
import ios.silv.gemclient.ui.LaunchedOnStartedEffect
import ios.silv.gemclient.ui.theme.GemClientTheme
import ios.silv.gemclient.home.geminiHomeDestination
import kotlinx.serialization.Serializable

@Serializable
data object DialogTest

class MainActivity : ComponentActivity() {

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi")
        setContent {

            val navController = rememberNavController()
            val mainNavController = rememberNavController()

            val navigator = rememberDependency { navigator }

            GemClientTheme {
                Surface {
                    LaunchedOnStartedEffect(navController) {
                        navigator.handleTopLevel(navController)
                    }

                    LaunchedOnStartedEffect(mainNavController) {
                        navigator.handleNavigationCommands(mainNavController)
                    }

                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        NavHost(
                            navController,
                            startDestination = GeminiMain,
                        ) {
                            composable<GeminiMain> {

                                val backStackEntry by mainNavController.currentBackStackEntryAsState()
                                val bottomBarViewModel = createViewModel { GeminiMainTabViewModel() }

                                val bottomBarState by bottomBarViewModel.state.collectAsStateWithLifecycle()
                                
                                GeminiMainTab(
                                    dispatcher = bottomBarViewModel,
                                    state = bottomBarState,
                                    backStackEntry = backStackEntry
                                ) {
                                    NavHost(
                                        navController = mainNavController,
                                        startDestination = GeminiHome,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        geminiHomeDestination()

                                        geminiPageDestination()
                                    }
                                }
                            }

                            composable<GeminiSettings> {
                                Box(Modifier.fillMaxSize()) {
                                    Button(
                                        onClick = {
                                            navigator.topLevelDest.tryEmit(GeminiMain)
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




