package app.mnemo.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.mnemo.db.MnemoDatabase
import java.io.File
import java.util.Properties

actual class DriverFactory {
    actual fun create(path: String?): SqlDriver {
        val url = if (path == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
        path?.let { File(it).parentFile?.mkdirs() }
        val props = Properties().apply { put("foreign_keys", "true") }
        val driver = JdbcSqliteDriver(url, props)
        migrate(driver)
        return driver
    }

    private fun migrate(driver: SqlDriver) {
        val current =
            driver
                .executeQuery(
                    null,
                    "PRAGMA user_version;",
                    { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
                    0,
                ).value
        val target = MnemoDatabase.Schema.version
        if (current == 0L) {
            MnemoDatabase.Schema.create(driver)
        } else if (current < target) {
            MnemoDatabase.Schema.migrate(driver, current, target)
        }
        driver.execute(null, "PRAGMA user_version = $target;", 0)
    }
}
