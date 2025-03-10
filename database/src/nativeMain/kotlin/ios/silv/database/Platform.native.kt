package ios.silv.database

internal actual class ThreadLocalRef<T> actual constructor() : IThreadLocal<T> {
    override fun get(): T = TODO("Not yet implemented")
    override fun set(value: T) = TODO("Not yet implemented")
}

internal actual fun identityHashCode(instance: Any?): Int = TODO("Not yet implemented")
internal actual class AtomicIntImpl actual constructor(value: Int) : IAtomicInt {
    override fun incrementAndGet(): Int {
        TODO("Not yet implemented")
    }
    override fun decrementAndGet(): Int {
        TODO("Not yet implemented")
    }
}

internal actual fun List<String>.toJsonArray(): String {
    TODO("Not yet implemented")
}

internal actual fun String.fromJsonArray(): List<String> {
    TODO("Not yet implemented")
}