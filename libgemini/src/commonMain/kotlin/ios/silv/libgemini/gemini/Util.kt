package ios.silv.libgemini.gemini
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.preferences.core.Preferences
import ios.silv.logcat.asLog
import ios.silv.logcat.logcat
import kotlin.coroutines.cancellation.CancellationException

/**
 * Attempts [block], returning a successful [Result] if it succeeds, otherwise a [Result.Failure]
 * taking care not to break structured concurrency
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellationException: CancellationException) {
        throw cancellationException
    } catch (exception: Exception) {
        Result.failure(exception)
    }

fun <T> Result<T>.onFailureLog() = onFailure {
    logcat { it.asLog() }
}

// shared/src/commonMain/kotlin/createDataStore.kt
/**
 *   Gets the singleton DataStore instance, creating it if necessary.
 */
internal fun createDataStore(storage: Storage<Preferences>): DataStore<Preferences> =
    DataStoreFactory.create(storage = storage)

internal const val dataStoreFileName = "libgemini.preferences_pb"

