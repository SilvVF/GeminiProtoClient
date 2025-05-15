package ios.silv.database

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import ios.silv.DatabaseMp

class NativeSqlDriverFactory : SqlDriverFactory {
    override fun create() =
        NativeSqliteDriver(
            schema = DatabaseMp.Schema,
            onConfiguration = { config: DatabaseConfiguration ->
                config.copy(
                    extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true)
                )
            },
            name = DATABASE_NAME,
        )
}