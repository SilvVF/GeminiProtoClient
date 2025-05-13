package ios.silv.gemclient

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.imageDecoderEnabled
import coil3.request.allowHardware
import coil3.request.crossfade
import dev.zacsweers.metro.createGraphFactory
import ios.silv.core_android.log.AndroidLogcatLogger
import ios.silv.core_android.log.LogPriority
import ios.silv.gemclient.dependency.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class App : Application(), SingletonImageLoader.Factory {

    val appGraph by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createGraphFactory<AppGraph.Factory>()
            .create(this)
    }

    override fun onCreate() {
        super.onCreate()

        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .fetcherCoroutineContext (Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .allowHardware(true)
            .imageDecoderEnabled(true)
            .build()
    }
}
