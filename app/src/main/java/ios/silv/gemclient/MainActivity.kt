package ios.silv.gemclient

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.rememberDependency
import ios.silv.gemclient.tab.geminiPageDestination
import ios.silv.gemclient.ui.LaunchedOnStartedEffect
import ios.silv.gemclient.ui.components.BottomSearchBarWrapper
import ios.silv.gemclient.ui.theme.GemClientTheme
import ios.silv.home.geminiHomeDestination
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
                                BottomSearchBarWrapper(
                                    mainNavController,
                                    listOf(
                                        GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi"),
                                        GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi"),
                                        GeminiTab("gemini://gemini.circumlunar.space/docs/specification.gmi")
                                    )
                                ) {
                                    NavHost(
                                        navController = mainNavController,
                                        startDestination = GeminiHome,
                                        modifier = Modifier.weight(1f)
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




