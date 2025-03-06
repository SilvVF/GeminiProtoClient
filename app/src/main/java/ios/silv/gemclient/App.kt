package ios.silv.gemclient

import android.app.Application
import com.zhuinden.simplestack.GlobalServices
import ios.silv.gemclient.log.AndroidLogcatLogger
import ios.silv.gemclient.log.LogPriority

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        SslSettings.configure(this)

        globalServices = GlobalServices.builder().build()
    }

    companion object {
        lateinit var globalServices: GlobalServices
            private set
    }
}