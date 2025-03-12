package ios.silv.gemclient.dependency

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import ios.silv.database.Database
import ios.silv.database_android.DatabaseHandler
import ios.silv.database_android.DatabaseHandlerImpl
import ios.silv.database_android.DriverFactory
import ios.silv.database_android.dao.TabsRepo
import ios.silv.gemclient.base.ComposeNavigator
import ios.silv.gemini.GeminiCache
import ios.silv.gemini.GeminiClient

/**
 * Global var for making the [CommonDependencies] accessible.
 */
@DependencyAccessor
public lateinit var commonDeps: CommonDependencies

@OptIn(DependencyAccessor::class)
public val LifecycleOwner.commonDepsLifecycle: CommonDependencies
    get() = commonDeps

/**
 * Access to various dependencies for common-app module.
 */
@OptIn(DependencyAccessor::class)
public abstract class CommonDependencies {

    abstract val application: Application

    private val geminiCache by lazy { GeminiCache(application) }

    val geminiClient by lazy { GeminiClient(geminiCache) }

    private val driver by lazy { DriverFactory(application).createDriver() }
    private val database by lazy { Database(driver) }

    private val databaseHandler by lazy<DatabaseHandler> { DatabaseHandlerImpl(database, driver) }

    val tabsRepo by lazy { TabsRepo(database, databaseHandler) }

    val navigator = ComposeNavigator()

}
