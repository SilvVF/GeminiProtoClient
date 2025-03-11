package ios.silv.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.GeminiSettings
import ios.silv.gemclient.base.ActionDispatcher
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.base.rememberViewModel

fun NavGraphBuilder.geminiHomeDestination() {
    composable<GeminiHome> {

        val viewModel = rememberViewModel { GeminiHomeViewModel() }

        val state by viewModel.state.collectAsStateWithLifecycle()

        GeminiHomeContent(
            dispatcher = viewModel,
            state = state,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewGeminiHomeContent() {

    val dispatcher = remember {
        object : ActionDispatcher<GeminiHomeViewModelAction> {
            override fun dispatch(action: GeminiHomeViewModelAction.() -> Unit) {}
        }
    }

    GeminiHomeContent(dispatcher, HomeViewModelState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiHomeContent(
    dispatcher: ActionDispatcher<GeminiHomeViewModelAction>,
    state: HomeViewModelState,
) {
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = animateColorAsState(
            if (state.incognito) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.background
            },
            label = "incog-background-color"
        ).value,
        topBar = {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text("Gemini Browser") },
                actions = {
                    val navigator = LocalNavigator.current
                    Button(
                        onClick = {
                            navigator.topLevelDest.tryEmit(GeminiSettings)
                        }
                    ) {
                        Text("nav")
                    }
                    ToggleIncognitoButton(
                        dispatcher,
                        state.incognito
                    )
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        Column(
            Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {

        }
    }
}

@Composable
private fun ToggleIncognitoButton(
    dispatcher: ActionDispatcher<GeminiHomeViewModelAction>,
    incognito: Boolean,
) {
    FilledIconToggleButton(
        checked = incognito,
        onCheckedChange = {
            dispatcher.dispatch {
                toggleIncognito()
            }
        }
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
        )
    }
}