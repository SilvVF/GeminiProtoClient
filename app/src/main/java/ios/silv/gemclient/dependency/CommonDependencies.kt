package ios.silv.gemclient.dependency

import android.app.Application
import androidx.lifecycle.LifecycleOwner
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

    val geminiCache by lazy { ios.silv.gemini.GeminiCache(application) }

    val geminiClient by lazy { ios.silv.gemini.GeminiClient(geminiCache) }

    val navigator = ComposeNavigator()
}
