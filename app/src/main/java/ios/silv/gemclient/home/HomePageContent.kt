package ios.silv.gemclient.home

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.GeminiSettings
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.metroViewModel

fun NavGraphBuilder.geminiHomeDestination() {
    composable<GeminiHome> {

        val viewModel = metroViewModel<HomeViewModel>()

        val state by viewModel.state.collectAsStateWithLifecycle()

        GeminiHomeContent(
            state = state,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewGeminiHomeContent() {

    GeminiHomeContent(HomeViewModelState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiHomeContent(
    state: HomeViewModelState,
) {
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
                        { state.events(HomeViewModelEvent.ToggleIncognito) },
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
                .fillMaxSize()
        ) {
            Text("HOME")
        }
    }
}

@Composable
private fun ToggleIncognitoButton(
    toggleIncognito: () -> Unit,
    incognito: Boolean,
) {
    FilledIconToggleButton(
        checked = incognito,
        onCheckedChange = {
            toggleIncognito()
        }
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
        )
    }
}