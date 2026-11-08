package ios.silv.gembrowse

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.RoomDatabase
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.IOSGeminiClient
import ios.silv.logcat.IosLogger
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

@OptIn(DependencyAccessor::class, ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
fun MainViewController() = run {
    IosLogger.installOnDebuggableApp()

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
        override val previewCache: PreviewCache = CommonPreviewCache(
            okio.FileSystem.SYSTEM,
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )!!.path!!.toPath()
        )
    }

    ComposeUIViewController {
        App()
    }
}