package ios.silv.gemclient

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.LifecycleOwner

/** Marks dependency injection system accessors, so direct access must be explicitly opted in. */
@MustBeDocumented
@RequiresOptIn(
    message = "Direct access to the DI causes tight coupling. If possible, use constructor injection or parameters.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(value = AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
public annotation class DependencyAccessor

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

    val navigator = ComposeNavigator()
}

/** Global var for making the [AndroidDependencies] accessible. */
@DependencyAccessor
public lateinit var androidDeps: AndroidDependencies


@OptIn(DependencyAccessor::class)
public val LifecycleOwner.androidDepsLifecycle: AndroidDependencies get() = androidDeps

@OptIn(DependencyAccessor::class)
public fun initAndroidDepsIfNeeded(application: Application) {
    if (!::androidDeps.isInitialized) {
        androidDeps = object : AndroidDependencies() {
            override val application: Application = application
        }
    }
}

/** Access to various dependencies for android-utils module. */
public abstract class AndroidDependencies {

    /** The android [Application]. */
    public abstract val application: Application
}