package ios.silv.database_android

import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

const val DATABASE_NAME = "gemtabs.db"

internal interface IThreadLocal<T> {
    fun get(): T?
    fun set(value: T)
}

internal interface IAtomicInt {
    fun incrementAndGet(): Int
    fun decrementAndGet(): Int
}

internal fun <T> threadLocalRef(): IThreadLocal<T> = object : IThreadLocal<T> {

    val threadLocal = ThreadLocal<T>()

    override fun get(): T? = threadLocal.get()

    override fun set(value: T) = threadLocal.set(value)

}
internal fun atomicInt(value: Int): IAtomicInt = object : IAtomicInt {

    val ai = AtomicInteger(value)

    override fun incrementAndGet(): Int = ai.incrementAndGet()

    override fun decrementAndGet(): Int = ai.decrementAndGet()
}

internal  fun identityHashCode(instance: Any?): Int {
    return System.identityHashCode(instance)
}

internal fun List<String>.toJsonArray(): String {
    return JSONArray(this).toString()
}

internal fun String.fromJsonArray(): List<String> {
    val jsonArray = JSONArray(this)
    return List(jsonArray.length()) { i -> jsonArray.getString(i) }
}