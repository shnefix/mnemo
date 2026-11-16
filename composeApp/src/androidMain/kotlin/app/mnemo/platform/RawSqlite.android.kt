package app.mnemo.platform

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

actual class RawSqlite actual constructor(path: String) : AutoCloseable {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)

    actual fun query(
        sql: String,
        vararg args: Any?,
    ): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val row = LinkedHashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] =
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                            Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                            Cursor.FIELD_TYPE_NULL -> null
                            else -> c.getString(i)
                        }
                }
                out += row
            }
        }
        return out
    }

    actual fun tableExists(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", name).isNotEmpty()

    actual override fun close() = db.close()
}
