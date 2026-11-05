@file:OptIn(ExperimentalForeignApi::class)

package ios.silv.libgemini.gemini

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.gai_strerror
import platform.posix.getaddrinfo
import platform.posix.getnameinfo
import platform.posix.memset
import platform.posix.sockaddr
import platform.posix.socklen_t


/** The IP family of a [ResolvedAddress]. */
enum class IpFamily {
    V4,
    V6,
}

/** One address a name resolved to: an IP literal, never a name. */
data class ResolvedAddress(
    val ip: String,
    val family: IpFamily,
)

/** What a [HostResolver] answered for a name. */
sealed interface Resolution {
    /** The addresses, in the order to try. Never empty: an empty answer is [NoAddress]. */
    data class Resolved(
        val candidates: List<ResolvedAddress>,
    ) : Resolution {
        init {
            require(candidates.isNotEmpty()) { "a resolved answer carries at least one address" }
        }
    }

    /** The resolver answered, and the name has no address. */
    data class NoAddress(
        val host: String,
    ) : Resolution

    /** The resolver could not answer. */
    data class Failed(
        val host: String,
        val cause: Throwable,
    ) : Resolution
}

/** Turns a name into every address it stands for. */
fun interface HostResolver {
    suspend fun resolve(host: String): Resolution

    companion object {
        /** This platform's own resolver: every record it returns, in [happyEyeballsOrder]. */
        fun platform(): HostResolver = PosixHostResolver
    }
}

/**
 * RFC 8305 §4. The resolver's order within a family is kept (the OS already applied RFC 6724), and
 * the families are interleaved starting with the family of the first address, so a family whose
 * every address is unreachable costs one attempt rather than all of them.
 */
fun happyEyeballsOrder(candidates: List<ResolvedAddress>): List<ResolvedAddress> {
    if (candidates.size < 2) return candidates
    val first = candidates.first().family
    val preferred = candidates.filter { it.family == first }
    val other = candidates.filter { it.family != first }
    val ordered = ArrayList<ResolvedAddress>(candidates.size)
    for (i in 0 until maxOf(preferred.size, other.size)) {
        if (i < preferred.size) ordered += preferred[i]
        if (i < other.size) ordered += other[i]
    }
    return ordered
}

/** A platform resolver's raw records as an answer: interleaved, or [Resolution.NoAddress] when there are none. */
internal fun resolvedInOrder(
    records: List<ResolvedAddress>,
    host: String,
): Resolution = if (records.isEmpty()) Resolution.NoAddress(host) else Resolution.Resolved(happyEyeballsOrder(records))

/**
 * The addresses a connect to [host] tries, in order; a name without one is [SocketUnknownHostException].
 *
 * Public because a QUIC connect resolves at its own entry rather than inside each platform's
 * connection builder: quiche is handed a literal endpoint and the name separately, so the name has to
 * become addresses one level above the backend.
 */
suspend fun NameResolution.candidatesFor(host: String): List<ResolvedAddress> {
    val resolver =
        when (this) {
            NameResolution.Platform -> HostResolver.platform()
            is NameResolution.Via -> resolver
        }
    return when (val answer = resolver.resolve(host)) {
        is Resolution.Resolved -> answer.candidates
        is Resolution.NoAddress -> throw SocketUnknownHostException(host, "the name has no address")
        is Resolution.Failed -> throw SocketUnknownHostException(host, cause = answer.cause)
    }
}
/** The POSIX resolver: `getaddrinfo`, every record, off the caller's dispatcher. A link-local scope is not carried. */
internal object PosixHostResolver : HostResolver {
    override suspend fun resolve(host: String): Resolution = withContext(Dispatchers.Default) { lookup(host) }

    private fun lookup(host: String): Resolution =
        memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_STREAM
            val result = allocPointerTo<addrinfo>()
            val ret = getaddrinfo(host, null, hints.ptr, result.ptr)
            if (ret != 0) {
                val reason = gai_strerror(ret)?.toKString() ?: "getaddrinfo returned $ret"
                return@memScoped Resolution.Failed(host, SocketUnknownHostException(host, reason))
            }
            val head = result.value ?: return@memScoped Resolution.NoAddress(host)
            try {
                val records = ArrayList<ResolvedAddress>()
                var node: CPointer<addrinfo>? = head
                while (node != null) {
                    val entry = node.pointed
                    entry.ai_addr?.let { collect(it, entry.ai_addrlen, records) }
                    node = entry.ai_next
                }
                resolvedInOrder(records, host)
            } finally {
                freeaddrinfo(head)
            }
        }

    /** `getnameinfo` with `NI_NUMERICHOST` renders the literal for either family, scope included. */
    private fun MemScope.collect(
        address: CPointer<sockaddr>,
        length: socklen_t,
        into: MutableList<ResolvedAddress>,
    ) {
        val family =
            when (address.pointed.sa_family.toInt()) {
                AF_INET -> IpFamily.V4
                AF_INET6 -> IpFamily.V6
                else -> return
            }
        val text = allocArray<ByteVar>(NI_MAXHOST)
        if (getnameinfo(address, length, text, NI_MAXHOST.convert(), null, 0.convert(), NI_NUMERICHOST) == 0) {
            into += ResolvedAddress(text.toKString(), family)
        }
    }
}