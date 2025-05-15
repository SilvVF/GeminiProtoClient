package ios.silv.shared.settings

import ios.silv.shared.ui.UiState

data class SettingsState(
    val incognito: Boolean,
    val appTheme: AppTheme,
    val theme: Theme
) : UiState