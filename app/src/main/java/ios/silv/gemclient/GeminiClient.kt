package ios.silv.gemclient

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.ui.util.lerp
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.zhuinden.simplestackextensions.servicesktx.get
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeString
import ios.silv.gemclient.GeminiStatus.Failure
import ios.silv.gemclient.GeminiStatus.Input
import ios.silv.gemclient.GeminiStatus.Redirect
import ios.silv.gemclient.GeminiStatus.Success
import ios.silv.gemclient.log.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.parcelize.Parcelize
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.Future
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.Uuid

@Parcelize
@JvmInline
value class GeminiQuery(
    val url: String
): Parcelable

private const val GEMINI_PORT = 1965
private const val GEMINI_URI_PREFIX = "gemini://"
private const val GEMINI_URI_MAX_SIZE = 1024
private const val GEMTEXT_LINK_PREFIX = "=>"
private const val CR_LF = "\r\n"
private const val SPACE = 0x20

private val GeminiDispatcher: CoroutineContext = Dispatchers.IO.limitedParallelism(2)

sealed class GeminiStatus(
    open val code: Int,
    open val meta: String
) {
    data class Input(override val code: Int, override val meta: String) : GeminiStatus(code, meta)
    data class Success(override val code: Int, override val meta: String) : GeminiStatus(code, meta)
    data class Redirect(override val code: Int, override val meta: String) :
        GeminiStatus(code, meta)

    sealed class Failure(override val code: Int, override val meta: String) :
        GeminiStatus(code, meta) {
        data class TempFailure(override val code: Int, override val meta: String) :
            Failure(code, meta)

        data class PermFailure(override val code: Int, override val meta: String) :
            Failure(code, meta)

        data class ClientCertReq(override val code: Int, override val meta: String) :
            Failure(code, meta)
    }
}

private fun transformStatus(response: String): GeminiStatus {
    // <STATUS><SPACE><META><CR><LF>
    val status = response.slice(0..1)

    val space = response.indexOf(' ')
    val meta = response.slice(space + 1..response.lastIndex)

    logcat("GeminiClient") { "received header $response $status $meta" }

    return when (val code = status.toInt()) {
        in 10..19 -> Input(code, meta)
        in 20..29 -> Success(code, meta)
        in 30..39 -> Redirect(code, meta)
        in 40..49 -> Failure.TempFailure(code, meta)
        in 50..59 -> Failure.PermFailure(code, meta)
        in 60..69 -> Failure.ClientCertReq(code, meta)
        else -> error("Invalid status code received")
    }
}

data class GeminiResponse(
    val query: GeminiQuery,
    val meta: String,
    val content: ByteArray
)


sealed class GeminiContent(val response: GeminiResponse) {
    data class Text(val res: GeminiResponse, val content: String, val lang: String, val parent: String = "") : GeminiContent(res)
}

data class TextParams(
    val charset: Charset,
    val lang: String,
)

fun getParams(meta: String): TextParams {

    var charset = Charset.forName("UTF-8")
    var lang = ""

    if (meta == "text/gemini") {
        return TextParams(
            charset,
            lang
        )
    }

    val paramList = meta.removePrefix("text/gemini;")
    val params = paramList.split(';')

    for (param in params) {
        val (name, value) = param.trim().split('=')
        when (name) {
            "lang" -> lang = value
            "charset" -> charset = Charset.forName(value)
            else -> Unit
        }
    }
    return TextParams(
        charset,
        lang
    )
}

fun parseResponse(response: GeminiResponse): GeminiContent {
    logcat("GeminiClient") { "parsing response meta: ${response.meta} content size: ${response.content.size}" }
    val meta = response.meta
    when {
        meta.startsWith("text/gemini") -> {
            val (charset, lang) = getParams(meta)
            return GeminiContent.Text(response, String(response.content, charset), lang, response.query.url)
        }

        else -> error("unimplemented")
    }
}

private suspend fun makeGeminiQuery(query: GeminiQuery, cache: GeminiCache): Result<GeminiContent> {

    return runCatching {
        if (query.url.encodeUtf8().size > GEMINI_URI_MAX_SIZE) {
            error("${query.url} was larger than max size $GEMINI_URI_MAX_SIZE")
        }

        val host = query.extractHostFromGeminiUrl()

        logcat("GeminiClient") { "Making request to ${host.getOrNull()} $GEMINI_PORT" }

        withContext(GeminiDispatcher) {

            val defer = mutableListOf<() -> Unit>()
            val cached = cache.getResponse(url = query.url)

            val readCh = if (cached != null) {
                ByteReadChannel(source = cached.inputStream().asSource().buffered())
            } else {
                val sock = aSocket(SelectorManager(GeminiDispatcher))
                    .tcp()
                    .connect(hostname = host.getOrThrow(), port = GEMINI_PORT)
                    .tls(Dispatchers.IO) {
                        random = SecureRandom()
                        trustManager = SslSettings.getTrustManager()
                    }

                val sendChannel = sock.openWriteChannel(autoFlush = true)
                val receiveChannel = sock.openReadChannel()

                sendChannel.writeString("${query.url}$CR_LF")

                defer.add { sock.close() }

                receiveChannel
            }

            when (val status = transformStatus(readCh.readUTF8Line(1029)!!)) {
                is Failure -> error(status.meta)
                is Input -> TODO()
                is Redirect -> TODO()
                is Success -> parseResponse(
                    GeminiResponse(
                        query = query,
                        meta = status.meta,
                        content = readCh.readRemaining().readByteArray()
                    )
                )
            }.also {
                readCh.cancel()
                defer.forEach { it() }
            }
        }
    }
}

private const val NOTIFICATION_ID = 1000
private const val GEMINI_LOADER_PREFIX = "GeminiLoader_"
private const val GEMINI_LOADER_NOTIFICATION_CHANNEL_ID = "SyncNotificationChannel"

/**
 * Foreground information for sync on lower API levels when sync workers are being
 * run with a foreground service
 */
private fun Context.geminiForegroundInfo() = ForegroundInfo(
    NOTIFICATION_ID,
    geminiNotification(),
)

/**
 * Notification displayed on lower API levels when sync workers are being
 * run with a foreground service
 */
private fun Context.geminiNotification(): Notification {
    return NotificationCompat.Builder(
        this,
        GEMINI_LOADER_NOTIFICATION_CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(getString(R.string.gemini))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
}


private const val CACHE_SIZE_BYTES = 100L * 1024 * 1024

class GeminiCache(private val context: Context) {
    private val cacheDir get() = File(context.cacheDir.path, "gemini")

    private val md5 get() = MessageDigest.getInstance("MD5")

    private var bytes = 0L
    private val modified = mutableMapOf<String, Long>()
    private val queue =
       PriorityQueue<String>(compareByDescending { path -> modified[path] ?: Long.MAX_VALUE })

    private val mutex = Mutex(locked = true)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            cacheDir.mkdirs()

            for (file in cacheDir.listFiles().orEmpty()) {
                if (file.isFile) {
                    bytes += file.totalSpace

                    modified[file.name] = file.lastModified()
                    queue.add(file.name)
                }
            }
            cleanupIfNeeded()
        }.invokeOnCompletion {
            mutex.unlock()
        }
    }

    private fun cleanupIfNeeded() {
        val seen = mutableSetOf<String>()
        while (bytes >= CACHE_SIZE_BYTES) {

            val path = queue.poll() ?: return
            if (!seen.add(path)) break

            val file = File(cacheDir, path)

            if (file.delete()) {
                bytes -= file.totalSpace
            } else {
                modified[path] = System.currentTimeMillis()
                queue.add(path)
            }
        }
    }

    private fun hashForKey(key: String): String {
        val hashBytes = md5.digest(key.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteResponse(url: String) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key)
            val space = file.totalSpace

            if (file.delete()) {
                queue.remove(key)
                modified.remove(key)

                bytes -= space
            }
        }
    }

    suspend fun getResponse(url: String): File? {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key)

            return file.takeIf { it.exists() }
        }
    }

    suspend fun cacheResponse(url: String, response: GeminiResponse) {
        mutex.withLock {
            val key = hashForKey(url)

            val file = File(cacheDir, key).apply {
                createNewFile()
            }

            file.outputStream().buffered().use { w ->
                w.write(response.meta.encodeToByteArray())
                w.write(response.content)
            }
        }
    }
}

private const val URL_KEY = "url"
private const val RESPONSE_KEY = "response"

class GeminiCacheWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val cache by lazy { App.globalServices.get<GeminiCache>() }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            appContext.geminiNotification()
        )
    }

    override suspend fun doWork(): Result {

        val url = inputData.getString(URL_KEY) ?: return Result.failure()
        val response = GeminiFetchWorker.content[inputData.getString(RESPONSE_KEY)!!]!!

        cache.cacheResponse(url, response)

        GeminiFetchWorker.content.remove(inputData.getString(RESPONSE_KEY)!!)!!

        return Result.success()
    }

    companion object {
        fun request(workInfo: WorkInfo) =
            OneTimeWorkRequestBuilder<GeminiCacheWorker>()
                .setInputData(workInfo.outputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build()
    }
}

class GeminiFetchWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val cache by lazy { App.globalServices.get<GeminiCache>() }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            NOTIFICATION_ID,
            appContext.geminiNotification()
        )
    }


    override suspend fun doWork(): Result {
        val query = inputData.getString(URL_KEY)

        if (query.isNullOrBlank()) {
            return Result.failure()
        }

        if (nodes[query] != null) {
            return Result.success(
                workDataOf(
                    URL_KEY to query,
                    Progress to 100L
                )
            )
        }

        return withContext(Dispatchers.IO) {
            setProgress(workDataOf(Progress to 0L))

            makeGeminiQuery(GeminiQuery(query), cache).onSuccess {
                setProgress(workDataOf(Progress to 50L))
            }
        }
            .fold(
                onFailure = { Result.failure() },
                onSuccess = {
                    val parsed = when (it) {
                        is GeminiContent.Text -> parseTextWithProgress(it) { parsed, total ->
                            val updated = (50 + lerp(0, 50, (parsed.toFloat() / total.toFloat())))
                            val prog = workDataOf(
                                Progress to updated.toLong()
                            )
                            logcat { "$parsed $total $updated" }
                            setProgressAsync(prog)
                        }
                    }

                    val key = UUID.randomUUID().toString()

                    mutex.withLock {
                        nodes[query] = parsed
                        content[key] = it.response
                    }

                    setProgress(workDataOf(Progress to 100L))

                    Result.success(
                        workDataOf(
                            URL_KEY to query,
                            RESPONSE_KEY to key
                        )
                    )
                }
            )
    }

    companion object {

        private val mutex = Mutex()

        val content = mutableMapOf<String, GeminiResponse>()
        private val nodes = mutableMapOf<String, List<ContentNode>>()

        private const val Progress = "progress"

        fun getProgress(workInfo: WorkInfo): Long {
            return workInfo.progress.getLong(Progress, 0L)
        }

        fun getNodes(workInfo: WorkInfo): List<ContentNode> {
            return nodes[workInfo.outputData.getString(URL_KEY)].orEmpty()
        }

        fun enqueue(workManager: WorkManager, query: GeminiQuery): Pair<String, Operation> {
            val name = GEMINI_LOADER_PREFIX + query.url
            return name to workManager.beginUniqueWork(
                    uniqueWorkName = name,
                    existingWorkPolicy = ExistingWorkPolicy.KEEP,
                    request(query)
                )
                .enqueue()
        }

        private fun request(query: GeminiQuery) = OneTimeWorkRequestBuilder<GeminiFetchWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(URL_KEY to query.url))
            .build()
    }
}


private fun GeminiQuery.extractHostFromGeminiUrl(): Result<String> = runCatching {
    if (!url.startsWith(prefix = GEMINI_URI_PREFIX)) {
        error("Invalid URL $url Does not start with $GEMINI_URI_PREFIX")
    }

    val end = url.indexOf('/', startIndex = GEMINI_URI_PREFIX.length)
        .takeIf { i -> i != -1 }
        ?: url.length

    url.slice(GEMINI_URI_PREFIX.length..<end)
}

