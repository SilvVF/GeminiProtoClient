package ios.silv.gemclient.settings

import ios.silv.gemclient.ui.UiState
import ios.silv.shared.settings.AppTheme
import ios.silv.shared.settings.Theme

data class SettingsState(
    val incognito: Boolean,
    val appTheme: AppTheme,
    val theme: Theme
) : UiState