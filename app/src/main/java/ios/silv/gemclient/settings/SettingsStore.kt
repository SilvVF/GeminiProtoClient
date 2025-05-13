package ios.silv.gemclient.settings

import android.content.Context
import androidx.annotation.Keep
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.zacsweers.metro.Inject
import ios.silv.core_android.log.asLog
import ios.silv.core_android.log.logcat
import ios.silv.gemclient.App
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
    val darkMode = booleanPreferencesKey("dark_mode")
    val incognito = booleanPreferencesKey("incognito")
}

enum class AppTheme {
    Default,
    Dynamic
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

    val appTheme by preferenceStateFlow<Int, AppTheme>(Keys.appTheme, AppTheme.Default) {
        AppTheme.entries.getOrNull(it) ?: AppTheme.Default
    }
    val darkMode by preferenceStateFlow(Keys.darkMode, true)
    val incognito by preferenceStateFlow(Keys.incognito, true)

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