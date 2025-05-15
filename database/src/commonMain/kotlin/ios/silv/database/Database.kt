package ios.silv.database

import app.cash.sqldelight.ColumnAdapter

const val DATABASE_NAME = "gemtabs.db"

internal interface IThreadLocal<T> {
    fun get(): T?
    fun set(value: T)
}

internal interface IAtomicInt {
    fun incrementAndGet(): Int
    fun decrementAndGet(): Int
}

internal expect fun <T> threadLocalRef(): IThreadLocal<T>
internal expect fun atomicInt(value: Int): IAtomicInt

internal expect fun identityHashCode(instance: Any?): Int

private const val LIST_OF_STRINGS_SEPARATOR = ", "

internal object StringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            emptyList()
        } else {
            databaseValue.split(LIST_OF_STRINGS_SEPARATOR)
        }
    override fun encode(value: List<String>) = value.joinToString(
        separator = LIST_OF_STRINGS_SEPARATOR,
    )
}
