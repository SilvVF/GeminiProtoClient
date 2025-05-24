package ios.silv.shared.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStoreAndroid(context: Context, filename: String): DataStore<Preferences> = createDataStore(
    producePath = { context.filesDir.resolve(filename).absolutePath }
)