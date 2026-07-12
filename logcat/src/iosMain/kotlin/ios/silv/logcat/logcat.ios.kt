package ios.silv.logcat

import ios.silv.logcat.LogcatLogger.Companion.installedThrowable
import ios.silv.logcat.LogcatLogger.Companion.isInstalled
import ios.silv.logcat.LogcatLogger.Companion.logger
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


internal actual fun LogcatLogger.installLogger() {
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

internal actual fun uninstallLogger() {
    installedThrowable = null
    logger = NoLog
}