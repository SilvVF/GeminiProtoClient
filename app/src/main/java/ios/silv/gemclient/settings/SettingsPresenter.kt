package ios.silv.gemclient.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.collectAsRetainedState
import ios.silv.gemclient.GeminiHome
import ios.silv.gemclient.bar.BarPresenter
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow
import ios.silv.shared.datastore.Keys
import ios.silv.shared.settings.SettingsStore

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(SettingsPresenter::class)
@Inject
class SettingsPresenter(
    private val settingsStore: SettingsStore,
    private val navigator: ComposeNavigator,
) : Presenter {

    @Composable
    fun present(events: EventFlow<SettingsEvent>): SettingsState {

        val incognito by settingsStore.incognito.collectAsRetainedState()
        val theme by settingsStore.theme.collectAsRetainedState()
        val appTheme by settingsStore.appTheme.collectAsRetainedState()

        EventEffect(events) { event ->
            when (event) {
                is SettingsEvent.ChangeAppTheme -> settingsStore.edit {
                    it[Keys.appTheme] = event.theme.ordinal
                }

                is SettingsEvent.ChangeTheme -> settingsStore.edit {
                    it[Keys.darkMode] = event.theme.ordinal
                }

                SettingsEvent.ToggleIncognito -> settingsStore.edit {
                    it[Keys.incognito] = !incognito
                }

                SettingsEvent.NavigateHome -> navigator.topLevelDest.tryEmit(GeminiHome)
            }
        }

        return SettingsState(
            incognito = incognito,
            theme = theme,
            appTheme = appTheme
        )
    }
}
