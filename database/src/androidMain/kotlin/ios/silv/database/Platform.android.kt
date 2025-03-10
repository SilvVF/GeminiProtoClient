package ios.silv.database

import org.json.JSONArray

actual fun platform() = "Android"


internal actual class ThreadLocalRef<T> actual constructor() : IThreadLocal<T> {

    private val threadLocal = ThreadLocal<T>()

    override fun get(): T? = threadLocal.get()

    override fun set(value: T) = threadLocal.set(value)
}

internal actual fun identityHashCode(instance: Any?): Int {
    return System.identityHashCode(instance)
}

internal actual class AtomicIntImpl actual constructor(value: Int) : IAtomicInt {

    private val ai = AtomicIntImpl(value)

    override fun incrementAndGet(): Int = ai.incrementAndGet()

    override fun decrementAndGet(): Int = ai.decrementAndGet()
}

internal actual fun List<String>.toJsonArray(): String {
    return JSONArray(this).toString()
}

internal actual fun String.fromJsonArray(): List<String> {
    val jsonArray = JSONArray(this)
    return List(jsonArray.length()) { i -> jsonArray.getString(i) }
}