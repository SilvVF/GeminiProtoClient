package ios.silv.gembrowse

import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.IOSGeminiClient
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(DependencyAccessor::class, ExperimentalForeignApi::class)
fun MainViewController() = run {

    commonDeps = object : CommonDependencies() {
        override val geminiClient: GeminiClient = IOSGeminiClient()
        override val builder: RoomDatabase.Builder<AppDatabase> = getDatabaseBuilder()
        override val dataStore: DataStore<Preferences> = createDataStore(
            producePath = {
                val documentDirectory: NSURL? = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )
                requireNotNull(documentDirectory).path + "/$it"
            }
        )
    }

    ComposeUIViewController {
        App()
    }
}