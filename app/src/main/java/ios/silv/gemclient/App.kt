package ios.silv.gemclient

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.work.WorkManager
import ios.silv.gemclient.log.AndroidLogcatLogger
import ios.silv.gemclient.log.LogPriority


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
