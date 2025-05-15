package ios.silv.shared.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object Keys {
    val appTheme = intPreferencesKey("app_theme_pref")
    val darkMode = intPreferencesKey("dark_mode_pref")
    val incognito = booleanPreferencesKey("incognito")
    val recentlyViewed = stringSetPreferencesKey("recently_viewed_links")
    val bookmarked = stringSetPreferencesKey("bookmarked_links")
}
