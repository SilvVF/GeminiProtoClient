package ios.silv.gemclient.settings

import ios.silv.gemclient.ui.UiEvent

sealed interface SettingsEvent : UiEvent {
    data object ToggleDarkMode : SettingsEvent
    data object ToggleIncognito : SettingsEvent
    data class ChangeAppTheme(val theme: AppTheme) : SettingsEvent
}
