package ios.silv.gemclient

import android.app.Application
import ios.silv.gemclient.dependency.AndroidDependencies
import ios.silv.gemclient.dependency.CommonDependencies
import ios.silv.gemclient.dependency.DependencyAccessor
import ios.silv.gemclient.dependency.androidDeps
import ios.silv.gemclient.dependency.commonDeps
import ios.silv.core_android.log.AndroidLogcatLogger
import ios.silv.core_android.log.LogPriority


class App : Application() {

    @OptIn(DependencyAccessor::class)
    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        commonDeps = object : CommonDependencies() {

            override val application: Application = this@App
        }

        androidDeps = object : AndroidDependencies() {

            override val application: Application = this@App
        }
    }
}
