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



