package app.mnemo.platform

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

actual class RawSqlite actual constructor(
    path: String,
) : AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    actual fun query(
        sql: String,
        vararg args: Any?,
    ): List<Map<String, Any?>> {
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs ->
                val meta = rs.metaData
                val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                val out = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    val row = LinkedHashMap<String, Any?>()
                    cols.forEachIndexed { i, c ->
                        val idx = i + 1
                        row[c] =
                            when (meta.getColumnType(idx)) {
                                Types.INTEGER, Types.BIGINT, Types.SMALLINT, Types.TINYINT -> rs.getLong(idx).takeUnless { rs.wasNull() }
                                Types.FLOAT, Types.DOUBLE, Types.REAL, Types.NUMERIC, Types.DECIMAL ->
                                    rs
                                        .getDouble(
                                            idx,
                                        ).takeUnless { rs.wasNull() }
                                Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> rs.getBytes(idx)
                                else -> rs.getObject(idx)
                            }
                    }
                    out += row
                }
                return out
            }
        }
    }

    actual fun tableExists(name: String): Boolean =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", name).isNotEmpty()

    actual override fun close() = conn.close()
}

actual class SqliteWriter actual constructor(
    path: String,
) : AutoCloseable {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path")

    actual fun exec(
        sql: String,
        vararg args: Any?,
    ) {
        conn.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.execute()
        }
    }

    actual fun transaction(block: () -> Unit) {
        conn.autoCommit = false
        try {
            block()
            conn.commit()
        } catch (e: Throwable) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    actual override fun close() = conn.close()
}
