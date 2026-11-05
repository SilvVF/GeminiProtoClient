package ios.silv.libgemini.gemini

import com.ditchoom.buffer.BufferFactory
import com.ditchoom.buffer.Default
import com.ditchoom.buffer.MutableDataBuffer
import com.ditchoom.buffer.NSDataBuffer
import com.ditchoom.buffer.ReadBuffer
import com.ditchoom.buffer.flow.ReadResult
import com.ditchoom.buffer.freeIfNeeded
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import platform.Foundation.NSData
import platform.Foundation.NSMakeRange
import platform.Foundation.subdataWithRange

/**
 * Converts the remaining bytes of a ReadBuffer (`[position(), limit())`) to NSData for
 * network writes.
 *
 * The zero-copy paths for `MutableDataBuffer` / `NSDataBuffer` must honour position and
 * limit — returning the raw backing NSData was a bug that leaked unrelated prefix/suffix
 * bytes onto the wire whenever the caller had positioned the buffer inside a larger
 * allocation (the websocket codec's reserved-prefix pattern is the motivating case).
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, UnsafeNumber::class)
internal fun ReadBuffer.toNSData(): NSData =
    when (this) {
        is MutableDataBuffer -> {
            val pos = position()
            val rem = remaining()
            if (pos == 0 && rem == data.length.toInt()) {
                data
            } else {
                data.subdataWithRange(NSMakeRange(pos.convert(), rem.convert()))
            }
        }
        is NSDataBuffer -> {
            val pos = position()
            val rem = remaining()
            if (pos == 0 && rem == data.length.toInt()) {
                data
            } else {
                data.subdataWithRange(NSMakeRange(pos.convert(), rem.convert()))
            }
        }
        else -> {
            // Fallback for exotic ReadBuffer types (e.g. a sliced/composite ReadBuffer).
            // Stage into an NSMutableData-backed scratch buffer — one copy into native
            // memory — then hand the backing NSMutableData out directly (NSMutableData
            // IS-an NSData). No Kotlin-heap ByteArray intermediary. Source position is
            // preserved; NWSocketWrapper.write advances it after the write completes.
            val remaining = remaining()
            if (remaining == 0) {
                NSData()
            } else {
                val savedPosition = position()
                val scratch = BufferFactory.Default.allocate(remaining)
                scratch.write(this)
                position(savedPosition)
                scratch.resetForRead()
                scratch.toNSData()
            }
        }
    }


/**
 * Translates a platform read that returns a [ReadBuffer] (and signals end/reset by throwing
 * [SocketClosedException]) into the [ReadResult] trichotomy.
 *
 * A `ClientSocket` **is** a [com.ditchoom.buffer.flow.ByteStream] (no adapter), so each platform
 * socket's `read(deadline): ReadResult` wraps its throwing read body with this helper:
 *
 * ```kotlin
 * override suspend fun read(deadline: Duration): ReadResult = translateRead { readRaw(deadline) }
 * ```
 *
 * - a non-empty buffer  → [ReadResult.Data]
 * - an empty buffer / clean EOF / [SocketClosedException] → [ReadResult.End]
 * - [SocketClosedException.ConnectionReset] → [ReadResult.Reset]
 */
internal suspend inline fun translateRead(read: () -> ReadBuffer): ReadResult =
    try {
        val buffer = read()
        if (buffer.remaining() <= 0) {
            buffer.freeIfNeeded()
            ReadResult.End
        } else {
            ReadResult.Data(buffer)
        }
    } catch (_: SocketClosedException.ConnectionReset) {
        ReadResult.Reset
    } catch (_: SocketClosedException) {
        ReadResult.End
    }