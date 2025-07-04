/*
Copyright 2015 Javier Tomás

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Original Author most likely https://github.com/arkon from git blame
https://github.com/aniyomiorg/aniyomi/blame/4291ec4ad1828160e7880436a8bce449cf071243/data/src/main/java/tachiyomi/data/handlers/manga/MangaDatabaseHandler.kt#L4
 */

package ios.silv.database
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import ios.silv.DatabaseMp
import kotlinx.coroutines.flow.Flow

interface DatabaseHandler {
    suspend fun <T> await(inTransaction: Boolean = false, block: suspend DatabaseMp.() -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend DatabaseMp.() -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend DatabaseMp.() -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend DatabaseMp.() -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend DatabaseMp.() -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend DatabaseMp.() -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: DatabaseMp.() -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: DatabaseMp.() -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: DatabaseMp.() -> Query<T>): Flow<T?>
}