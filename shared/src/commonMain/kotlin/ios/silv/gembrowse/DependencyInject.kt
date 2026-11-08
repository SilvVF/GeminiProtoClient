package ios.silv.gembrowse

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.RoomDatabase
import ios.silv.libgemini.gemini.GeminiClient

/** Marks dependency injection system accessors, so direct access must be explicitly opted in. */
@Suppress("ExperimentalAnnotationRetention")
@MustBeDocumented
@RequiresOptIn(
    message = "Direct access to the DI causes tight coupling. If possible, use constructor injection or parameters.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(value = AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
public annotation class DependencyAccessor

@DependencyAccessor
public lateinit var commonDeps: CommonDependencies

@OptIn(DependencyAccessor::class)
abstract class CommonDependencies {

    abstract val geminiClient: GeminiClient

    abstract val builder: RoomDatabase.Builder<AppDatabase>

    abstract val dataStore: DataStore<Preferences>

    abstract val previewCache: PreviewCache

    val settingsStore by lazy { Settings(dataStore) }

    val database by lazy { getRoomDatabase(builder) }

    val tabDao by lazy { database.getTabDao() }
    val pageDao by lazy { database.getPageDao() }
    val bookmarkDao by lazy { database.getBookmarkDao() }
    val historyDao by lazy { database.getHistoryDao() }
}