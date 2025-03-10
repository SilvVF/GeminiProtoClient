package ios.silv.database

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_NAME = "gemini.db"

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: SqlDriver): Database {
    return Database(driverFactory)
}