package app.mnemo.data.db

import app.cash.sqldelight.db.SqlDriver

/** Creates the platform SQLite driver. [path] is a file path, or null for an in-memory database. */
expect class DriverFactory {
    fun create(path: String?): SqlDriver
}
