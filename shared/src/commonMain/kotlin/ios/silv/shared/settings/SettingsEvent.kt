package ios.silv.shared.settings

import ios.silv.shared.ui.UiEvent

sealed interface SettingsEvent : UiEvent {
    data class ChangeTheme(val theme: Theme) : SettingsEvent
    data object ToggleIncognito : SettingsEvent
    data class ChangeAppTheme(val theme: AppTheme) : SettingsEvent
    data object NavigateHome: SettingsEvent
}
