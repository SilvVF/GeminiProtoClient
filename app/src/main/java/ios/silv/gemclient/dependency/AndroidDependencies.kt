package ios.silv.gemclient.dependency

import android.app.Application
import androidx.lifecycle.LifecycleOwner


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