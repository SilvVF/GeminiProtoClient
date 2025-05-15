package ios.silv.core.logcat

import ios.silv.core.logcat.LogcatLogger.Companion.installedThrowable
import ios.silv.core.logcat.LogcatLogger.Companion.isInstalled
import ios.silv.core.logcat.LogcatLogger.Companion.logger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Utility to turn a [Throwable] into a loggable string.
 */
actual fun Throwable.asLog(): String {
    return this.stackTraceToString()
}

@PublishedApi
internal actual fun Any.outerClassSimpleNameInternalOnlyDoNotUseKThxBye(): String {
    val name = this::class.simpleName ?: "Unknown"
    val simplified = name.removeSuffix("Kt")
    return simplified
}

private val mutex = Mutex()

internal actual fun LogcatLogger.installLogger() = runBlocking {
    mutex.withLock {
        if (isInstalled) {
            logger.log(
                LogPriority.ERROR,
                "LogcatLogger",
                "Installing $logger even though a logger was previously installed here: " +
                        installedThrowable!!.asLog()
            )
        }
        installedThrowable = RuntimeException("Previous logger installed here")
        LogcatLogger.Companion.logger = logger
    }
}

internal actual fun uninstallLogger() = runBlocking {
    mutex.withLock {
        installedThrowable = null
        logger = NoLog
    }
}