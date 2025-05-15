package ios.silv.gemclient.settings

import ios.silv.gemclient.ui.UiEvent
import ios.silv.shared.settings.AppTheme
import ios.silv.shared.settings.Theme

sealed interface SettingsEvent : UiEvent {
    data class ChangeTheme(val theme: Theme) : SettingsEvent
    data object ToggleIncognito : SettingsEvent
    data class ChangeAppTheme(val theme: AppTheme) : SettingsEvent
    data object NavigateHome: SettingsEvent
}
