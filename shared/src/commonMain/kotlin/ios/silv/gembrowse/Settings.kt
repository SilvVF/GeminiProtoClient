package ios.silv.gembrowse

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import kotlin.concurrent.Volatile

@Stable
enum class AppTheme {
    Default,
    Dynamic
}

@Stable
enum class Theme {
    Light,
    Dark,
    System
}

/*
    val appTheme by preferenceStateFlow(Keys.appTheme, AppTheme.Default) {
        AppTheme.entries.getOrNull(it) ?: AppTheme.Default
    }
    val theme by preferenceStateFlow(Keys.darkMode, Theme.System) {
        Theme.entries.getOrNull(it) ?: Theme.System
    }
    val incognito by preferenceStateFlow(Keys.incognito, true)
    val recentlyViewed by preferenceStateFlow(Keys.recentlyViewed, emptySet())
    val bookmarked by preferenceStateFlow(Keys.bookmarked, emptySet())
 */


class Settings(
    dataStore: DataStore<Preferences>
): SettingsStore(dataStore) {
    val appTheme by preferenceStateFlow(Keys.appTheme, AppTheme.Default) {
        AppTheme.entries.getOrNull(it) ?: AppTheme.Default
    }
    val theme by preferenceStateFlow(Keys.darkMode, Theme.System) {
        Theme.entries.getOrNull(it) ?: Theme.System
    }
    val incognito by preferenceStateFlow(Keys.incognito, true)
    val recentlyViewed by preferenceStateFlow(Keys.recentlyViewed, emptySet())
    val bookmarked by preferenceStateFlow(Keys.bookmarked, emptySet())


    companion object {
        object Keys {
            val appTheme = intPreferencesKey("app_theme_pref")
            val darkMode = intPreferencesKey("dark_mode_pref")
            val incognito = booleanPreferencesKey("incognito")
            val recentlyViewed = stringSetPreferencesKey("recently_viewed_links")
            val bookmarked = stringSetPreferencesKey("bookmarked_links")
        }
    }
}

private const val dataStoreFileName = "settings.preferences_pb"

fun createDataStore(producePath: (filename: String) -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath(dataStoreFileName).toPath() }
    )

abstract class SettingsStore(
    dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = dataStore

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

    suspend fun edit(
        transform: suspend (MutablePreferences) -> Unit
    ) {
        try {
            store.edit(transform)
        } catch (e: Exception) {
            logcat { e.asLog() }
        }
    }

    protected fun <KeyType> preferenceStateFlow(
        key: Preferences.Key<KeyType>,
        defaultValue: KeyType,
    ): Lazy<StateFlow<KeyType>> {
        return preferenceStateFlow(key, defaultValue) { it }
    }

    protected fun <KeyType, StateType> preferenceStateFlow(
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