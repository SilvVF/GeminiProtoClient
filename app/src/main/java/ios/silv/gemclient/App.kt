package ios.silv.gemclient

import android.app.Application
import androidx.work.WorkManager
import com.zhuinden.simplestack.GlobalServices
import com.zhuinden.simplestackextensions.servicesktx.add
import ios.silv.gemclient.log.AndroidLogcatLogger
import ios.silv.gemclient.log.LogPriority

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        SslSettings.configure(this)

        val cache by lazy { GeminiCache(this) }
        val workManager by lazy {  WorkManager.getInstance(this) }

        globalServices = GlobalServices.builder()
            .add(cache)
            .add(workManager)
            .build()
    }

    companion object {
        lateinit var globalServices: GlobalServices
            private set
    }
}