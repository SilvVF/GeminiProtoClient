package ios.silv.gemclient.dependency

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import ios.silv.database.DatabaseHandler
import ios.silv.database.DatabaseHandlerImpl
import ios.silv.database.DriverFactory
import ios.silv.database.createDatabase
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

    val geminiCache by lazy { GeminiCache(application) }

    val geminiClient by lazy { GeminiClient(geminiCache) }

    private val driver by lazy { DriverFactory(application).createDriver() }
    private val database by lazy { createDatabase(driver) }

    val databaseHandler by lazy<DatabaseHandler> { DatabaseHandlerImpl(database, driver) }

    val navigator = ComposeNavigator()

}
