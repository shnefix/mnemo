package app.mnemo.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.mnemo.db.MnemoDatabase

actual class DriverFactory(
    private val context: Context,
) {
    actual fun create(path: String?): SqlDriver =
        AndroidSqliteDriver(
            schema = MnemoDatabase.Schema,
            context = context,
            name = path?.substringAfterLast('/') ?: "mnemo-memory.db",
            callback =
                object : AndroidSqliteDriver.Callback(MnemoDatabase.Schema) {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )
}
