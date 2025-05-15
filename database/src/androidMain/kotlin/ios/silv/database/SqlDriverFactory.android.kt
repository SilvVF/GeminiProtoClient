package ios.silv.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ios.silv.DatabaseMp

class AndroidSqlDriverFactory(private val context: Context) : SqlDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = DatabaseMp.Schema,
            context = context,
            name = DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(DatabaseMp.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
}