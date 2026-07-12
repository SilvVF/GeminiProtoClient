package ios.silv.gembrowse

import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.RoomDatabase
import ios.silv.libgemini.gemini.GeminiClient
import kotlinx.coroutines.flow.Flow
import okio.Path

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform


/** Marks dependency injection system accessors, so direct access must be explicitly opted in. */
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

interface PreviewCache {
    val invalidated: Flow<Unit>
    suspend fun write(url: String, bitmap: ImageBitmap): Result<Path>
    suspend fun read(url: String): Path?
    suspend fun clean(urls: List<String>): Boolean
}

@OptIn(DependencyAccessor::class)
abstract class CommonDependencies {

    abstract val geminiClient: GeminiClient

    abstract val builder: RoomDatabase.Builder<AppDatabase>

    abstract val dataStore: DataStore<Preferences>

    abstract val previewCache: PreviewCache

    val settingsStore by lazy { Settings(dataStore) }

    val database by lazy { getRoomDatabase(builder) }
    val combinedDao by lazy { database.getCombinedDao() }
}
