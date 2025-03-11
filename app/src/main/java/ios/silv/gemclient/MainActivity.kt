package ios.silv.gemclient

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.rememberDependency
import ios.silv.gemclient.tab.geminiPageDestination
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

                LaunchedEffect(navController) {
                    navigator.handleTopLevel(navController)
                }
                LaunchedEffect(mainNavController) {
                    navigator.handleNavigationCommands(mainNavController)
                }

                CompositionLocalProvider(LocalNavigator provides navigator) {
                    NavHost(
                        navController,
                        startDestination = GeminiMain,
                    ) {
                        composable<GeminiMain> {
                            Scaffold { paddingValues ->
                                Column(Modifier.padding(paddingValues)) {
                                    NavHost(
                                        mainNavController,
                                        GeminiHome
                                    ) {
                                        geminiHomeDestination()

                                        geminiPageDestination()
                                    }
                                }
                            }
                        }

                        composable<GeminiSettings> {
                            Box(Modifier.fillMaxSize()) {
                                Text("Settings", Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}




