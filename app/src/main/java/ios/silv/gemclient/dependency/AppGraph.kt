package ios.silv.gemclient.dependency

import android.app.Activity
import app.cash.sqldelight.db.SqlDriver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import ios.silv.core_android.log.logcat
import ios.silv.database.Database
import ios.silv.database_android.DatabaseHandler
import ios.silv.database_android.DatabaseHandlerImpl
import ios.silv.database_android.DriverFactory
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.App
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemini.GeminiCache
import ios.silv.gemini.GeminiClient
import kotlin.reflect.KClass


@DependencyGraph(AppScope::class, isExtendable = true)
abstract class AppGraph {

    abstract val app: App

    @SingleIn(AppScope::class)
    @Provides
    val navigator: ComposeNavigator = ComposeNavigator()

    @SingleIn(AppScope::class)
    @Provides
    val provideDriver: SqlDriver by lazy { DriverFactory(this.app).createDriver() }

    @SingleIn(AppScope::class)
    @Provides
    fun provideDatabase(driver: SqlDriver): Database = Database(driver)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabaseHandler(database: Database, sqlDriver: SqlDriver): DatabaseHandler =
        DatabaseHandlerImpl(database, sqlDriver)

    @SingleIn(AppScope::class)
    @Provides
    fun provideTabsRepo(databaseHandler: DatabaseHandler): TabsRepo = TabsRepo(databaseHandler)

    @SingleIn(AppScope::class)
    @Provides
    fun provideGeminiCache(): GeminiCache = GeminiCache(app)

    @SingleIn(AppScope::class)
    @Provides
    fun provideGeminiClient(cache: GeminiCache): GeminiClient = GeminiClient(cache)

    /**
     * A multibinding map of activity classes to their providers accessible for
     * [MetroAppComponentFactory].
     */
    @Multibinds
    abstract val activityProviders: Map<KClass<out Activity>, Provider<Activity>>

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides app: App): AppGraph
    }
}


