package ios.silv.gembrowse

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.room3.RoomDatabase
import ios.silv.libgemini.gemini.AndroidGeminiClient
import ios.silv.libgemini.gemini.GeminiClient

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

fun buildCommonDeps(context: Context): CommonDependencies {
    return object : CommonDependencies() {
        override val geminiClient: GeminiClient = AndroidGeminiClient(context)
        override val builder = getDatabaseBuilder(context)
        override val dataStore: DataStore<Preferences> = createDataStore(
            producePath = { context.filesDir.resolve(it).absolutePath }
        )
        override val previewCache: PreviewCache = AndroidPreviewCache(context)
    }
}

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("gemini_browse.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}