package ios.silv.logcat

import ios.silv.logcat.LogcatLogger.Companion.installedThrowable
import ios.silv.logcat.LogcatLogger.Companion.isInstalled
import ios.silv.logcat.LogcatLogger.Companion.logger
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create
import kotlin.math.min

/**
 * Utility to turn a [Throwable] into a loggable string.
 */
actual fun Throwable.asLog(): String {
    return this.stackTraceToString()
}
private const val MAX_LOG_LENGTH = 4000
private const val MAX_TAG_LENGTH = 23

class IosLogger(minPriority: LogPriority = LogPriority.DEBUG) : LogcatLogger {

    private val minPriorityInt: Int = minPriority.priorityInt

    override fun isLoggable(priority: LogPriority): Boolean =
        priority.priorityInt >= minPriorityInt
    override fun log(
        priority: LogPriority,
        tag: String,
        message: String
    ) {
        // Tag length limit was removed in API 26.
        val trimmedTag = if (tag.length <= MAX_TAG_LENGTH) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }

        if (message.length < MAX_LOG_LENGTH) {
            logToLogcat(priority.priorityInt, trimmedTag, message)
            return
        }

        // Split by line, then ensure each line can fit into Log's maximum length.
        var i = 0
        val length = message.length
        while (i < length) {
            var newline = message.indexOf('\n', i)
            newline = if (newline != -1) newline else length
            do {
                val end = min(newline, i + MAX_LOG_LENGTH)
                val part = message.substring(i, end)
                logToLogcat(priority.priorityInt, trimmedTag, part)
                i = end
            } while (i < newline)
            i++
        }
    }

    @OptIn(BetaInteropApi::class)
    private fun logToLogcat(
        priority: Int,
        tag: String,
        part: String
    ) {
        val line = "$priority $tag: $part"
        NSLog("%@", NSString.create(string = line))
    }

    companion object {
        fun installOnDebuggableApp(minPriority: LogPriority = LogPriority.DEBUG) {
            if (!isInstalled) {
                LogcatLogger.install(IosLogger(minPriority))
            }
        }
    }
}


@PublishedApi
internal actual fun Any.outerClassSimpleNameInternalOnlyDoNotUseKThxBye(): String {
    val name = this::class.simpleName ?: "Unknown"
    val simplified = name.removeSuffix("Kt")
    return simplified
}


internal actual fun LogcatLogger.installLogger() {
    if (isInstalled) {
        log(
            LogPriority.ERROR,
            "LogcatLogger",
            "Installing $logger even though a logger was previously installed here: " +
                    installedThrowable!!.asLog()
        )
    }
    LogcatLogger.Companion.installedThrowable = RuntimeException("Previous logger installed here")
    LogcatLogger.Companion.logger = this
}

internal actual fun uninstallLogger() {
    installedThrowable = null
    logger = NoLog
}