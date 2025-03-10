package ios.silv.database

expect fun platform(): String

internal interface IThreadLocal<T> {
    fun get(): T?
    fun set(value: T)
}

internal interface IAtomicInt {
    fun incrementAndGet(): Int
    fun decrementAndGet(): Int
}

internal expect class ThreadLocalRef<T>(): IThreadLocal<T>
internal expect class AtomicIntImpl(value: Int): IAtomicInt

internal expect fun identityHashCode(instance: Any?): Int

internal expect fun List<String>.toJsonArray(): String
internal expect fun String.fromJsonArray(): List<String>