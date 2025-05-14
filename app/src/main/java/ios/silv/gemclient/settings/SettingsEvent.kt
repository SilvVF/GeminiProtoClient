package ios.silv.gemclient.settings

import ios.silv.gemclient.ui.UiEvent

sealed interface SettingsEvent : UiEvent {
    data class ChangeTheme(val theme: Theme) : SettingsEvent
    data object ToggleIncognito : SettingsEvent
    data class ChangeAppTheme(val theme: AppTheme) : SettingsEvent
    data object NavigateHome: SettingsEvent
}
