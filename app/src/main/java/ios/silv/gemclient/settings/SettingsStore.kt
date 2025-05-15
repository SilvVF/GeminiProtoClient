package ios.silv.gemclient.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.zacsweers.metro.Inject
import ios.silv.core_android.log.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object Keys {
    val appTheme = intPreferencesKey("app_theme_pref")
    val darkMode = intPreferencesKey("dark_mode_pref")
    val incognito = booleanPreferencesKey("incognito")
    val recentlyViewed = stringSetPreferencesKey("recently_viewed_links")
    val bookmarked = stringSetPreferencesKey("bookmarked_links")
}

enum class AppTheme {
    Default,
    Dynamic
}

enum class Theme {
    Light,
    Dark,
    System
}

@Inject
class SettingsStore(
    context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = context.settingsStore

    @Volatile
    var initialized = false

    private val initialValues: Preferences by lazy {
        runBlocking { store.data.first() }
    }

    init {
        scope.launch {
            initialValues.let {
                initialized = true
                logcat { "initialized settings" }
            }
        }
    }

    val appTheme by preferenceStateFlow(Keys.appTheme, AppTheme.Default) {
        AppTheme.entries.getOrNull(it) ?: AppTheme.Default
    }
    val theme by preferenceStateFlow(Keys.darkMode, Theme.System) {
        Theme.entries.getOrNull(it) ?: Theme.System
    }
    val incognito by preferenceStateFlow(Keys.incognito, true)
    val recentlyViewed by preferenceStateFlow(Keys.recentlyViewed, emptySet())
    val bookmarked by preferenceStateFlow(Keys.bookmarked, emptySet())

    suspend fun edit(
        transform: suspend (MutablePreferences) -> Unit
    ) {
        try {
            store.edit(transform)
        } catch (e: Exception) {
            logcat { e.stackTraceToString() }
        }
    }

    private fun <KeyType> preferenceStateFlow(
        key: Preferences.Key<KeyType>,
        defaultValue: KeyType,
    ): Lazy<StateFlow<KeyType>> {
        return preferenceStateFlow(key, defaultValue) { it }
    }

    private fun <KeyType, StateType> preferenceStateFlow(
        key: Preferences.Key<KeyType>,
        defaultValue: StateType,
        transform: ((KeyType) -> StateType?),
    ): Lazy<StateFlow<StateType>> = lazy {
        val initialValue = initialValues[key]?.let(transform) ?: defaultValue
        val stateFlow = MutableStateFlow(initialValue)
        scope.launch {
            store.data
                .map { preferences -> preferences[key]?.let(transform) ?: defaultValue }
                .collect(stateFlow::emit)
        }
        stateFlow
    }
}