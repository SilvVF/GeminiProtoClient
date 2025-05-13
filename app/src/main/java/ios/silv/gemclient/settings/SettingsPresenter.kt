package ios.silv.gemclient.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import io.github.takahirom.rin.collectAsRetainedState
import ios.silv.gemclient.dependency.Presenter
import ios.silv.gemclient.dependency.PresenterKey
import ios.silv.gemclient.dependency.PresenterScope
import ios.silv.gemclient.ui.EventEffect
import ios.silv.gemclient.ui.EventFlow

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(SettingsPresenter::class)
@Inject
class SettingsPresenter(
    private val settingsStore: SettingsStore
) : Presenter {

    @Composable
    fun present(events: EventFlow<SettingsEvent>): SettingsState {

        val incognito by settingsStore.incognito.collectAsRetainedState()
        val darkMode by settingsStore.darkMode.collectAsRetainedState()
        val appTheme by settingsStore.appTheme.collectAsRetainedState()

        EventEffect(events) { event ->
            when (event) {
                is SettingsEvent.ChangeAppTheme -> settingsStore.edit {
                    it[Keys.appTheme] = event.theme.ordinal
                }

                SettingsEvent.ToggleDarkMode -> settingsStore.edit {
                    it[Keys.darkMode] = !darkMode
                }

                SettingsEvent.ToggleIncognito -> settingsStore.edit {
                    it[Keys.incognito] = !incognito
                }
            }
        }

        return SettingsState(
            incognito = incognito,
            darkMode = darkMode,
            appTheme = appTheme
        )
    }
}
