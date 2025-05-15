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


package ios.silv.database.adapters

import app.cash.sqldelight.ColumnAdapter


internal object FloatColumnAdapter : ColumnAdapter<Float, Double> {
    override fun decode(databaseValue: Double): Float = databaseValue.toFloat()

    override fun encode(value: Float): Double = value.toDouble()
}

internal object IntColumnAdapter : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()

    override fun encode(value: Int): Long = value.toLong()
}

internal object ShortColumnAdapter : ColumnAdapter<Short, Long> {
    override fun decode(databaseValue: Long): Short = databaseValue.toShort()

    override fun encode(value: Short): Long = value.toLong()
}