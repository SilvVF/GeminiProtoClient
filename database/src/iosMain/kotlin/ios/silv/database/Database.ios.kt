package ios.silv.database

import co.touchlab.stately.concurrency.ThreadLocalRef
import kotlin.concurrent.AtomicInt
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.identityHashCode

internal actual fun <T> threadLocalRef(): IThreadLocal<T> = object : IThreadLocal<T> {

    val th = ThreadLocalRef<T>()

    override fun get(): T? {
        return th.get()
    }

    override fun set(value: T) {
        th.set(value)
    }
}

internal actual fun atomicInt(value: Int): IAtomicInt = object : IAtomicInt {

    val ai = AtomicInt(value)

    override fun incrementAndGet(): Int {
        return ai.incrementAndGet()
    }

    override fun decrementAndGet(): Int {
        return ai.decrementAndGet()
    }
}

@OptIn(ExperimentalNativeApi::class)
internal actual fun identityHashCode(instance: Any?): Int {
    return instance.identityHashCode()
}
