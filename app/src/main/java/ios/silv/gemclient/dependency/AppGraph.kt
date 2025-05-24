package ios.silv.gemclient.dependency

import android.app.Activity
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ios.silv.DatabaseMp
import ios.silv.database.AndroidSqlDriverFactory
import ios.silv.database.DatabaseHandler
import ios.silv.database.DatabaseHandlerImpl
import ios.silv.database.dao.TabsDao
import ios.silv.shared.PreviewCache
import ios.silv.libgemini.gemini.GeminiCache
import ios.silv.libgemini.gemini.GeminiClient
import ios.silv.libgemini.gemini.IGeminiCache
import ios.silv.shared.AppComposeNavigator
import ios.silv.shared.datastore.createDataStoreAndroid
import ios.silv.shared.datastore.dataStoreFileName
import ios.silv.shared.datastore.tofuDatastoreFileName
import ios.silv.shared.settings.SettingsStore
import ios.silv.sqldelight.Tab
import kotlin.reflect.KClass

@DependencyGraph(AppScope::class, isExtendable = true)
interface AppGraph {

    val context: Context

    val previewCache: PreviewCache

    val composeNavigator: AppComposeNavigator

    val settingsStore: SettingsStore

    val tabsDao: TabsDao

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseHandler(
        databaseMp: DatabaseMp,
        driver: SqlDriver
    ): DatabaseHandler = DatabaseHandlerImpl(
        db = databaseMp,
        driver = driver
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideDataStore(): DataStore<Preferences> = createDataStoreAndroid(context, dataStoreFileName)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTabsDao(databaseHandler: DatabaseHandler): TabsDao = TabsDao(databaseHandler)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDriver(): SqlDriver = AndroidSqlDriverFactory(context).create()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(driver: SqlDriver): DatabaseMp = DatabaseMp(driver)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGeminiCache(): IGeminiCache = GeminiCache(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGeminiClient(cache: IGeminiCache): GeminiClient = GeminiClient(
        cache,
        createDataStoreAndroid(context, tofuDatastoreFileName)
    )

    /**
     * A multibinding map of activity classes to their providers accessible for
     * [MetroAppComponentFactory].
     */
    @Multibinds
    val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides context: Context,
        ): AppGraph
    }
}


