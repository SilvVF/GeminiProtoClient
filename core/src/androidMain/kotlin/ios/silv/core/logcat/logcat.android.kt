package ios.silv.core.logcat


import android.annotation.SuppressLint
import java.io.PrintWriter
import java.io.StringWriter
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import ios.silv.core.logcat.LogcatLogger.Companion.installedThrowable
import ios.silv.core.logcat.LogcatLogger.Companion.isInstalled
import ios.silv.core.logcat.LogcatLogger.Companion.logger
import kotlin.math.min

private const val MAX_LOG_LENGTH = 4000
private const val MAX_TAG_LENGTH = 23

/**
 * A [logcat] logger that delegates to [android.util.Log] for any log with a priority of
 * at least [minPriorityInt], and is otherwise a no-op.
 *
 * Handles special cases for [LogPriority.ASSERT] (which requires sending to Log.wtf) and
 * splitting logs to be at most 4000 characters per line (otherwise logcat just truncates).
 *
 * Call [installOnDebuggableApp] to make sure you never log in release builds.
 *
 * The implementation is based on Timber DebugTree.
 */
class AndroidLogcatLogger(minPriority: LogPriority = LogPriority.DEBUG) : LogcatLogger {

    private val minPriorityInt: Int = minPriority.priorityInt

    override fun isLoggable(priority: LogPriority): Boolean =
        priority.priorityInt >= minPriorityInt

    @SuppressLint("ObsoleteSdkInt")
    override fun log(
        priority: LogPriority,
        tag: String,
        message: String
    ) {
        // Tag length limit was removed in API 26.
        val trimmedTag = if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
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

    private fun logToLogcat(
        priority: Int,
        tag: String,
        part: String
    ) {
        if (priority == Log.ASSERT) {
            Log.wtf(tag, part)
        } else {
            Log.println(priority, tag, part)
        }
    }

    companion object {
        fun installOnDebuggableApp(application: Application, minPriority: LogPriority = LogPriority.DEBUG) {
            if (!LogcatLogger.isInstalled && application.isDebuggableApp) {
                LogcatLogger.install(AndroidLogcatLogger(minPriority))
            }
        }
    }
}

/**
 * Utility to turn a [Throwable] into a loggable string.
 *
 * The implementation is based on Timber.getStackTraceString(). It's different
 * from [android.util.Log.getStackTraceString] in the following ways:
 * - No silent swallowing of UnknownHostException.
 * - The buffer size is 256 bytes instead of the default 16 bytes.
 */
actual fun Throwable.asLog(): String {
    val stringWriter = StringWriter(256)
    val printWriter = PrintWriter(stringWriter, false)
    printStackTrace(printWriter)
    printWriter.flush()
    return stringWriter.toString()
}

@PublishedApi
internal actual fun Any.outerClassSimpleNameInternalOnlyDoNotUseKThxBye(): String {
    val javaClass = this::class.java
    val fullClassName = javaClass.name
    val outerClassName = fullClassName.substringBefore('$')
    val simplerOuterClassName = outerClassName.substringAfterLast('.')
    return if (simplerOuterClassName.isEmpty()) {
        fullClassName
    } else {
        simplerOuterClassName.removeSuffix("Kt")
    }
}

private val Application.isDebuggableApp: Boolean
    get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0


internal actual fun LogcatLogger.installLogger() {
    synchronized(LogcatLogger.Companion) {
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

internal actual fun uninstallLogger() {
    synchronized(LogcatLogger.Companion) {
        installedThrowable = null
        logger = NoLog
    }
}