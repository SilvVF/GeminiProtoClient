package ios.silv.shared.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiHome
import ios.silv.shared.datastore.Keys
import ios.silv.shared.di.Presenter
import ios.silv.shared.di.PresenterKey
import ios.silv.shared.di.PresenterScope
import ios.silv.shared.toTopLevel
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow
import ios.silv.shared.ui.collectAsRetainedState

@ContributesIntoMap(PresenterScope::class)
@PresenterKey(SettingsPresenter::class)
@Inject
class SettingsPresenter(
    private val settingsStore: SettingsStore,
    private val navigator: AppComposeNavigator,
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

                SettingsEvent.NavigateHome -> navigator.navCmds.tryEmit(toTopLevel(GeminiHome))
            }
        }

        return SettingsState(
            incognito = incognito,
            theme = theme,
            appTheme = appTheme
        )
    }
}
