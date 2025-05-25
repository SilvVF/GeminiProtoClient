package ios.silv.gemclient

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.Scene
import androidx.navigation3.ui.SceneStrategy
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.gemclient.bar.BottombarScaffold
import ios.silv.gemclient.base.LocalNavBackStack
import ios.silv.gemclient.base.LocalNavigator
import ios.silv.gemclient.dependency.ActivityKey
import ios.silv.gemclient.dependency.metroViewModel
import ios.silv.gemclient.home.geminiHomeDestination
import ios.silv.gemclient.settings.geminiSettingsDestination
import ios.silv.gemclient.tab.geminiTabDestination
import ios.silv.gemclient.ui.theme.GemClientTheme
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiHome
import ios.silv.shared.NavKey
import ios.silv.shared.bar.BarPresenter
import ios.silv.shared.settings.AppTheme
import ios.silv.shared.settings.SettingsStore


@Suppress("UNCHECKED_CAST")
@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
    private val navigator: AppComposeNavigator,
    private val settingsStore: SettingsStore,
) : ComponentActivity() {

    @Composable
    private fun GeminiCompositionLocals(
        backstack: SnapshotStateList<NavKey>,
        content: @Composable () -> Unit
    ) {
        LaunchedEffect(backstack) {
            navigator.handleNavigationCommands(backstack)
        }

        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalNavBackStack provides backstack,
        ) {
            content()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            !settingsStore.initialized
        }

        enableEdgeToEdge()

        setContent {

            val backStack = rememberNavBackStack(GeminiHome)

            val theme by settingsStore.theme.collectAsStateWithLifecycle()
            val appTheme by settingsStore.appTheme.collectAsStateWithLifecycle()
            val owner = rememberViewModelStoreNavEntryDecorator()

            GeminiCompositionLocals(backStack as SnapshotStateList<NavKey>) {
                GemClientTheme(
                    theme = theme,
                    dynamicColor = appTheme == AppTheme.Dynamic,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NavDisplay(
                            backStack = backStack,
                            entryDecorators = listOf(
                                // Add the default decorators for managing scenes and saving state
                                rememberSceneSetupNavEntryDecorator(),
                                rememberSavedStateNavEntryDecorator(),
                                // Then add the view model store decorator
                                owner
                            ),
                            predictivePopTransitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(tween(700)),
                                    initialContentExit = fadeOut(tween(700))
                                )
                            },
                            sceneStrategy = BottomBarSinglePaneSceneStrategy(),
                            entryProvider = entryProvider {
                                geminiTabDestination()
                                geminiHomeDestination()
                                geminiSettingsDestination()
                            },
                        )
                    }
                }
            }
        }
    }
}

data class BottomBarSinglePaneScene<T : Any>(
    override val key: T,
    val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>
) : Scene<T> {
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable (() -> Unit) = {
        if (entry.metadata.containsKey(BOTTOM_BAR_KEY)) {

            val presenter = metroViewModel<BarPresenter>()
            val state by presenter.models.collectAsStateWithLifecycle()

            BottombarScaffold(state, presenter.events, Modifier.fillMaxSize()) {
                entry.content.invoke(entry.key)
            }
        } else {
            entry.content.invoke(entry.key)
        }
    }

    companion object {
        const val BOTTOM_BAR_KEY = "BOTTOM_BAR_KEY"
    }
}

/**
 * A [SceneStrategy] that always creates a 1-entry [Scene] simply displaying the last entry in the
 * list.
 */
class BottomBarSinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    @Composable
    override fun calculateScene(entries: List<NavEntry<T>>, onBack: (Int) -> Unit): Scene<T> =
        BottomBarSinglePaneScene(
            key = entries.last().key,
            entry = entries.last(),
            previousEntries = entries.dropLast(1)
        )
}




