package ios.silv.gembrowse

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope

@ExperimentalMaterial3Api
@Composable
@Preview
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }

    val stack = rememberStateStack<Route>(listOf(HomeRoute), 1)

    val events = rememberEventFlow<SettingsEvent>()
    val state = settingsPresenter(events, stack)

    MaterialTheme(
        colorScheme = when(state.theme) {
            Theme.Light -> lightColorScheme()
            Theme.Dark -> darkColorScheme()
            Theme.System -> if(isSystemInDarkTheme()) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
        }
    ) {
        NavDisplay(
            backStack = stack.items,
            onBack = { stack.pop() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            sceneStrategies = listOf(
                SearchBarSceneStrategy(stack)
            ),
            entryProvider = entryProvider {
                entry<SettingsRoute> {
                    SettingsContent(state, events)
                }
                entry<HomeRoute>(
                    metadata = mapOf(SearchBarSceneStrategy.BOTTOM_BAR_KEY to true)
                ) {
                    val events = rememberEventFlow<HomeEvent>()
                    val state = homePresenter(stack, events)

                    HomeContent(events, stack, state)
                }
                entry<TabRoute>(
                    metadata = mapOf(SearchBarSceneStrategy.BOTTOM_BAR_KEY to true)
                ) { key ->
                    val events = rememberEventFlow<PageEvent>()
                    val pageState = pagePresenter(stack, events, key.id)
                    PageContent(pageState, events)
                }
            }
        )
    }
}

data class SearchBarScene<T : Any>(
    override val key: Any,
    val entry: NavEntry<T>,
    val stack: SnapshotStateStack<Route>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = {
        if (entry.metadata.containsKey(SearchBarSceneStrategy.BOTTOM_BAR_KEY)) {
            val events = rememberEventFlow<BarEvent>()
            val state = searchPresenter(stack, events)

            BottombarScaffold(state, events, Modifier.fillMaxSize()) {
                entry.Content()
            }
        } else {
            entry.Content()
        }
    }
}

/**
 * A [SceneStrategy] that always creates a 1-entry [Scene] simply displaying the last entry in the
 * list.
 */
public class SearchBarSceneStrategy<T : Any>(
    val stack: SnapshotStateStack<Route>,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? =
        SearchBarScene(
            key = entries.last().contentKey,
            entry = entries.last(),
            previousEntries = entries.dropLast(1),
            stack = stack
        )

    companion object {
        const val BOTTOM_BAR_KEY = "BOTTOM_BAR_KEY"
    }
}

