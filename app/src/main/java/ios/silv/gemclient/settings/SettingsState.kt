package ios.silv.gemclient.settings

import ios.silv.gemclient.ui.UiState

data class SettingsState(
    val incognito: Boolean,
    val appTheme: AppTheme,
    val darkMode: Boolean
) : UiState