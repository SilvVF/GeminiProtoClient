package ios.silv.database

import app.cash.sqldelight.db.SqlDriver

fun interface SqlDriverFactory {
     fun create(): SqlDriver
}
