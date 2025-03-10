/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("FlowQuery")

package ios.silv.database

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

suspend fun <R> SqlDriver.awaitQuery(
    identifier: Int?,
    sql: String,
    mapper: suspend (SqlCursor) -> R,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)? = null,
): R = executeQuery<R>(identifier, sql, { QueryResult.AsyncValue { mapper(it) } }, parameters, binders).await()

suspend fun SqlDriver.await(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)? = null,
): Long = execute(identifier, sql, parameters, binders).await()

suspend fun SqlSchema<*>.awaitCreate(driver: SqlDriver) = create(driver).await()

suspend fun SqlSchema<*>.awaitMigrate(driver: SqlDriver, oldVersion: Long, newVersion: Long) = migrate(driver, oldVersion, newVersion).await()

suspend fun <T : Any> ExecutableQuery<T>.awaitAsList(): List<T> = execute { cursor ->
    val first = cursor.next()
    val result = mutableListOf<T>()

    // If the cursor isn't async, we want to preserve the blocking semantics and execute it synchronously
    when (first) {
        is QueryResult.AsyncValue -> {
            QueryResult.AsyncValue {
                if (first.await()) result.add(mapper(cursor)) else return@AsyncValue result
                while (cursor.next().await()) result.add(mapper(cursor))
                result
            }
        }

        is QueryResult.Value -> {
            if (first.value) result.add(mapper(cursor)) else return@execute QueryResult.Value(result)
            while (cursor.next().value) result.add(mapper(cursor))
            QueryResult.Value(result)
        }
    }
}.await()

suspend fun <T : Any> ExecutableQuery<T>.awaitAsOne(): T {
    return awaitAsOneOrNull()
        ?: throw NullPointerException("ResultSet returned null for $this")
}

suspend fun <T : Any> ExecutableQuery<T>.awaitAsOneOrNull(): T? = execute { cursor ->
    // If the cursor isn't async, we want to preserve the blocking semantics and execute it synchronously
    when (val next = cursor.next()) {
        is QueryResult.AsyncValue -> {
            QueryResult.AsyncValue {
                if (!next.await()) return@AsyncValue null
                val value = mapper(cursor)
                check(!cursor.next().await()) { "ResultSet returned more than 1 row for $this" }
                value
            }
        }

        is QueryResult.Value -> {
            if (!next.value) return@execute QueryResult.Value(null)
            val value = mapper(cursor)
            check(!cursor.next().value) { "ResultSet returned more than 1 row for $this" }
            QueryResult.Value(value)
        }
    }
}.await()

/** Turns this [Query] into a [Flow] which emits whenever the underlying result set changes. */
@JvmName("toFlow")
fun <T : Any> Query<T>.asFlow(): Flow<Query<T>> = flow {
    val channel = Channel<Unit>(CONFLATED)
    channel.trySend(Unit)

    val listener = Query.Listener {
        channel.trySend(Unit)
    }

    addListener(listener)
    try {
        for (item in channel) {
            emit(this@asFlow)
        }
    } finally {
        removeListener(listener)
    }
}

fun <T : Any> Flow<Query<T>>.mapToOne(
    context: CoroutineContext,
): Flow<T> = map {
    withContext(context) {
        it.awaitAsOne()
    }
}

fun <T : Any> Flow<Query<T>>.mapToOneOrDefault(
    defaultValue: T,
    context: CoroutineContext,
): Flow<T> = map {
    withContext(context) {
        it.awaitAsOneOrNull() ?: defaultValue
    }
}

fun <T : Any> Flow<Query<T>>.mapToOneOrNull(
    context: CoroutineContext,
): Flow<T?> = map {
    withContext(context) {
        it.awaitAsOneOrNull()
    }
}

fun <T : Any> Flow<Query<T>>.mapToOneNotNull(
    context: CoroutineContext,
): Flow<T> = mapNotNull {
    withContext(context) {
        it.awaitAsOneOrNull()
    }
}

fun <T : Any> Flow<Query<T>>.mapToList(
    context: CoroutineContext,
): Flow<List<T>> = map {
    withContext(context) {
        it.awaitAsList()
    }
}
