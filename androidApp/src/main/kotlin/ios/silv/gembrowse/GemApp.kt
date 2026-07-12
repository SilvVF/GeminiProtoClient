package ios.silv.gembrowse

import android.app.Application
import ios.silv.logcat.AndroidLogcatLogger

class GemApp: Application() {

    @OptIn(DependencyAccessor::class)
    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this@GemApp)
        commonDeps = buildCommonDeps(this@GemApp)
    }
}
