package ios.silv.shared.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.GeminiHome
import ios.silv.shared.MoleculeViewModel
import ios.silv.shared.datastore.Keys
import ios.silv.shared.di.ViewModelKey
import ios.silv.shared.di.ViewModelScope
import ios.silv.shared.toTopLevel
import ios.silv.shared.ui.EventEffect
import ios.silv.shared.ui.EventFlow

@ContributesIntoMap(ViewModelScope::class, binding = binding<ViewModel>())
@ViewModelKey(SettingsPresenter::class)
@Inject
class SettingsPresenter(
    private val settingsStore: SettingsStore,
    private val navigator: AppComposeNavigator,
) : MoleculeViewModel<SettingsEvent, SettingsState>() {

    @Composable
    override fun models(events: EventFlow<SettingsEvent>): SettingsState {

        val incognito by settingsStore.incognito.collectAsState()
        val theme by settingsStore.theme.collectAsState()
        val appTheme by settingsStore.appTheme.collectAsState()

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
