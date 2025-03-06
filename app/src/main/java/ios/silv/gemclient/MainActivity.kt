package ios.silv.gemclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.zhuinden.simplestack.History
import com.zhuinden.simplestackcomposeintegration.core.ComposeNavigator
import ios.silv.gemclient.log.LogcatLogger.PrintLogger.asLog
import ios.silv.gemclient.log.logcat
import ios.silv.gemclient.ui.theme.GemClientTheme
import kotlinx.parcelize.Parcelize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GemClientTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ComposeNavigator(
                        modifier = Modifier.padding(innerPadding),
                        init = {
                            createBackstack(
                                initialKeys = History.of(MainScreen),
                                scopedServices = ServiceProvider(),
                                globalServices = App.globalServices
                            )
                        }
                    )
                }
            }
        }
    }
}

@Immutable
@Parcelize
private object MainScreen : Screen() {

    @Composable
    override fun ScreenComposable(modifier: Modifier) {

        val content by produceState(emptyList()) {
            val response = makeGeminiQuery(
                GeminiQuery("gemini://geminiprotocol.net/docs/faq.gmi")
            )
                .onFailure { logcat { it.asLog() } }


            value = response.fold(
                onFailure = { listOf(ContentNode.Error(it.message.orEmpty())) },
                onSuccess = { content ->
                    when(content) {
                        is GeminiContent.Text -> parseText(content)
                    }
                }
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(content) { node ->
                when(node) {
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

