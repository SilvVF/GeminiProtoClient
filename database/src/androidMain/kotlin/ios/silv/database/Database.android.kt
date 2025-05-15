package ios.silv.database

import java.util.concurrent.atomic.AtomicInteger

internal actual fun <T> threadLocalRef(): IThreadLocal<T> = object : IThreadLocal<T> {

    val threadLocal = ThreadLocal<T>()

    override fun get(): T? = threadLocal.get()

    override fun set(value: T) = threadLocal.set(value)
}

internal actual fun atomicInt(value: Int): IAtomicInt = object : IAtomicInt {

    val ai = AtomicInteger(value)

    override fun incrementAndGet(): Int = ai.incrementAndGet()

    override fun decrementAndGet(): Int = ai.decrementAndGet()
}

internal actual fun identityHashCode(instance: Any?): Int {
    return System.identityHashCode(instance)
}
