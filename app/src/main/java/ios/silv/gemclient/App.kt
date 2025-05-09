package ios.silv.gemclient

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ios.silv.core_android.log.AndroidLogcatLogger
import ios.silv.core_android.log.LogPriority
import ios.silv.gemclient.dependency.AppGraph


class App : Application() {

    val appGraph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createGraphFactory<AppGraph.Factory>()
            .create(this)
    }

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
    }
}
