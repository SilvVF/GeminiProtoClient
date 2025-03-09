
package ios.silv.gemclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.rememberDependency
import ios.silv.gemclient.tab.geminiPageDestination
import ios.silv.gemclient.ui.theme.GemClientTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GemClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    val navController = rememberNavController()
                    val navigator = rememberDependency { navigator }

                    LaunchedEffect(navController) {
                        navigator.handleNavigationCommands(navController)
                    }

                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        NavHost(
                            navController,
                            modifier = Modifier.padding(innerPadding),
                            startDestination = GeminiTab("gemini://geminiprotocol.net/docs/faq.gmi"),
                        ) {
                            geminiPageDestination()
                        }
                    }
                }
            }
        }
    }
}



